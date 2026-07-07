package com.dex.common.repository;

import com.dex.common.model.entity.TaskInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface TaskInstanceRepository extends JpaRepository<TaskInstanceEntity, String> {
    List<TaskInstanceEntity> findByTaskId(String taskId);
    List<TaskInstanceEntity> findByState(String state);
    List<TaskInstanceEntity> findByAssignedWorkerIdAndState(String workerId, String state);

    // 新增超时查询
    @Query("SELECT t FROM TaskInstanceEntity t WHERE t.state = :state AND t.lastHeartbeat < :threshold")
    List<TaskInstanceEntity> findByStateAndLastHeartbeatBefore(@Param("state") String state, @Param("threshold") Date threshold);

    // 查询需要重试的任务（失败且未超过重试次数）
    @Query("SELECT t FROM TaskInstanceEntity t WHERE t.state = 'FAILED' AND t.retryCount < t.maxRetries")
    List<TaskInstanceEntity> findRetryableTasks();
}