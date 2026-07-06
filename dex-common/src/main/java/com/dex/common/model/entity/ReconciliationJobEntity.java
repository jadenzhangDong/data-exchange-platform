package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "reconciliation_job")
@Data
public class ReconciliationJobEntity {
    @Id
    @Column(name = "job_id", length = 32)
    private String jobId;
    @Column(name = "config_id", nullable = false, length = 32)
    private String configId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "window_start", nullable = false)
    private Date windowStart;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "window_end", nullable = false)
    private Date windowEnd;
    @Column(name = "source_count")
    private Long sourceCount;
    @Column(name = "target_count")
    private Long targetCount;
    @Column(name = "diff_count")
    private Long diffCount;
    @Column(name = "source_missing_count")
    private Long sourceMissingCount;
    @Column(name = "target_extra_count")
    private Long targetExtraCount;
    @Column(length = 20)
    private String status;
    @Column(name = "error_msg", length = 500)
    private String errorMsg;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "start_time")
    private Date startTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "end_time")
    private Date endTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    // ===== 新增字段 =====
    @Column(name = "processed_count")
    private Long processedCount;
    @Column(name = "total_count")
    private Long totalCount;
    @Column(name = "progress_percent")
    private Integer progressPercent;
    @Column(name = "cancelled")
    private Boolean cancelled;
    @Column(name = "source_checksum_count")
    private Long sourceChecksumCount;
    @Column(name = "target_checksum_count")
    private Long targetChecksumCount;
}