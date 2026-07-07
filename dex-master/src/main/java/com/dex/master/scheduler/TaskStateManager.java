package com.dex.master.scheduler;

import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.model.enums.TaskState;
import com.dex.common.repository.TaskInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class TaskStateManager {

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Transactional
    public boolean transition(String instanceId, TaskState targetState, String errorMessage) {
        return transition(instanceId, targetState, errorMessage, null);
    }

    @Transactional
    public boolean transition(String instanceId, TaskState targetState, String errorMessage, Long processedRecords) {
        TaskInstanceEntity instance = taskInstanceRepo.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("任务实例不存在: {}", instanceId);
            return false;
        }

        TaskState currentState = TaskState.valueOf(instance.getState());
        if (!currentState.canTransitionTo(targetState)) {
            log.warn("非法状态转换: {} -> {} (instanceId={})", currentState, targetState, instanceId);
            return false;
        }

        instance.setState(targetState.name());
        if (errorMessage != null) {
            instance.setErrorMessage(errorMessage);
        }
        if (processedRecords != null) {
            instance.setProcessedRecords(processedRecords);
        }
        if (targetState.isTerminal()) {
            instance.setEndTime(new Date());
        }
        if (targetState == TaskState.RUNNING || targetState == TaskState.RETRYING) {
            instance.setStartTime(new Date());
        }
        instance.setLastHeartbeat(new Date());
        taskInstanceRepo.save(instance);

        log.info("状态转换: {} -> {} (instanceId={})", currentState, targetState, instanceId);
        return true;
    }

    @Transactional
    public void heartbeat(String instanceId, Long processedRecords) {
        TaskInstanceEntity instance = taskInstanceRepo.findById(instanceId).orElse(null);
        if (instance == null) return;
        if (!TaskState.RUNNING.name().equals(instance.getState())) return;

        instance.setLastHeartbeat(new Date());
        if (processedRecords != null) {
            instance.setProcessedRecords(processedRecords);
        }
        taskInstanceRepo.save(instance);
    }
}