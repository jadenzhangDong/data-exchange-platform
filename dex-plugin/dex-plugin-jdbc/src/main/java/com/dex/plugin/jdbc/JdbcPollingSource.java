package com.dex.plugin.jdbc;

import com.dex.plugin.api.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JDBC 轮询 Source 插件，水位线存储在管理库（通过 DataSource 访问）
 * <p>
 * 配置参数：
 * <ul>
 *   <li>url: 业务数据源 JDBC URL（必填）</li>
 *   <li>user: 业务数据源用户名（必填）</li>
 *   <li>password: 业务数据源密码（必填）</li>
 *   <li>driverClass: 驱动类名（可选）</li>
 *   <li>table: 业务源表名（必填）</li>
 *   <li>incrementColumn: 增量字段（必填）</li>
 *   <li>incrementType: NUMBER 或 TIMESTAMP（默认 NUMBER）</li>
 *   <li>primaryKey: 主键字段（默认 incrementColumn）</li>
 *   <li>initialWatermark: 初始水位线（默认 0）</li>
 *   <li>fetchSize: 每批读取条数（默认 1000）</li>
 *   <li>maxRetries: 最大重试次数（默认 3）</li>
 *   <li>taskId: 任务ID（必填，用于水位线持久化）</li>
 *   <li>shardStartId: 分片起始ID（Master自动注入）</li>
 *   <li>shardEndId: 分片结束ID（Master自动注入）</li>
 *   <li>_managementDataSource: 管理库 DataSource（Worker 自动注入）</li>
 * </ul>
 */
