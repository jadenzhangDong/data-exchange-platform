package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "data_source_meta")
@Data
public class DataSourceMetaEntity {
    @Id
    @Column(length = 32)
    private String id;
    @Column(length = 100)
    private String name;
    @Column(length = 50)
    private String type;
    @Column(length = 255)
    private String description;
    @Column(columnDefinition = "JSON")
    private String config;
    @Column(length = 20)
    private String status;
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}