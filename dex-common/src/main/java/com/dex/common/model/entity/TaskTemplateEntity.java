package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "task_template")
@Data
public class TaskTemplateEntity {
    @Id
    @Column(length = 32)
    private String id;
    @Column(nullable = false)
    private String name;
    private String description;
    private String category;
    @Column(nullable = false)
    private String mode;
    @Column(name = "source_template", columnDefinition = "JSON", nullable = false)
    private String sourceTemplate;
    @Column(name = "sink_template", columnDefinition = "JSON", nullable = false)
    private String sinkTemplate;
    @Column(name = "transform_templates", columnDefinition = "JSON")
    private String transformTemplates;
    @Column(name = "default_batch_size")
    private Integer defaultBatchSize;
    @Column(name = "default_cron")
    private String defaultCron;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time")
    private Date updateTime;
}