public class JdbcPollingSource implements Source<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(JdbcPollingSource.class);

    // ========== 业务数据源配置 ==========
    private String url;
    private String user;
    private String password;
    private String driverClass;
    private String tableName;
    private String incrementColumn;
    private String incrementType;
    private String primaryKey;
    private Integer fetchSize = 1000;
    private Integer maxRetries = 3;
    private String taskId;

    // ========== 运行时状态 ==========
    private Connection businessConn;          // 业务数据源连接（用于查询数据）
    private DataSource managementDataSource;  // 管理库数据源（用于水位线读写）
    private AtomicLong watermark = new AtomicLong(0L);
    private boolean isTimestampMode = false;
    private long pendingWatermark = 0;

    // ========== 分片参数 ==========
    private Long shardStartId;
    private Long shardEndId;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        // 1. 解析业务数据源配置
        this.url = (String) config.get("url");
        this.user = (String) config.get("user");
        this.password = (String) config.get("password");
        this.driverClass = (String) config.get("driverClass");
        this.tableName = (String) config.get("table");
        this.incrementColumn = (String) config.get("incrementColumn");
        this.primaryKey = (String) config.getOrDefault("primaryKey", incrementColumn);
        this.fetchSize = (Integer) config.getOrDefault("fetchSize", 1000);
        this.maxRetries = (Integer) config.getOrDefault("maxRetries", 3);
        this.taskId = (String) config.get("taskId");

        if (url == null || user == null || password == null || tableName == null || incrementColumn == null) {
            throw new IllegalArgumentException("url, user, password, table, incrementColumn 为必填参数");
        }
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("taskId 为必填参数（用于水位线持久化）");
        }

        // 2. 获取管理库 DataSource（由 Worker 注入）
        this.managementDataSource = (DataSource) config.get("_managementDataSource");
        if (this.managementDataSource == null) {
            throw new IllegalArgumentException("_managementDataSource 未注入，请检查 Worker 配置");
        }

        // 3. 判断增量类型
        String incType = (String) config.get("incrementType");
        if (incType == null) {
            incType = detectIncrementType();
        }
        this.incrementType = incType;
        this.isTimestampMode = "TIMESTAMP".equalsIgnoreCase(incType);

        // 4. 读取分片参数
        Object startObj = config.get("shardStartId");
        Object endObj = config.get("shardEndId");
        if (startObj != null && endObj != null) {
            this.shardStartId = ((Number) startObj).longValue();
            this.shardEndId = ((Number) endObj).longValue();
            log.info("启用分片模式: startId={}, endId={}", shardStartId, shardEndId);
        }

        // 5. 加载驱动并建立业务连接
        if (driverClass != null && !driverClass.isEmpty()) {
            Class.forName(driverClass);
        }
        this.businessConn = DriverManager.getConnection(url, user, password);
        this.businessConn.setAutoCommit(true);

        // 6. 验证业务表结构
        validateColumns();

        // 7. 从管理库加载水位线
        loadWatermarkFromManagementDb(config);

        log.info("JdbcPollingSource 初始化完成: table={}, column={}, type={}, watermark={}",
                tableName, incrementColumn, incrementType, watermark.get());
    }

    private void loadWatermarkFromManagementDb(Map<String, Object> config) {
        Long saved = getWatermarkFromManagementDb(taskId, tableName, incrementColumn);
        if (saved != null) {
            watermark.set(saved);
            log.info("从管理库恢复水位线: {}", saved);
            return;
        }

        // 无保存值，使用初始水位线
        Object initWatermark = config.get("initialWatermark");
        if (initWatermark != null) {
            setInitialWatermark(initWatermark);
        }
    }

    private Long getWatermarkFromManagementDb(String taskId, String table, String column) {
        String sql = "SELECT watermark_value FROM task_watermark WHERE task_id = ? AND source_table = ? AND increment_column = ?";
        try (Connection conn = managementDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            pstmt.setString(2, table);
            pstmt.setString(3, column);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.warn("读取水位线失败", e);
        }
        return null;
    }

    private void saveWatermarkToManagementDb(String taskId, String table, String column, long value) {
        String selectSql = "SELECT COUNT(*) FROM task_watermark WHERE task_id = ? AND source_table = ? AND increment_column = ?";
        try (Connection conn = managementDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, taskId);
            pstmt.setString(2, table);
            pstmt.setString(3, column);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    // 更新
                    String updateSql = "UPDATE task_watermark SET watermark_value = ? WHERE task_id = ? AND source_table = ? AND increment_column = ?";
                    try (PreparedStatement upstmt = conn.prepareStatement(updateSql)) {
                        upstmt.setLong(1, value);
                        upstmt.setString(2, taskId);
                        upstmt.setString(3, table);
                        upstmt.setString(4, column);
                        upstmt.executeUpdate();
                    }
                } else {
                    // 插入
                    String insertSql = "INSERT INTO task_watermark (id, task_id, source_table, increment_column, watermark_value) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement instmt = conn.prepareStatement(insertSql)) {
                        instmt.setString(1, UUID.randomUUID().toString().replace("-", ""));
                        instmt.setString(2, taskId);
                        instmt.setString(3, table);
                        instmt.setString(4, column);
                        instmt.setLong(5, value);
                        instmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            log.error("保存水位线失败", e);
        }
    }

    private void setInitialWatermark(Object initWatermark) {
        if (isTimestampMode) {
            if (initWatermark instanceof Number) {
                watermark.set(((Number) initWatermark).longValue());
            } else if (initWatermark instanceof String) {
                try {
                    Timestamp ts = Timestamp.valueOf((String) initWatermark);
                    watermark.set(ts.getTime());
                } catch (IllegalArgumentException e) {
                    try {
                        watermark.set(Long.parseLong((String) initWatermark));
                    } catch (NumberFormatException ex) {
                        log.warn("无法解析初始水位线: {}", initWatermark);
                    }
                }
            }
        } else {
            if (initWatermark instanceof Number) {
                watermark.set(((Number) initWatermark).longValue());
            } else if (initWatermark instanceof String) {
                try {
                    watermark.set(Long.parseLong((String) initWatermark));
                } catch (NumberFormatException e) {
                    log.warn("无法解析初始水位线: {}", initWatermark);
                }
            }
        }
    }

    @Override
    public List<Map<String, Object>> read(int batchSize) throws Exception {
        if (batchSize <= 0) {
            batchSize = fetchSize;
        }

        // 检查分片模式与增量类型兼容性
        if (shardStartId != null && shardEndId != null && isTimestampMode) {
            log.warn("分片模式不支持 TIMESTAMP 增量类型，降级为普通模式");
            shardStartId = null;
            shardEndId = null;
        }

        // 构建 SQL
        String sql;
        Object queryWatermark;
        StringBuilder whereClause = new StringBuilder();

        if (shardStartId != null && shardEndId != null) {
            whereClause.append(" WHERE ")
                    .append(incrementColumn).append(" >= ").append(shardStartId)
                    .append(" AND ").append(incrementColumn).append(" <= ").append(shardEndId);
            if (watermark.get() > 0) {
                whereClause.append(" AND ").append(incrementColumn).append(" > ?");
                queryWatermark = watermark.get();
            } else {
                queryWatermark = 0;
            }
        } else {
            whereClause.append(" WHERE ").append(incrementColumn).append(" > ?");
            if (isTimestampMode) {
                queryWatermark = new Timestamp(watermark.get());
            } else {
                queryWatermark = watermark.get();
            }
        }

        whereClause.append(" ORDER BY ").append(incrementColumn).append(" ASC");
        if (primaryKey != null && !primaryKey.equals(incrementColumn)) {
            whereClause.append(", ").append(primaryKey).append(" ASC");
        }
        whereClause.append(" LIMIT ?");

        sql = String.format("SELECT * FROM %s %s", tableName, whereClause.toString());

        List<Map<String, Object>> result;
        try (PreparedStatement pstmt = businessConn.prepareStatement(sql)) {
            if (shardStartId != null && shardEndId != null && watermark.get() == 0) {
                // 分片首次无水位线参数
                pstmt.setInt(1, batchSize);
            } else {
                if (isTimestampMode) {
                    pstmt.setTimestamp(1, (Timestamp) queryWatermark);
                } else {
                    pstmt.setLong(1, (Long) queryWatermark);
                }
                pstmt.setInt(2, batchSize);
            }
            result = executeQuery(pstmt);
        }

        return result;
    }

    private List<Map<String, Object>> executeQuery(PreparedStatement pstmt) throws Exception {
        Exception lastException = null;
        for (int retry = 0; retry <= maxRetries; retry++) {
            try {
                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    List<Map<String, Object>> result = new ArrayList<>();
                    Object maxWatermark = null;

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        result.add(row);

                        // 记录当前批次的最大水位线
                        Object val;
                        if (isTimestampMode) {
                            val = rs.getTimestamp(incrementColumn);
                        } else {
                            val = rs.getObject(incrementColumn);
                        }
                        if (val != null) {
                            if (maxWatermark == null) {
                                maxWatermark = val;
                            } else if (val instanceof Comparable) {
                                @SuppressWarnings("unchecked")
                                Comparable<Object> comp = (Comparable<Object>) val;
                                if (comp.compareTo(maxWatermark) > 0) {
                                    maxWatermark = val;
                                }
                            }
                        }
                    }

                    if (!result.isEmpty() && maxWatermark != null) {
                        long newWatermark;
                        if (isTimestampMode) {
                            newWatermark = ((Timestamp) maxWatermark).getTime();
                        } else {
                            newWatermark = ((Number) maxWatermark).longValue();
                        }
                        pendingWatermark = newWatermark;
                        log.debug("暂存待提交水位线: {}", newWatermark);
                    } else {
                        pendingWatermark = 0;
                    }
                    return result;
                }
            } catch (SQLException e) {
                lastException = e;
                log.warn("查询失败，重试 {}/{}", retry + 1, maxRetries + 1, e);
                if (retry < maxRetries) {
                    Thread.sleep(1000L * (retry + 1));
                }
            }
        }
        throw lastException != null ? lastException : new RuntimeException("查询失败");
    }

    @Override
    public void commitWatermark() {
        if (pendingWatermark > watermark.get()) {
            watermark.set(pendingWatermark);
            saveWatermarkToManagementDb(taskId, tableName, incrementColumn, pendingWatermark);
            log.debug("水位线已持久化: taskId={}, watermark={}", taskId, pendingWatermark);
        }
    }

    public long getPendingWatermark() {
        return pendingWatermark;
    }

    // ========== 辅助方法 ==========

    private void validateColumns() throws SQLException {
        try (Statement stmt = businessConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + incrementColumn + ", " + primaryKey + " FROM " + tableName + " LIMIT 0")) {
            // 验证列存在
        } catch (SQLException e) {
            log.error("表或列不存在: table={}, column={}, pk={}", tableName, incrementColumn, primaryKey);
            throw e;
        }
    }

    private String detectIncrementType() throws SQLException {
        try (Statement stmt = businessConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + incrementColumn + " FROM " + tableName + " LIMIT 1")) {
            int type = rs.getMetaData().getColumnType(1);
            if (type == Types.TIMESTAMP || type == Types.DATE || type == Types.TIME) {
                return "TIMESTAMP";
            } else {
                return "NUMBER";
            }
        } catch (SQLException e) {
            log.warn("自动检测增量类型失败，默认 NUMBER", e);
            return "NUMBER";
        }
    }

    @Override
    public void close() throws Exception {
        if (businessConn != null) {
            try { businessConn.close(); } catch (SQLException e) { log.warn("关闭业务连接异常", e); }
        }
        log.info("JdbcPollingSource 关闭");
    }
}