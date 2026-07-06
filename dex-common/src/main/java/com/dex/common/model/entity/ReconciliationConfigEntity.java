package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "reconciliation_config")
@Data
public class ReconciliationConfigEntity {
    @Id
    @Column(length = 32)
    private String id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(name = "source_data_source_id", nullable = false, length = 32)
    private String sourceDataSourceId;
    @Column(name = "target_data_source_id", nullable = false, length = 32)
    private String targetDataSourceId;
    @Column(name = "source_table", nullable = false, length = 100)
    private String sourceTable;
    @Column(name = "target_table", nullable = false, length = 100)
    private String targetTable;
    @Column(name = "primary_key", nullable = false, length = 100)
    private String primaryKey;
    @Column(name = "increment_column", length = 100)
    private String incrementColumn;
    @Column(name = "increment_type", length = 20)
    private String incrementType;  // TIMESTAMP, NUMBER
    @Column(name = "shard_size")
    private Long shardSize;        // ID分片大小，仅NUMBER类型有效
    @Column(name = "check_strategy", length = 20)
    private String checkStrategy;
    @Column(name = "window_unit", length = 10)
    private String windowUnit;
    @Column(name = "window_size")
    private Integer windowSize;
    @Column(name = "delay_minutes")
    private Integer delayMinutes = 5;
    @Column(name = "cron_expression", nullable = false, length = 50)
    private String cronExpression;
    @Column
    private Boolean enabled;
    @Column(name = "ext_condition", length = 500)
    private String extCondition;
    @Column(name = "compare_columns", columnDefinition = "TEXT")
    private String compareColumns;
    @Column(name = "sample_rate")
    private BigDecimal sampleRate;
    @Column(name = "extra_action", length = 20)
    private String extraAction;
    @Column(name = "source_params", columnDefinition = "JSON")
    private String sourceParams;
    @Column(name = "target_params", columnDefinition = "JSON")
    private String targetParams;
    @Column(name = "diff_threshold")
    private Integer diffThreshold;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time")
    private Date updateTime;
}