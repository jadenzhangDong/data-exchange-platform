package com.dex.web.service;

import com.dex.common.model.metadata.ColumnMeta;
import com.dex.common.model.metadata.TableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class SchemaInspectorService {
    private static final Logger log = LoggerFactory.getLogger(SchemaInspectorService.class);

    public List<TableMeta> inspect(String dataSourceId, Map<String, Object> config) {
        log.info("开始探查数据源: dataSourceId={}, config={}", dataSourceId, config);

        // ===== 安全解析连接参数 =====
        String host = getString(config, "host");
        Integer port = getInteger(config, "port");
        String database = getString(config, "database");
        String user = getString(config, "user");
        String password = getString(config, "password");
        String jdbcUrl = getString(config, "jdbcUrl");

        if (jdbcUrl == null && host != null && database != null) {
            int p = port != null ? port : 3306;
            jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true",
                    host, p, database);
        }

        if (jdbcUrl == null) {
            throw new IllegalArgumentException("无法构建 JDBC URL，请检查配置中的 host/port/database 或直接提供 jdbcUrl");
        }

        log.info("使用 JDBC URL: {}", jdbcUrl);

        List<TableMeta> tables = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            log.info("数据库连接成功");
            DatabaseMetaData meta = conn.getMetaData();

            // 获取所有表（TABLE, VIEW）
            try (ResultSet rs = meta.getTables(database, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");

                    TableMeta table = new TableMeta();
                    table.setId(UUID.randomUUID().toString());
                    table.setDataSourceId(dataSourceId);
                    table.setSchemaName(database);
                    table.setTableName(tableName);
                    table.setTableType(tableType);
                    table.setRowCount(getTableRowCount(conn, database, tableName));

                    // 获取列信息
                    List<ColumnMeta> columns = new ArrayList<>();
                    try (ResultSet colRs = meta.getColumns(database, null, tableName, "%")) {
                        while (colRs.next()) {
                            ColumnMeta col = new ColumnMeta();
                            col.setColumnName(colRs.getString("COLUMN_NAME"));
                            col.setDataType(colRs.getString("TYPE_NAME"));
                            col.setColumnLength(colRs.getInt("COLUMN_SIZE"));
                            col.setIsNullable("YES".equals(colRs.getString("IS_NULLABLE")));
                            col.setComment(colRs.getString("REMARKS"));
                            col.setDefaultValue(colRs.getString("COLUMN_DEF"));
                            columns.add(col);
                        }
                    }

                    // 获取主键信息
                    try (ResultSet pkRs = meta.getPrimaryKeys(database, null, tableName)) {
                        Set<String> pkSet = new HashSet<>();
                        while (pkRs.next()) {
                            pkSet.add(pkRs.getString("COLUMN_NAME"));
                        }
                        for (ColumnMeta col : columns) {
                            col.setIsPrimaryKey(pkSet.contains(col.getColumnName()));
                        }
                    }

                    table.setColumns(columns);
                    tables.add(table);
                    log.info("发现表: {}, 列数: {}", tableName, columns.size());
                }
            }

            log.info("探查完成，共发现 {} 张表", tables.size());

        } catch (SQLException e) {
            log.error("探查数据库失败", e);
            throw new RuntimeException("探查数据库失败: " + e.getMessage() + " (SQLState: " + e.getSQLState() + ")", e);
        } catch (Exception e) {
            log.error("探查过程发生异常", e);
            throw new RuntimeException("探查过程发生异常: " + e.getMessage(), e);
        }

        return tables;
    }

    private Long getTableRowCount(Connection conn, String database, String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM `%s`.`%s`", database, tableName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.warn("获取表行数失败: {}", tableName);
        }
        return 0L;
    }

    // ===== 安全类型转换辅助方法 =====

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}