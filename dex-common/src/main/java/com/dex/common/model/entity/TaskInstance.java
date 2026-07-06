package com.dex.common.model.entity;

import lombok.Data;
import java.util.Date;

@Data
public class TaskInstance {
    private String instanceId;
    private String taskId;
    private String state; // PENDING, RUNNING, SUCCESS, FAILED, STOPPED
    private String assignedWorkerId;
    private Date startTime;
    private Date endTime;
    private Long processedRecords;
    private String errorMessage;
}