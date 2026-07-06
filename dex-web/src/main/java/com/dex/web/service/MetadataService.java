package com.dex.web.service;

import com.dex.common.model.metadata.DataSourceMeta;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetadataService {

    private final Map<String, DataSourceMeta> dataSourceMap = new ConcurrentHashMap<>();

    public DataSourceMeta save(DataSourceMeta meta) {
        if (meta.getId() == null) {
            meta.setId(UUID.randomUUID().toString());
        }
        meta.setCreateTime(new Date());
        meta.setUpdateTime(new Date());
        dataSourceMap.put(meta.getId(), meta);
        return meta;
    }

    public DataSourceMeta get(String id) {
        return dataSourceMap.get(id);
    }

    public List<DataSourceMeta> list() {
        return new ArrayList<>(dataSourceMap.values());
    }

    public DataSourceMeta update(DataSourceMeta meta) {
        meta.setUpdateTime(new Date());
        dataSourceMap.put(meta.getId(), meta);
        return meta;
    }

    public void delete(String id) {
        dataSourceMap.remove(id);
    }
}