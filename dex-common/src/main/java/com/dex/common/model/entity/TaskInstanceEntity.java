package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "task_instance")
@Data
public class TaskInstanceEntity {
    @Id
    @Column(name = "instance_id", length = 32)
    private String instanceId;
    @Column(name = "task_id", length = 32, nullable = false)
    private String taskId;
    @Column(name = "parent_instance_id", length = 32)
    private String parentInstanceId;
    @Column(name = "sub_task_index")
    private Integer subTaskIndex;
    @Column(length = 20, nullable = false)
    private String state;
    @Column(name = "assigned_worker_id", length = 100)
    private String assignedWorkerId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "start_time")
    private Date startTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "end_time")
    private Date endTime;
    @Column(name = "processed_records")
    private Long processedRecords;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;

    // ===== 新增状态机字段 =====
    @Column(name = "max_retries")
    private Integer maxRetries = 0;
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_heartbeat")
    private Date lastHeartbeat;
    @Column(name = "task_timeout_ms")
    private Long taskTimeoutMs = 3600000L;
}