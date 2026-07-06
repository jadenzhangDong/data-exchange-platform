package com.dex.master.quartz;

import com.dex.common.model.entity.TaskDefinitionEntity;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.TaskDefinitionRepository;
import com.dex.common.util.JsonUtil;
import com.dex.master.dispatch.TaskDispatcher;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TaskQuartzJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(TaskQuartzJob.class);

    @Autowired
    private TaskDefinitionRepository taskDefRepo;

    @Autowired
    private TaskDispatcher taskDispatcher;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String taskId = dataMap.getString("taskId");

        log.info("Quartz 触发定时任务: taskId={}", taskId);

        try {
            // 使用显式类型，不使用 var
            TaskDefinitionEntity def = taskDefRepo.findById(taskId).orElse(null);
            if (def == null) {
                log.warn("任务定义不存在: {}", taskId);
                return;
            }
            if (!"ENABLED".equals(def.getStatus())) {
                log.info("任务已禁用，跳过执行: {}", taskId);
                return;
            }

            TaskConfig config = JsonUtil.fromJson(def.getConfigJson(), TaskConfig.class);
            if (config == null) {
                log.error("解析任务配置失败: {}", taskId);
                return;
            }

            String instanceId = UUID.randomUUID().toString().replace("-", "");
            log.info("Quartz 分发任务: taskId={}, instanceId={}", taskId, instanceId);
            taskDispatcher.dispatchTask(config, instanceId);

        } catch (Exception e) {
            log.error("Quartz 执行任务失败: {}", taskId, e);
        }
    }
}