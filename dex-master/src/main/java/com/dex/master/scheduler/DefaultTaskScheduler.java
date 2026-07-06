package com.dex.master.scheduler;

import com.dex.common.model.entity.TaskDefinitionEntity;
import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.model.task.PluginConfig;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.TaskDefinitionRepository;
import com.dex.common.repository.TaskInstanceRepository;
import com.dex.common.util.JsonUtil;
import com.dex.master.dispatch.TaskDispatcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 任务调度器
 * <p>
 * 职责：
 * - 提交任务（保存到数据库，根据模式执行或调度）
 * - BATCH 模式：支持手动执行、定时执行、分片并行
 * - STREAMING 模式：常驻执行
 * - 从数据库恢复定时任务（Master 启动/主备切换时）
 * - 确保 taskId 永远不会丢失
 */
@Slf4j
@Component
public class DefaultTaskScheduler {

    @Autowired
    private TaskDefinitionRepository taskDefRepo;

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Autowired
    @Lazy
    private TaskDispatcher taskDispatcher;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private ThreadPoolTaskScheduler scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    private Counter taskSubmittedCounter;
    private Counter taskCompletedCounter;
    private Counter taskFailedCounter;

    @PostConstruct
    public void init() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(20);
        scheduler.setThreadNamePrefix("master-scheduler-");
        scheduler.initialize();
        log.info("Master 调度器初始化完成");

