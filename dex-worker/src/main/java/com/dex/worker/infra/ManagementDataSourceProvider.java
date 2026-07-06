package com.dex.worker.infra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ManagementDataSourceProvider {

    @Autowired
    private DataSource dataSource;  // Spring 自动注入管理库数据源

    public DataSource getDataSource() {
        return dataSource;
    }
}