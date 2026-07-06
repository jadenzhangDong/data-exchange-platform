package com.dex.master.service.fetcher;

import com.dex.common.model.entity.DataSourceMetaEntity;
import com.dex.common.model.KeyChecksum;
import com.dex.common.service.KeyFetcher;
import com.dex.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.function.Consumer;

public class JdbcKeyFetcher implements KeyFetcher {
    private static final Logger log = LoggerFactory.getLogger(JdbcKeyFetcher.class);
    private final Connection conn;
    private final String table;
    private final String primaryKey;
    private final String incrementColumn;
    private final String incrementType;
    private final String extCondition;
    private final int pageSize = 10000;
    private volatile boolean cancelled = false;

    // 构造器（5 参数，兼容旧调用）
    public JdbcKeyFetcher(DataSourceMetaEntity ds, String table, String primaryKey,
                          String incrementColumn, String extCondition) throws SQLException {
        this(ds, table, primaryKey, incrementColumn, "TIMESTAMP", extCondition);
    }

    // 构造器（6 参数，支持增量类型）
    public JdbcKeyFetcher(DataSourceMetaEntity ds, String table, String primaryKey,
                          String incrementColumn, String incrementType, String extCondition) throws SQLException {
        this.table = table;
        this.primaryKey = primaryKey;
        this.incrementColumn = incrementColumn;
        this.incrementType = incrementType != null ? incrementType : "TIMESTAMP";
        this.extCondition = extCondition;
        Map<String, Object> config = JsonUtil.fromJson(ds.getConfig(), Map.class);
        this.conn = getConnection(config);
    }

    private Connection getConnection(Map<String, Object> config) throws SQLException {
        String url = (String) config.get("jdbcUrl");
        if (url == null) {
            String host = (String) config.get("host");
            Integer port = getInteger(config, "port");
            String database = (String) config.get("database");
            int p = port != null ? port : 3306;
            url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    host, p, database);
        }
        String user = (String) config.get("user");
        String password = (String) config.get("password");
        return DriverManager.getConnection(url, user, password);
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
    @Override
    public Set<String> fetchKeys(Date windowStart, Date windowEnd) throws Exception {
        return fetchKeys(windowStart, windowEnd, null);
    }

    public Set<String> fetchKeys(Date windowStart, Date windowEnd, Consumer<Integer> progressCallback) throws SQLException {
        Set<String> keys = new HashSet<>();
        String whereClause = buildWhereClause(windowStart, windowEnd, null, null);
        String countSql = "SELECT COUNT(*) FROM " + table + " " + whereClause;
        long total = 0;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) total = rs.getLong(1);
        }
        if (progressCallback != null) progressCallback.accept(0);
        String sql = "SELECT " + primaryKey + " FROM " + table + " " + whereClause + " LIMIT ? OFFSET ?";
        long offset = 0;
        while (!cancelled) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, pageSize);
                pstmt.setLong(2, offset);
                try (ResultSet rs = pstmt.executeQuery()) {
                    boolean hasData = false;
                    while (rs.next()) {
                        keys.add(rs.getString(1));
                        hasData = true;
                    }
                    if (!hasData) break;
                }
            }
            offset += pageSize;
            if (progressCallback != null) {
                int progress = (int) (offset * 100 / total);
                progressCallback.accept(progress > 100 ? 100 : progress);
            }
        }
        return keys;
    }

    @Override
    public Set<KeyChecksum> fetchKeysWithChecksum(Date windowStart, Date windowEnd,
                                                  String compareColumns,
                                                  Consumer<Integer> progressCallback) throws SQLException {
        return fetchKeysWithChecksum(windowStart, windowEnd, compareColumns, progressCallback, null, null);
    }

    public Set<KeyChecksum> fetchKeysWithChecksum(Date windowStart, Date windowEnd,
                                                  String compareColumns,
                                                  Consumer<Integer> progressCallback,
                                                  Long startId, Long endId) throws SQLException {
        Set<KeyChecksum> result = new HashSet<>();
        String whereClause = buildWhereClause(windowStart, windowEnd, startId, endId);

        // 获取总数
        String countSql = "SELECT COUNT(*) FROM " + table + " " + whereClause;
        long total = 0;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) total = rs.getLong(1);
        }
        if (progressCallback != null) progressCallback.accept(0);

        // 构建 SELECT 列
        String selectCols;
        if (compareColumns != null && !compareColumns.trim().isEmpty()) {
            selectCols = primaryKey + ", " + compareColumns;
        } else {
            selectCols = "*";
        }

        String sql = "SELECT " + selectCols + " FROM " + table + " " + whereClause + " LIMIT ? OFFSET ?";

        long offset = 0;
        while (!cancelled) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, pageSize);
                pstmt.setLong(2, offset);
                try (ResultSet rs = pstmt.executeQuery()) {
                    boolean hasData = false;
                    while (rs.next()) {
                        String key = rs.getString(primaryKey);
                        String checksum = null;
                        if (compareColumns != null && !compareColumns.trim().isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (String col : compareColumns.split(",")) {
                                Object val = rs.getObject(col.trim());
                                sb.append(val != null ? val.toString() : "NULL").append("|");
                            }
                            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                            checksum = sb.toString();
                            try {
                                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                                byte[] digest = md.digest(checksum.getBytes("UTF-8"));
                                checksum = javax.xml.bind.DatatypeConverter.printHexBinary(digest).toUpperCase();
                            } catch (Exception e) {
                                checksum = String.valueOf(checksum.hashCode());
                            }
                        }
                        result.add(new KeyChecksum(key, checksum));
                        hasData = true;
                    }
                    if (!hasData) break;
                }
            }
            offset += pageSize;
            if (progressCallback != null) {
                int progress = (int) (offset * 100 / (total > 0 ? total : 1));
                progressCallback.accept(progress > 100 ? 100 : progress);
            }
        }
        return result;
    }

    /**
     * 构建 WHERE 子句，支持 ID 范围或时间窗口
     */
    public String buildWhereClause(Date windowStart, Date windowEnd, Long startId, Long endId) {
        List<String> conditions = new ArrayList<>();

        // 判断增量类型
        if ("NUMBER".equalsIgnoreCase(incrementType) || (incrementColumn != null && "id".equalsIgnoreCase(incrementColumn))) {
            // 数值类型：按 ID 范围
            if (startId != null && endId != null) {
                conditions.add(incrementColumn + " >= " + startId + " AND " + incrementColumn + " < " + endId);
            }
        } else {
            // 时间戳类型：按时间窗口
            if (incrementColumn != null && !incrementColumn.isEmpty() && windowStart != null && windowEnd != null) {
                conditions.add(incrementColumn + " >= '" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(windowStart) +
                        "' AND " + incrementColumn + " <= '" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(windowEnd) + "'");
            }
        }

        // 额外条件
        if (extCondition != null && !extCondition.isEmpty()) {
            conditions.add(extCondition);
        }

        if (conditions.isEmpty()) {
            return "";
        }
        return "WHERE " + String.join(" AND ", conditions);
    }

    /**
     * 获取表的最小和最大 ID
     */
    public long[] getIdRange() throws SQLException {
        String sql = "SELECT MIN(" + incrementColumn + "), MAX(" + incrementColumn + ") FROM " + table;
        if (extCondition != null && !extCondition.isEmpty()) {
            sql += " WHERE " + extCondition;
        }
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new long[]{rs.getLong(1), rs.getLong(2)};
            }
        }
        return new long[]{0, 0};
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void close() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}