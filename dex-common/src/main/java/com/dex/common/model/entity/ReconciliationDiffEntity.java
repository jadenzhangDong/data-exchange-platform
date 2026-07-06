package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "reconciliation_diff")
@Data
public class ReconciliationDiffEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false, length = 32)
    private String jobId;
    @Column(name = "config_id", nullable = false, length = 32)
    private String configId;
    @Column(name = "diff_type", nullable = false, length = 20)
    private String diffType; // MISSING, EXTRA, CONTENT_DIFF
    @Column(name = "pk_value", nullable = false, length = 200)
    private String pkValue;
    @Column(length = 20)
    private String status;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "fixed_time")
    private Date fixedTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
}