package com.dex.master.controller;

import com.dex.common.model.entity.TaskDefinitionEntity;
import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.TaskDefinitionRepository;
import com.dex.common.repository.TaskInstanceRepository;
import com.dex.common.util.JsonUtil;
import com.dex.master.dispatch.TaskDispatcher;
import com.dex.master.scheduler.DefaultTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/master/task")
public class TaskSubmitController {

    @Autowired
    private DefaultTaskScheduler taskScheduler;

    @Autowired
    private TaskDefinitionRepository taskDefRepo;

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Autowired
    private TaskDispatcher taskDispatcher;  // 注入任务分发器

    @PostMapping("/submit")
    public String submitTask(@RequestBody TaskConfig task) {
        log.info("收到任务提交请求: taskId={}, mode={}", task.getTaskId(), task.getMode());
        taskScheduler.submitTask(task);
        return "任务已提交: " + task.getTaskId();
    }

    /**
     * 手动停止任务实例（不修改任务定义状态）
     */
    @PostMapping("/stop")
    public String stopTask(@RequestParam String taskId) {
        List<TaskInstanceEntity> instances = taskInstanceRepo.findByTaskId(taskId);
        for (TaskInstanceEntity inst : instances) {
            if ("RUNNING".equals(inst.getState())) {
                // 更新数据库状态
                inst.setState("STOPPED");
                inst.setEndTime(new Date());
                taskInstanceRepo.save(inst);
                // 通知 Worker 停止
                taskDispatcher.stopTask(taskId, inst.getInstanceId());
                log.info("已通知 Worker 停止任务实例: {}", inst.getInstanceId());
            }
        }
        return "任务停止请求已发送";
    }

    /**
     * 删除任务定义（同时停止所有运行中的实例）
     */
    @DeleteMapping("/{taskId}")
    public String deleteTask(@PathVariable String taskId) {
        // 1. 停止所有运行中的实例
        List<TaskInstanceEntity> instances = taskInstanceRepo.findByTaskId(taskId);
        for (TaskInstanceEntity inst : instances) {
            if ("RUNNING".equals(inst.getState())) {
                inst.setState("STOPPED");
                inst.setEndTime(new Date());
                taskInstanceRepo.save(inst);
                taskDispatcher.stopTask(taskId, inst.getInstanceId());
            }
        }
        // 2. 取消定时调度（如果有）
        taskScheduler.cancelScheduledTask(taskId);
        // 3. 删除任务定义
        taskDefRepo.deleteById(taskId);
        log.info("任务已删除: {}", taskId);
        return "任务已删除";
    }

    /**
     * 更新任务定义（先停用旧任务，再更新配置，再重新启用）
     */
    @PutMapping("/update")
    public String updateTask(@RequestBody TaskConfig config) {
        // 1. 检查任务是否存在
        TaskDefinitionEntity existing = taskDefRepo.findById(config.getTaskId()).orElse(null);
        if (existing == null) {
            return "任务不存在";
        }
        // 2. 停止正在运行的实例
        List<TaskInstanceEntity> instances = taskInstanceRepo.findByTaskId(config.getTaskId());
        for (TaskInstanceEntity inst : instances) {
            if ("RUNNING".equals(inst.getState())) {
                inst.setState("STOPPED");
                inst.setEndTime(new Date());
                taskInstanceRepo.save(inst);
                taskDispatcher.stopTask(config.getTaskId(), inst.getInstanceId());
            }
        }
        // 3. 取消定时调度（如果有）
        taskScheduler.cancelScheduledTask(config.getTaskId());
        // 4. 删除旧定义
        taskDefRepo.deleteById(config.getTaskId());
        // 5. 重新提交任务（会自动保存新定义并调度）
        taskScheduler.submitTask(config);
        log.info("任务已更新: {}", config.getTaskId());
        return "任务更新成功";
    }

    /**
     * 启用任务（将状态改为 ENABLED，如果是定时任务则恢复调度）
     */
    @PostMapping("/enable")
    public String enableTask(@RequestParam String taskId) {
        TaskDefinitionEntity def = taskDefRepo.findById(taskId).orElse(null);
        if (def == null) {
            return "任务不存在";
        }
        if ("ENABLED".equals(def.getStatus())) {
            return "任务已是启用状态";
        }
        def.setStatus("ENABLED");
        taskDefRepo.save(def);
        // 如果是定时任务，重新调度
        if ("SCHEDULED".equals(def.getMode())) {
            TaskConfig config = JsonUtil.fromJson(def.getConfigJson(), TaskConfig.class);
            if (config != null) {
                taskScheduler.rescheduleTask(config);
                log.info("已重新调度定时任务: {}", taskId);
            }
        }
        log.info("任务已启用: {}", taskId);
        return "任务已启用";
    }

    /**
     * 禁用任务（停止运行中的实例、取消定时调度、状态置为 DISABLED）
     */
    @PostMapping("/disable")
    public String disableTask(@RequestParam String taskId) {
        TaskDefinitionEntity def = taskDefRepo.findById(taskId).orElse(null);
        if (def == null) {
            return "任务不存在";
        }
        if ("DISABLED".equals(def.getStatus())) {
            return "任务已是禁用状态";
        }

        // 1. 停止所有运行中的实例（并通知 Worker）
        List<TaskInstanceEntity> instances = taskInstanceRepo.findByTaskId(taskId);
        for (TaskInstanceEntity inst : instances) {
            if ("RUNNING".equals(inst.getState())) {
                inst.setState("STOPPED");
                inst.setEndTime(new Date());
                taskInstanceRepo.save(inst);
                // 通知 Worker 停止执行
                taskDispatcher.stopTask(taskId, inst.getInstanceId());
                log.info("已通知 Worker 停止任务实例: {}", inst.getInstanceId());
            }
        }

        // 2. 取消定时调度（如果有）
        taskScheduler.cancelScheduledTask(taskId);

        // 3. 状态置为 DISABLED
        def.setStatus("DISABLED");
        taskDefRepo.save(def);

        log.info("任务已禁用: {}", taskId);
        return "任务已禁用";
    }
}