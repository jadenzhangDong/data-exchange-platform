package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "task_definition")
@Data
public class TaskDefinitionEntity {
    @Id
    @Column(name = "task_id", length = 32)
    private String taskId;
    @Column(name = "task_name", length = 100)
    private String taskName;
    @Column(length = 20)
    private String mode;
    @Column(name = "config_json", columnDefinition = "JSON")
    private String configJson;
    @Column(name = "cron_expression", length = 50)
    private String cronExpression;
    @Column(length = 20)
    private String status;
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}