        if (meterRegistry != null) {
            taskSubmittedCounter = Counter.builder("dex.task.submitted")
                    .description("Total tasks submitted")
                    .register(meterRegistry);
            taskCompletedCounter = Counter.builder("dex.task.completed")
                    .description("Total tasks completed successfully")
                    .register(meterRegistry);
            taskFailedCounter = Counter.builder("dex.task.failed")
                    .description("Total tasks failed")
                    .register(meterRegistry);
        }
    }

    // ============================================================
    //  核心：确保 taskId 永远有值（补全并持久化）
    // ============================================================

    /**
     * 从数据库加载任务配置，并强制补全 taskId（持久化）
     */
    private TaskConfig loadConfigWithTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            log.error("loadConfigWithTaskId: taskId 为空");
            return null;
        }
        TaskDefinitionEntity def = taskDefRepo.findById(taskId).orElse(null);
        if (def == null) {
            log.warn("任务定义不存在: {}", taskId);
            return null;
        }
        TaskConfig config = JsonUtil.fromJson(def.getConfigJson(), TaskConfig.class);
        if (config == null) {
            log.error("解析任务配置失败: {}", taskId);
            return null;
        }

        // 补全 taskId（如果 config 中没有）
        if (config.getTaskId() == null || config.getTaskId().isEmpty()) {
            config.setTaskId(def.getTaskId());
            // 持久化补全，避免重复补全
            def.setConfigJson(JsonUtil.toJson(config));
            taskDefRepo.save(def);
            log.info("补全并持久化 taskId: {}", taskId);
        }

        // 补全 cronExpression（如果丢失）
        if (config.getCronExpression() == null || config.getCronExpression().isEmpty()) {
            config.setCronExpression(def.getCronExpression());
        }

        return config;
    }

    /**
     * 确保 taskId 有值（内存级补全，不持久化）
     */
    private void ensureTaskId(TaskConfig config) {
        if (config.getTaskId() == null || config.getTaskId().isEmpty()) {
            config.setTaskId("task-" + UUID.randomUUID().toString().replace("-", ""));
            log.info("生成新 taskId: {}", config.getTaskId());
        }
    }

    /**
     * 检查任务是否启用
     */
    private boolean isTaskEnabled(String taskId) {
        return taskDefRepo.findById(taskId)
                .map(def -> "ENABLED".equals(def.getStatus()))
                .orElse(false);
    }

    // ============================================================
    //  提交任务（用户手动提交 / 定时触发 / 启动）
    // ============================================================

    @Transactional
    public void submitTask(TaskConfig config) {
        ensureTaskId(config);

        TaskDefinitionEntity def = new TaskDefinitionEntity();
        def.setTaskId(config.getTaskId());
        def.setTaskName(config.getTaskName());
        def.setMode(config.getMode());
        // 保存完整配置（包含 taskId）
        def.setConfigJson(JsonUtil.toJson(config));
        def.setCronExpression(config.getCronExpression());
        def.setStatus("ENABLED");
        def.setCreateTime(new Date());
        def.setUpdateTime(new Date());
        taskDefRepo.save(def);

        if (taskSubmittedCounter != null) {
            taskSubmittedCounter.increment();
        }

        log.info("任务已保存: taskId={}, mode={}, cron={}",
                config.getTaskId(), config.getMode(), config.getCronExpression());

        // 根据模式处理
        if ("STREAMING".equals(config.getMode())) {
            executeStreaming(config);
        } else if ("BATCH".equals(config.getMode())) {
            if (config.getCronExpression() != null && !config.getCronExpression().isEmpty()) {
                scheduleBatchCron(config);
            } else {
                executeBatch(config);
            }
        }
    }

    // ============================================================
    //  BATCH 执行
    // ============================================================

    public void executeBatch(TaskConfig task) {
        // 防御：如果 taskId 丢失，尝试从数据库补全
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            log.error("executeBatch: taskId 为空，无法执行");
            return;
        }

        String instanceId = UUID.randomUUID().toString().replace("-", "");
        log.info("执行 BATCH 任务: taskId={}, instanceId={}, parallelism={}",
                task.getTaskId(), instanceId, task.getParallelism());

        TaskInstanceEntity instance = new TaskInstanceEntity();
        instance.setInstanceId(instanceId);
        instance.setTaskId(task.getTaskId());
        instance.setState("RUNNING");
        instance.setStartTime(new Date());
        instance.setCreateTime(new Date());
        taskInstanceRepo.save(instance);

        if (task.getParallelism() != null && task.getParallelism() > 1) {
            executeWithSharding(task, instanceId);
        } else {
            taskDispatcher.dispatchTask(task, instanceId, null);
        }
    }

    // ============================================================
    //  BATCH 分片并行执行
    // ============================================================

    private void executeWithSharding(TaskConfig task, String parentInstanceId) {
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            log.error("executeWithSharding: taskId 为空，无法分片");
            return;
        }

        log.info("开始分片执行: taskId={}, parallelism={}", task.getTaskId(), task.getParallelism());

        long[] idRange = getIdRange(task);
        long minId = idRange[0];
        long maxId = idRange[1];
        if (minId == 0 && maxId == 0) {
            log.warn("源表为空或无法获取 ID 范围，跳过分片");
            return;
        }
        log.info("ID 范围: {} ~ {}", minId, maxId);

        int parallelism = task.getParallelism();
        long totalRecords = maxId - minId + 1;
        long shardSize = (totalRecords + parallelism - 1) / parallelism;

        for (int i = 0; i < parallelism; i++) {
            long startId = minId + i * shardSize;
            long endId = Math.min(startId + shardSize - 1, maxId);
            if (startId > maxId) break;

            TaskConfig subTask = deepCopy(task);
            // 子任务 taskId = 父taskId + "-N"
            String subTaskId = task.getTaskId() + "-" + (i + 1);
            subTask.setTaskId(subTaskId);

            Map<String, Object> params = subTask.getSource().getParams();
            params.put("shardStartId", startId);
            params.put("shardEndId", endId);
            params.put("shardIndex", i + 1);
            params.put("totalShards", parallelism);

            String subInstanceId = UUID.randomUUID().toString().replace("-", "");
            TaskInstanceEntity subInstance = new TaskInstanceEntity();
            subInstance.setInstanceId(subInstanceId);
            subInstance.setTaskId(subTaskId);
            subInstance.setParentInstanceId(parentInstanceId);
            subInstance.setSubTaskIndex(i + 1);
            subInstance.setState("RUNNING");
            subInstance.setStartTime(new Date());
            subInstance.setCreateTime(new Date());
            taskInstanceRepo.save(subInstance);

            log.info("分发子任务 {}/{}: subTaskId={}, startId={}, endId={}",
                    i + 1, parallelism, subTaskId, startId, endId);

            taskDispatcher.dispatchTask(subTask, subInstanceId);
        }
    }

    /**
     * 获取源表 ID 范围（用于分片）
     */
    private long[] getIdRange(TaskConfig task) {
        PluginConfig source = task.getSource();
        Map<String, Object> params = source.getParams();
        String url = (String) params.get("url");
        String user = (String) params.get("user");
        String password = (String) params.get("password");
        String table = (String) params.get("table");
        String incrementColumn = (String) params.get("incrementColumn");
        if (incrementColumn == null) {
            incrementColumn = "id";
        }

        long minId = 0, maxId = 0;
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            String sql = String.format("SELECT MIN(%s), MAX(%s) FROM %s",
                    incrementColumn, incrementColumn, table);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    minId = rs.getLong(1);
                    maxId = rs.getLong(2);
                }
            }
        } catch (SQLException e) {
            log.error("获取 ID 范围失败: {}", e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
        return new long[]{minId, maxId};
    }

    /**
     * 深拷贝 TaskConfig
     */
    private TaskConfig deepCopy(TaskConfig original) {
        String json = JsonUtil.toJson(original);
        return JsonUtil.fromJson(json, TaskConfig.class);
    }

    // ============================================================
    //  STREAMING 执行
    // ============================================================

    private void executeStreaming(TaskConfig task) {
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            log.error("executeStreaming: taskId 为空，无法执行");
            return;
        }

        String instanceId = UUID.randomUUID().toString().replace("-", "");
        log.info("启动 STREAMING 任务: taskId={}, instanceId={}", task.getTaskId(), instanceId);

        TaskInstanceEntity instance = new TaskInstanceEntity();
        instance.setInstanceId(instanceId);
        instance.setTaskId(task.getTaskId());
        instance.setState("RUNNING");
        instance.setStartTime(new Date());
        instance.setCreateTime(new Date());
        taskInstanceRepo.save(instance);

        taskDispatcher.dispatchTask(task, instanceId, null);
    }

    // ============================================================
    //  BATCH 定时调度
    // ============================================================

    /**
     * 注册 BATCH 定时任务（Cron 触发）
     */
    private void scheduleBatchCron(TaskConfig task) {
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            log.error("scheduleBatchCron: taskId 为空，无法注册定时任务");
            return;
        }

        String cron = task.getCronExpression();
        if (cron == null || cron.isEmpty()) {
            log.error("定时任务缺少 cron 表达式: {}", task.getTaskId());
            return;
        }

        // 取消旧的调度（如果有）
        cancelScheduledTask(task.getTaskId());

        log.info("注册 BATCH 定时任务: taskId={}, cron={}", task.getTaskId(), cron);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            String taskId = task.getTaskId();
            log.info("定时任务触发: {}", taskId);

            // 检查任务是否启用
            if (!isTaskEnabled(taskId)) {
                log.info("任务已禁用，跳过执行: {}", taskId);
                return;
            }

            // 从数据库加载最新配置（确保 taskId 完整）
            TaskConfig config = loadConfigWithTaskId(taskId);
            if (config != null) {
                executeBatch(config);
            } else {
                log.error("加载任务配置失败: {}", taskId);
            }
        }, new CronTrigger(cron));

        scheduledFutures.put(task.getTaskId(), future);
        log.info("定时任务注册成功: taskId={}, cron={}", task.getTaskId(), cron);
    }

    // ============================================================
    //  任务恢复（Master 启动 / 主备切换）
    // ============================================================

    /**
     * 恢复所有已启用的定时任务
     */
    public void rescheduleAllEnabled() {
        List<TaskDefinitionEntity> defs = taskDefRepo.findByStatus("ENABLED");
        log.info("开始恢复定时任务，共 {} 个已启用任务", defs.size());

        int count = 0;
        for (TaskDefinitionEntity def : defs) {
            // 使用 loadConfigWithTaskId 确保 taskId 完整
            TaskConfig config = loadConfigWithTaskId(def.getTaskId());
            if (config == null) {
                log.warn("跳过任务配置解析失败: {}", def.getTaskId());
                continue;
            }

            // 如果是 BATCH 定时任务，重新调度
            if ("BATCH".equals(config.getMode())
                    && config.getCronExpression() != null
                    && !config.getCronExpression().isEmpty()) {
                scheduleBatchCron(config);
                count++;
            }
        }

        log.info("恢复定时任务完成，共恢复 {} 个", count);
    }

    // ============================================================
    //  任务管理
    // ============================================================

    /**
     * 取消定时任务
     */
    public void cancelScheduledTask(String taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("取消定时任务: {}", taskId);
        }
    }

    /**
     * 取消所有定时调度
     */
    public void shutdownSchedulers() {
        scheduledFutures.values().forEach(f -> f.cancel(false));
        scheduledFutures.clear();
        log.info("所有定时任务已取消");
    }

    /**
     * 重新调度任务（更新后调用）
     */
    public void rescheduleTask(TaskConfig config) {
        if (config.getTaskId() == null || config.getTaskId().isEmpty()) {
            log.error("rescheduleTask: taskId 为空");
            return;
        }
        // 1. 取消旧的调度
        cancelScheduledTask(config.getTaskId());
        // 2. 如果是 BATCH 定时任务，重新调度
        if ("BATCH".equals(config.getMode())
                && config.getCronExpression() != null
                && !config.getCronExpression().isEmpty()) {
            scheduleBatchCron(config);
            log.info("重新调度任务: {}", config.getTaskId());
        }
    }

    // ============================================================
    //  指标
    // ============================================================

    public void incrementCompleted() {
        if (taskCompletedCounter != null) taskCompletedCounter.increment();
    }

    public void incrementFailed() {
        if (taskFailedCounter != null) taskFailedCounter.increment();
    }
}