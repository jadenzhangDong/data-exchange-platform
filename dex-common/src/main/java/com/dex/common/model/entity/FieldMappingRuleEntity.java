package com.dex.common.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "field_mapping_rule")
@Data
public class FieldMappingRuleEntity {
    @Id
    @Column(length = 32)
    private String id;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(name = "source_table_id")
    private String sourceTableId;
    @Column(name = "target_table_id")
    private String targetTableId;
    @Column(name = "mapping_json", columnDefinition = "JSON", nullable = false)
    private String mappingJson;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "create_time")
    private Date createTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "update_time")
    private Date updateTime;
}
