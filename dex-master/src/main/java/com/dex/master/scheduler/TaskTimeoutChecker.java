package com.dex.master.scheduler;

import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.model.enums.TaskState;
import com.dex.common.repository.TaskInstanceRepository;
import com.dex.master.service.TaskStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class TaskTimeoutChecker {

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Autowired
    private TaskStateManager stateManager;

    /**
     * 每分钟检查一次超时任务
     */
    @Scheduled(fixedRate = 60000)
    public void checkTimeouts() {
        // 查找 RUNNING 状态且最后心跳超过 timeoutMs 的任务
        List<TaskInstanceEntity> runningTasks = taskInstanceRepo.findByState("RUNNING");
        Date now = new Date();

        for (TaskInstanceEntity task : runningTasks) {
            Long timeoutMs = task.getTaskTimeoutMs();
            if (timeoutMs == null) timeoutMs = 3600000L; // 默认 1 小时

            Date lastHeartbeat = task.getLastHeartbeat();
            if (lastHeartbeat == null) {
                lastHeartbeat = task.getStartTime();
                if (lastHeartbeat == null) {
                    lastHeartbeat = task.getCreateTime();
                }
            }
            if (lastHeartbeat == null) continue;

            long elapsed = now.getTime() - lastHeartbeat.getTime();
            if (elapsed > timeoutMs) {
                log.warn("任务超时: instanceId={}, taskId={}, 已执行 {} 毫秒",
                        task.getInstanceId(), task.getTaskId(), elapsed);
                stateManager.transition(task.getInstanceId(), TaskState.TIMEOUT,
                        "任务执行超时，超时时间: " + timeoutMs + "ms");
                // 可触发告警
            }
        }
    }
}