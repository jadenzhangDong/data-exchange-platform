package com.dex.common.repository;

import com.dex.common.model.entity.TaskInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskInstanceRepository extends JpaRepository<TaskInstanceEntity, String> {
    List<TaskInstanceEntity> findByTaskId(String taskId);
    List<TaskInstanceEntity> findByState(String state);
    List<TaskInstanceEntity> findByAssignedWorkerIdAndState(String workerId, String state);
}