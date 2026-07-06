package com.dex.common.model.entity;

import lombok.Data;
import java.util.Date;

@Data
public class TaskDefinition {
    private String taskId;
    private String taskName;
    private String mode;
    private String configJson;
    private String cronExpression;
    private String status; // ENABLED, DISABLED
    private Date createTime;
    private Date updateTime;
}