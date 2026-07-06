package com.dex.common.model.metadata;

import lombok.Data;
import java.util.Date;
import java.util.Map;

@Data
public class DataSourceMeta {
    private String id;
    private String name;
    private String type;
    private String description;
    private String status; // ONLINE, OFFLINE, TESTING
    private Map<String, Object> config;
    private Date createTime;
    private Date updateTime;
}