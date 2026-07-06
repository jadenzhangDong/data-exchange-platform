package com.dex.master.service;

import com.dex.common.alert.AlertClient;
import com.dex.common.model.entity.*;
import com.dex.common.model.KeyChecksum;
import com.dex.common.repository.*;
import com.dex.common.util.JsonUtil;
import com.dex.master.service.fetcher.JdbcKeyFetcher;
import com.dex.master.service.fetcher.KafkaKeyFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    @Autowired
    private ReconciliationConfigRepository configRepo;
    @Autowired
    private ReconciliationJobRepository jobRepo;
    @Autowired
    private ReconciliationDiffRepository diffRepo;
    @Autowired
    private DataSourceMetaRepository dsRepo;

    private final Map<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

    // ========== 入口方法 ==========

    @Transactional
    public void executeReconciliation(String configId) throws Exception {
        ReconciliationConfigEntity config = configRepo.findById(configId).orElse(null);
        if (config == null || !config.getEnabled()) {
            throw new IllegalArgumentException("核对配置不存在或未启用");
        }

        String incrementType = config.getIncrementType() != null ? config.getIncrementType() : "TIMESTAMP";

        if ("NUMBER".equalsIgnoreCase(incrementType) ||
                (config.getIncrementColumn() != null && "id".equalsIgnoreCase(config.getIncrementColumn()))) {
            executeByIdRangeReconciliation(config);
        } else {
            executeByTimeWindowReconciliation(config);
        }
    }

    // ========== 时间窗口核对（原有逻辑） ==========

    private void executeByTimeWindowReconciliation(ReconciliationConfigEntity config) throws Exception {
        log.info("开始时间窗口核对: configId={}", config.getId());

        Date now = new Date();
        Date windowStart = calculateWindowStart(now, config);
        Date windowEnd = now;

        ReconciliationJobEntity job = new ReconciliationJobEntity();
        job.setJobId(UUID.randomUUID().toString().replace("-", ""));
        job.setConfigId(config.getId());
        job.setWindowStart(windowStart);
        job.setWindowEnd(windowEnd);
        job.setStatus("RUNNING");
        job.setStartTime(new Date());
        jobRepo.save(job);

        try {
            DataSourceMetaEntity sourceDs = dsRepo.findById(config.getSourceDataSourceId()).orElse(null);
            DataSourceMetaEntity targetDs = dsRepo.findById(config.getTargetDataSourceId()).orElse(null);
            if (sourceDs == null || targetDs == null) {
                throw new IllegalArgumentException("数据源不存在");
            }

            // 获取源端数据
            Set<KeyChecksum> sourceChecksums = fetchDataWithChecksum(
                    config, sourceDs, config.getSourceTable(),
                    config.getSourceParams(), windowStart, windowEnd,
                    config.getCompareColumns(), null, "source");
            Set<KeyChecksum> targetChecksums = fetchDataWithChecksum(
                    config, targetDs, config.getTargetTable(),
                    config.getTargetParams(), windowStart, windowEnd,
                    config.getCompareColumns(), null, "target");

            // 计算差异
            ReconciliationResult result = computeDiff(sourceChecksums, targetChecksums);
            saveReconciliationResult(job, result, config);

        } catch (Exception e) {
            log.error("核对执行失败", e);
            job.setStatus("FAILED");
            job.setErrorMsg(e.getMessage());
            job.setEndTime(new Date());
            jobRepo.save(job);
            throw e;
        }
    }

    // ========== 辅助方法（时间窗口） ==========
    private Date calculateWindowStart(Date now, ReconciliationConfigEntity config) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);

        // ✅ 第一步：扣除延迟补偿分钟
        if (config.getDelayMinutes() != null && config.getDelayMinutes() > 0) {
            cal.add(Calendar.MINUTE, -config.getDelayMinutes());
            log.debug("扣除延迟补偿 {} 分钟", config.getDelayMinutes());
        }

        // 第二步：扣除窗口大小
        String unit = config.getWindowUnit() != null ? config.getWindowUnit() : "HOUR";
        int size = config.getWindowSize() != null ? config.getWindowSize() : 1;
        switch (unit.toUpperCase()) {
            case "HOUR":
                cal.add(Calendar.HOUR, -size);
                break;
            case "DAY":
                cal.add(Calendar.DAY_OF_MONTH, -size);
                break;
            default:
                cal.add(Calendar.HOUR, -size);
                break;
        }

        return cal.getTime();
    }

    private Set<KeyChecksum> fetchDataWithChecksum(ReconciliationConfigEntity config,
                                                   DataSourceMetaEntity ds,
                                                   String table,
                                                   String paramsJson,
                                                   Date windowStart,
                                                   Date windowEnd,
                                                   String compareColumns,
                                                   Consumer<Integer> progressCallback,
                                                   String side) throws Exception {
        if ("kafka".equals(ds.getType())) {
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> dsConfig = JsonUtil.fromJson(ds.getConfig(), Map.class);
            params.put("bootstrapServers", dsConfig.get("bootstrapServers"));
            Map<String, Object> sourceParams = JsonUtil.fromJson(paramsJson, Map.class);
            params.put("topic", sourceParams.getOrDefault("topic", table));
            params.put("keyField", sourceParams.get("keyField"));
            params.put("timestampField", sourceParams.get("timestampField"));
            params.put("useRecordTimestamp", true);
            params.put("isDebeziumFormat", true);
            KafkaKeyFetcher kf = new KafkaKeyFetcher(params);
            try {
                return kf.fetchKeysWithChecksum(windowStart, windowEnd, compareColumns, progressCallback);
            } finally {
                kf.close();
            }
        } else {
            JdbcKeyFetcher jf = new JdbcKeyFetcher(
                    ds, table, config.getPrimaryKey(),
                    config.getIncrementColumn(),
                    config.getIncrementType(),
                    config.getExtCondition());
            try {
                return jf.fetchKeysWithChecksum(windowStart, windowEnd, compareColumns, progressCallback);
            } finally {
                jf.close();
            }
        }
    }

    // ========== 差异计算与保存 ==========

    private ReconciliationResult computeDiff(Set<KeyChecksum> sourceChecksums, Set<KeyChecksum> targetChecksums) {
        Map<String, KeyChecksum> sourceMap = sourceChecksums.stream()
                .collect(Collectors.toMap(KeyChecksum::getKey, Function.identity(), (a, b) -> a));
        Map<String, KeyChecksum> targetMap = targetChecksums.stream()
                .collect(Collectors.toMap(KeyChecksum::getKey, Function.identity(), (a, b) -> a));

        Set<String> missingKeys = new HashSet<>(sourceMap.keySet());
        missingKeys.removeAll(targetMap.keySet());

        Set<String> extraKeys = new HashSet<>(targetMap.keySet());
        extraKeys.removeAll(sourceMap.keySet());

        Set<String> contentDiffKeys = new HashSet<>();
        for (String key : sourceMap.keySet()) {
            if (targetMap.containsKey(key)) {
                String srcChecksum = sourceMap.get(key).getChecksum();
                String tgtChecksum = targetMap.get(key).getChecksum();
                if (!Objects.equals(srcChecksum, tgtChecksum)) {
                    contentDiffKeys.add(key);
                }
            }
        }

        ReconciliationResult result = new ReconciliationResult();
        result.missingKeys = missingKeys;
        result.extraKeys = extraKeys;
        result.contentDiffKeys = contentDiffKeys;
        return result;
    }

    private void saveReconciliationResult(ReconciliationJobEntity job,
                                          ReconciliationResult result,
                                          ReconciliationConfigEntity config) {
        int diffCount = result.missingKeys.size() + result.extraKeys.size() + result.contentDiffKeys.size();
        job.setSourceCount((long) (result.missingKeys.size() + result.contentDiffKeys.size()));
        job.setTargetCount((long) (result.extraKeys.size() + result.contentDiffKeys.size()));
        job.setSourceMissingCount((long) result.missingKeys.size());
        job.setTargetExtraCount((long) result.extraKeys.size());
        job.setDiffCount((long) diffCount);
        job.setStatus("SUCCESS");
        job.setEndTime(new Date());
        job.setProgressPercent(100);
        jobRepo.save(job);

        // 保存差异明细（限制数量）
        int threshold = config.getDiffThreshold() != null ? config.getDiffThreshold() : 1000;
        if (diffCount > threshold) {
            AlertClient.sendAlert("核对差异超阈值",
                    String.format("配置: %s, 差异数: %d", config.getName(), diffCount));
        }

        int count = 0;
        for (String pk : result.missingKeys) {
            if (count++ >= threshold) break;
            saveDiff(job.getJobId(), config.getId(), "MISSING", pk);
        }
        for (String pk : result.extraKeys) {
            if (count++ >= threshold) break;
            saveDiff(job.getJobId(), config.getId(), "EXTRA", pk);
        }
        for (String pk : result.contentDiffKeys) {
            if (count++ >= threshold) break;
            saveDiff(job.getJobId(), config.getId(), "CONTENT_DIFF", pk);
        }
    }

    private void saveDiff(String jobId, String configId, String type, String pk) {
        ReconciliationDiffEntity diff = new ReconciliationDiffEntity();
        diff.setJobId(jobId);
        diff.setConfigId(configId);
        diff.setDiffType(type);
        diff.setPkValue(pk);
        diff.setStatus("PENDING");
        diff.setCreateTime(new Date());
        diffRepo.save(diff);
    }

    // ========== 取消核对 ==========

    public void cancelJob(String jobId) {
        cancelFlags.put(jobId, true);
        ReconciliationJobEntity job = jobRepo.findById(jobId).orElse(null);
        if (job != null && "RUNNING".equals(job.getStatus())) {
            job.setCancelled(true);
            jobRepo.save(job);
        }
    }

    // ========== 数据补偿 ==========

    @Transactional
    public void compensate(String configId, List<String> pkList) throws Exception {
        if (pkList == null || pkList.isEmpty()) {
            throw new IllegalArgumentException("主键列表不能为空");
        }

        ReconciliationConfigEntity config = configRepo.findById(configId).orElse(null);
        if (config == null) {
            throw new IllegalArgumentException("核对配置不存在");
        }

        DataSourceMetaEntity sourceDs = dsRepo.findById(config.getSourceDataSourceId()).orElse(null);
        DataSourceMetaEntity targetDs = dsRepo.findById(config.getTargetDataSourceId()).orElse(null);
        if (sourceDs == null || targetDs == null) {
            throw new IllegalArgumentException("数据源不存在");
        }

        Map<String, Object> sourceConfig = JsonUtil.fromJson(sourceDs.getConfig(), Map.class);
        Map<String, Object> targetConfig = JsonUtil.fromJson(targetDs.getConfig(), Map.class);

        String sourceTable = config.getSourceTable();
        String targetTable = config.getTargetTable();
        String primaryKey = config.getPrimaryKey();

        // 获取目标表列
        List<String> targetColumns = getTableColumns(targetConfig, targetTable);
        if (targetColumns.isEmpty()) {
            throw new RuntimeException("无法获取目标表结构");
        }

        String columnNames = targetColumns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
        String placeholders = String.join(", ", Collections.nCopies(targetColumns.size(), "?"));
        String replaceSql = String.format("REPLACE INTO `%s` (%s) VALUES (%s)", targetTable, columnNames, placeholders);

        String selectColumns = targetColumns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
        String selectSqlTemplate = String.format("SELECT %s FROM %s WHERE %s IN (", selectColumns, sourceTable, primaryKey);

        try (Connection sourceConn = getConnection(sourceConfig);
             Connection targetConn = getConnection(targetConfig
             )) {
            targetConn.setAutoCommit(false);

            int batchSize = 100;
            for (int i = 0; i < pkList.size(); i += batchSize) {
                List<String> batch = pkList.subList(i, Math.min(i + batchSize, pkList.size()));
                String inClause = String.join(",", Collections.nCopies(batch.size(), "?"));
                String selectSql = selectSqlTemplate + inClause + ")";

                try (PreparedStatement selectStmt = sourceConn.prepareStatement(selectSql)) {
                    for (int j = 0; j < batch.size(); j++) {
                        selectStmt.setObject(j + 1, batch.get(j));
                    }
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        while (rs.next()) {
                            try (PreparedStatement insertStmt = targetConn.prepareStatement(replaceSql)) {
                                for (int colIdx = 0; colIdx < targetColumns.size(); colIdx++) {
                                    insertStmt.setObject(colIdx + 1, rs.getObject(colIdx + 1));
                                }
                                insertStmt.executeUpdate();
                            }
                        }
                    }
                }
                targetConn.commit();
                log.info("补偿批次完成，{} 条", batch.size());
            }

            // 更新差异状态
            for (String pk : pkList) {
                List<ReconciliationDiffEntity> diffs = diffRepo.findByConfigIdAndPkValueAndStatus(configId, pk, "PENDING");
                for (ReconciliationDiffEntity diff : diffs) {
                    diff.setStatus("FIXED");
                    diff.setFixedTime(new Date());
                    diffRepo.save(diff);
                }
            }
            log.info("补偿完成，共 {} 条", pkList.size());
        } catch (SQLException e) {
            log.error("补偿失败", e);
            throw new RuntimeException("补偿失败: " + e.getMessage(), e);
        }
    }

    // ========== 辅助方法 ==========

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
    private List<String> getTableColumns(Map<String, Object> config, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection conn = getConnection(config);
             ResultSet rs = conn.getMetaData().getColumns(null, null, table, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    // ========== 内部类 ==========

    static class ReconciliationResult {
        Set<String> missingKeys = new HashSet<>();
        Set<String> extraKeys = new HashSet<>();
        Set<String> contentDiffKeys = new HashSet<>();
    }

    // ========== ID 分片核对 ==========

    private void executeByIdRangeReconciliation(ReconciliationConfigEntity config) throws Exception {
        log.info("开始 ID 范围分片核对: configId={}", config.getId());

        DataSourceMetaEntity sourceDs = dsRepo.findById(config.getSourceDataSourceId()).orElse(null);
        DataSourceMetaEntity targetDs = dsRepo.findById(config.getTargetDataSourceId()).orElse(null);
        if (sourceDs == null || targetDs == null) {
            throw new IllegalArgumentException("数据源不存在");
        }

        // 获取源表 ID 范围
        Map<String, Object> sourceConfig = JsonUtil.fromJson(sourceDs.getConfig(), Map.class);
        long minId, maxId;
        try (JdbcKeyFetcher fetcher = new JdbcKeyFetcher(
                sourceDs, config.getSourceTable(), config.getPrimaryKey(),
                config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition())) {
            long[] range = fetcher.getIdRange();
            minId = range[0];
            maxId = range[1];
            log.info("ID 范围: {} ~ {}", minId, maxId);
        }

        long shardSize = config.getShardSize() != null ? config.getShardSize() : 1000000L;
        long totalShards = (maxId - minId) / shardSize + 1;
        log.info("分片数: {}", totalShards);

        // 创建核对实例
        ReconciliationJobEntity job = new ReconciliationJobEntity();
        job.setJobId(UUID.randomUUID().toString().replace("-", ""));
        job.setConfigId(config.getId());
        job.setStatus("RUNNING");
        job.setStartTime(new Date());
        job.setTotalCount(totalShards);
        jobRepo.save(job);

        ReconciliationResult mergedResult = new ReconciliationResult();
        long processed = 0;
        long startId = minId;
        while (startId <= maxId) {
            long endId = Math.min(startId + shardSize, maxId + 1);

            // 执行单个分片核对
            ShardReconciliationResult shardResult = executeSingleShard(
                    sourceDs, targetDs, config,
                    startId, endId, job.getJobId(), processed, totalShards);

            mergedResult.missingKeys.addAll(shardResult.missingKeys);
            mergedResult.extraKeys.addAll(shardResult.extraKeys);
            mergedResult.contentDiffKeys.addAll(shardResult.contentDiffKeys);

            processed++;
            job.setProcessedCount(processed);
            job.setProgressPercent((int) (processed * 100 / totalShards));
            jobRepo.save(job);
            log.info("分片 {}/{} 完成，耗时: {}ms", processed, totalShards, shardResult.elapsedMs);

            startId = endId;
        }

        // 保存最终结果
        saveReconciliationResult(job, mergedResult, config);
        log.info("ID 分片核对完成，总差异数: {}", mergedResult.missingKeys.size() + mergedResult.extraKeys.size() + mergedResult.contentDiffKeys.size());
    }

    private ShardReconciliationResult executeSingleShard(
            DataSourceMetaEntity sourceDs,
            DataSourceMetaEntity targetDs,
            ReconciliationConfigEntity config,
            long startId, long endId,
            String jobId, long shardIndex, long totalShards) throws Exception {

        long startTime = System.currentTimeMillis();

        // 构建带 ID 范围的 Fetcher
        String idCondition = config.getIncrementColumn() + " >= " + startId + " AND " +
                config.getIncrementColumn() + " < " + endId;
        String fullCondition = idCondition;
        if (config.getExtCondition() != null && !config.getExtCondition().isEmpty()) {
            fullCondition = "(" + idCondition + ") AND (" + config.getExtCondition() + ")";
        }

        JdbcKeyFetcher sourceFetcher = new JdbcKeyFetcher(
                sourceDs, config.getSourceTable(), config.getPrimaryKey(),
                config.getIncrementColumn(), config.getIncrementType(), fullCondition);
        JdbcKeyFetcher targetFetcher = new JdbcKeyFetcher(
                targetDs, config.getTargetTable(), config.getPrimaryKey(),
                config.getIncrementColumn(), config.getIncrementType(), fullCondition);

        try {
            Set<KeyChecksum> sourceChecksums = sourceFetcher.fetchKeysWithChecksum(
                    null, null, config.getCompareColumns(), null);
            Set<KeyChecksum> targetChecksums = targetFetcher.fetchKeysWithChecksum(
                    null, null, config.getCompareColumns(), null);

            ReconciliationResult diff = computeDiff(sourceChecksums, targetChecksums);

            ShardReconciliationResult result = new ShardReconciliationResult();
            result.missingKeys = diff.missingKeys;
            result.extraKeys = diff.extraKeys;
            result.contentDiffKeys = diff.contentDiffKeys;
            result.elapsedMs = System.currentTimeMillis() - startTime;
            result.shardIndex = shardIndex + 1;
            result.totalShards = totalShards;
            return result;

        } finally {
            sourceFetcher.close();
            targetFetcher.close();
        }
    }

    // ========== ID 分片内部类 ==========

    private static class ShardReconciliationResult {
        Set<String> missingKeys = new HashSet<>();
        Set<String> extraKeys = new HashSet<>();
        Set<String> contentDiffKeys = new HashSet<>();
        long elapsedMs;
        long shardIndex;
        long totalShards;
    }
}