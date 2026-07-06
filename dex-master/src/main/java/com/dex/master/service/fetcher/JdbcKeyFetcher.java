package com.dex.master.service.fetcher;

import com.dex.common.model.entity.DataSourceMetaEntity;  // 使用 model 包
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

    public JdbcKeyFetcher(DataSourceMetaEntity ds, String table, String primaryKey,
                          String incrementColumn, String extCondition) throws SQLException {
        this(ds, table, primaryKey, incrementColumn, "TIMESTAMP", extCondition);
    }

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

    // ========== 分段校验和计算 ==========

    public Map<Integer, String> fetchSegmentChecksums(Date windowStart, Date windowEnd,
                                                      String compareColumns, int segmentSize) throws SQLException {
        if (segmentSize <= 0) segmentSize = 10000;
        Map<Integer, String> result = new LinkedHashMap<>();

        // ✅ 修复1：增加 GROUP_CONCAT 最大长度，防止截断
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET SESSION group_concat_max_len = 10485760");
        }

        String whereClause = buildWhereClause(windowStart, windowEnd, null, null);

        String selectCols;
        if (compareColumns != null && !compareColumns.trim().isEmpty()) {
            selectCols = primaryKey + ", " + compareColumns;
        } else {
            selectCols = "*";
        }

        String sql = String.format(
                "SELECT FLOOR(`%s` / %d) AS segment_id, " +
                        "MD5(GROUP_CONCAT(CONCAT_WS('|', %s) ORDER BY `%s`)) AS checksum " +
                        "FROM %s %s " +
                        "GROUP BY segment_id",
                primaryKey, segmentSize,
                selectCols, primaryKey,
                table, whereClause
        );

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int segmentId = rs.getInt("segment_id");
                String checksum = rs.getString("checksum");
                result.put(segmentId, checksum);
            }
        }

        log.debug("计算分段校验和完成，共 {} 段，每段 {} 行", result.size(), segmentSize);
        return result;
    }

    public Set<String> fetchKeysForSegment(Date windowStart, Date windowEnd,
                                           int segmentId, int segmentSize,
                                           Consumer<Integer> progressCallback) throws SQLException {
        Set<String> keys = new HashSet<>();
        long minId = (long) segmentId * segmentSize;
        long maxId = (long) (segmentId + 1) * segmentSize - 1;

        String whereClause = buildWhereClause(windowStart, windowEnd, minId, maxId);
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
        Set<KeyChecksum> result = new HashSet<>();
        String whereClause = buildWhereClause(windowStart, windowEnd, null, null);
        String countSql = "SELECT COUNT(*) FROM " + table + " " + whereClause;
        long total = 0;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) total = rs.getLong(1);
        }
        if (progressCallback != null) progressCallback.accept(0);

        String selectCols = primaryKey;
        if (compareColumns != null && !compareColumns.trim().isEmpty()) {
            selectCols += ", " + compareColumns;
        } else {
            selectCols += ", *";
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
                int progress = (int) (offset * 100 / total);
                progressCallback.accept(progress > 100 ? 100 : progress);
            }
        }
        return result;
    }

    public String buildWhereClause(Date windowStart, Date windowEnd, Long startId, Long endId) {
        List<String> conditions = new ArrayList<>();

        if ("NUMBER".equalsIgnoreCase(incrementType) || (incrementColumn != null && "id".equalsIgnoreCase(incrementColumn))) {
            if (startId != null && endId != null) {
                conditions.add(incrementColumn + " >= " + startId + " AND " + incrementColumn + " < " + endId);
            }
        } else {
            if (incrementColumn != null && !incrementColumn.isEmpty() && windowStart != null && windowEnd != null) {
                conditions.add(incrementColumn + " >= '" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(windowStart) +
                        "' AND " + incrementColumn + " <= '" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(windowEnd) + "'");
            }
        }

        if (extCondition != null && !extCondition.isEmpty()) {
            conditions.add(extCondition);
        }

        if (conditions.isEmpty()) {
            return "";
        }
        return "WHERE " + String.join(" AND ", conditions);
    }

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