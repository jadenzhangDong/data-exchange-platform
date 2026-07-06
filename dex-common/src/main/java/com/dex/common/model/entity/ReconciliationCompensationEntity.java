package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "reconciliation_compensation")
@Data
public class ReconciliationCompensationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "diff_id", nullable = false)
    private Long diffId;
    @Column(name = "job_id", nullable = false, length = 32)
    private String jobId;
    @Column(name = "config_id", nullable = false, length = 32)
    private String configId;
    @Column(length = 20, nullable = false)
    private String action; // INSERT, UPDATE, DELETE, IGNORE
    @Column(length = 20)
    private String status; // PENDING, SUCCESS, FAILED
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;
    @Column(name = "compensated_by", length = 50)
    private String compensatedBy;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time")
    private Date updateTime;
}