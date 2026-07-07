package com.dex.master.scheduler;

import com.dex.common.model.entity.TaskDefinitionEntity;
import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.model.enums.TaskState;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.TaskDefinitionRepository;
import com.dex.common.repository.TaskInstanceRepository;
import com.dex.common.util.JsonUtil;
import com.dex.master.dispatch.TaskDispatcher;
import com.dex.master.service.TaskStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TaskRetryManager {

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Autowired
    private TaskDefinitionRepository taskDefRepo;

    @Autowired
    private TaskStateManager stateManager;

    @Autowired
    private TaskDispatcher taskDispatcher;

    /**
     * 每 30 秒检查一次可重试任务
     */
    @Scheduled(fixedRate = 30000)
    public void retryFailedTasks() {
        List<TaskInstanceEntity> failedTasks = taskInstanceRepo.findRetryableTasks();
        for (TaskInstanceEntity instance : failedTasks) {
            if (instance.getRetryCount() >= instance.getMaxRetries()) {
                continue;
            }

            instance.setRetryCount(instance.getRetryCount() + 1);
            stateManager.transition(instance.getInstanceId(), TaskState.RETRYING, null);

            // ✅ 修正：使用显式类型，不使用 var
            TaskDefinitionEntity def = taskDefRepo.findById(instance.getTaskId()).orElse(null);
            if (def == null) {
                log.warn("任务定义不存在，无法重试: {}", instance.getTaskId());
                continue;
            }
            TaskConfig config = JsonUtil.fromJson(def.getConfigJson(), TaskConfig.class);
            if (config == null) {
                log.warn("任务配置解析失败，无法重试: {}", instance.getTaskId());
                continue;
            }

            stateManager.transition(instance.getInstanceId(), TaskState.RUNNING, null);
            taskDispatcher.dispatchTask(config, instance.getInstanceId());
            log.info("任务重试: instanceId={}, retryCount={}/{}",
                    instance.getInstanceId(), instance.getRetryCount(), instance.getMaxRetries());
        }
    }
}