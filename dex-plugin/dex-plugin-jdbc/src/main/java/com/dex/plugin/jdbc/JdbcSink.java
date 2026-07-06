package com.dex.plugin.jdbc;

import com.dex.plugin.api.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class JdbcSink implements Sink<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(JdbcSink.class);

    private Connection conn;
    private String tableName;
    private String primaryKey;
    private int batchSize;
    private List<String> targetColumns;
    private String insertSql;
    private String updateSql;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        String url = (String) config.get("url");
        String user = (String) config.get("user");
        String password = (String) config.get("password");
        this.tableName = (String) config.get("table");
        this.primaryKey = (String) config.getOrDefault("primaryKey", "id");
        this.batchSize = (Integer) config.getOrDefault("batchSize", 1000);

        if (url == null || tableName == null) {
            throw new IllegalArgumentException("必须提供 url 和 table 参数");
        }

        String driverClass = (String) config.get("driverClass");
        if (driverClass != null && !driverClass.isEmpty()) {
            Class.forName(driverClass);
        }

        conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(false);

        targetColumns = getTableColumns();
        if (targetColumns.isEmpty()) {
            throw new SQLException("表 " + tableName + " 不存在或无列");
        }

        prepareStatements();
        log.info("JdbcSink 初始化完成: table={}, primaryKey={}, columns={}",
                tableName, primaryKey, targetColumns);
    }

    private List<String> getTableColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private void prepareStatements() {
        String insertCols = targetColumns.stream()
                .map(col -> "`" + col + "`")
                .collect(Collectors.joining(", "));
        String placeholders = targetColumns.stream()
                .map(col -> "?")
                .collect(Collectors.joining(", "));
        insertSql = String.format("INSERT INTO `%s` (%s) VALUES (%s)", tableName, insertCols, placeholders);

        String setClause = targetColumns.stream()
                .filter(col -> !col.equals(primaryKey))
                .map(col -> "`" + col + "` = ?")
                .collect(Collectors.joining(", "));
        updateSql = String.format("UPDATE `%s` SET %s WHERE `%s` = ?", tableName, setClause, primaryKey);

        log.debug("INSERT SQL: {}", insertSql);
        log.debug("UPDATE SQL: {}", updateSql);
    }

    @Override
    public void write(List<Map<String, Object>> data) throws Exception {
        if (data == null || data.isEmpty()) {
            return;
        }

        int count = 0;
        for (Map<String, Object> row : data) {
            Map<String, Object> filteredRow = new LinkedHashMap<>();
            for (String col : targetColumns) {
                filteredRow.put(col, row.get(col));
            }

            boolean updated = tryUpdate(filteredRow);
            if (!updated) {
                tryInsert(filteredRow);
            }

            count++;
            if (count % batchSize == 0) {
                conn.commit();
                log.debug("已提交 {} 条", count);
            }
        }
        conn.commit();
        log.info("JdbcSink 写入 {} 条数据", data.size());
    }

    private boolean tryUpdate(Map<String, Object> row) throws SQLException {
        List<Object> params = new ArrayList<>();
        for (String col : targetColumns) {
            if (!col.equals(primaryKey)) {
                params.add(row.get(col));
            }
        }
        Object pkValue = row.get(primaryKey);
        if (pkValue == null) {
            return false;
        }
        params.add(pkValue);

        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            int affected = pstmt.executeUpdate();
            return affected > 0;
        }
    }

    private void tryInsert(Map<String, Object> row) throws SQLException {
        List<Object> params = new ArrayList<>();
        for (String col : targetColumns) {
            params.add(row.get(col));
        }

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            pstmt.executeUpdate();
        }
    }

    @Override
    public void close() throws Exception {
        if (conn != null) {
            conn.commit();
            conn.close();
        }
        log.info("JdbcSink 关闭");
    }
}