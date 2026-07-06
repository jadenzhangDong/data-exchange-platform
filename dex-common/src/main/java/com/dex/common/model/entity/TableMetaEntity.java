package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "table_meta")
@Data
public class TableMetaEntity {
    @Id
    @Column(length = 32)
    private String id;
    @Column(name = "data_source_id", length = 32)
    private String dataSourceId;
    @Column(name = "schema_name")
    private String schemaName;
    @Column(name = "table_name", nullable = false)
    private String tableName;
    @Column(name = "table_type")
    private String tableType;
    @Column(columnDefinition = "JSON")
    private String columns;  // JSON 字符串
    @Column(name = "row_count")
    private Long rowCount;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time")
    private Date updateTime;
}