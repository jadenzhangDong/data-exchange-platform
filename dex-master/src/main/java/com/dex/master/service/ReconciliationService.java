package com.dex.master.service;

import com.dex.common.alert.AlertClient;
import com.dex.common.model.*;  // 使用 model 包
import com.dex.common.model.entity.DataSourceMetaEntity;
import com.dex.common.model.entity.ReconciliationConfigEntity;
import com.dex.common.model.entity.ReconciliationDiffEntity;
import com.dex.common.model.entity.ReconciliationJobEntity;
import com.dex.common.repository.*;
import com.dex.common.util.JsonUtil;
import com.dex.master.service.fetcher.JdbcKeyFetcher;
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

        // 检查数据源类型
        DataSourceMetaEntity sourceDs = dsRepo.findById(config.getSourceDataSourceId()).orElse(null);
        if (sourceDs == null) {
            throw new IllegalArgumentException("源数据源不存在");
        }

        // 只有 JDBC 类型才支持分段校验和，其他类型降级到普通核对
        if (!"jdbc".equals(sourceDs.getType()) && !"mysql".equals(sourceDs.getType())) {
            log.info("数据源类型 {} 不支持分段校验和，降级到普通核对", sourceDs.getType());
            executeSimpleReconciliation(config);
            return;
        }

        // 分段校验和核对
        executeChecksumReconciliation(config);
    }

    // ========== 分段校验和核对 ==========

    private void executeChecksumReconciliation(ReconciliationConfigEntity config) throws Exception {
        log.info("开始分段校验和核对: configId={}", config.getId());

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

            String compareColumns = config.getCompareColumns();
            int segmentSize = config.getSegmentSize() != null ? config.getSegmentSize() : 10000;

            // ✅ 修复3：确保 segmentSize 有默认值
            if (segmentSize <= 0) segmentSize = 10000;

            Map<Integer, String> sourceSegments;
            Map<Integer, String> targetSegments;

            try (JdbcKeyFetcher sourceFetcher = new JdbcKeyFetcher(
                    sourceDs, config.getSourceTable(), config.getPrimaryKey(),
                    config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition());
                 JdbcKeyFetcher targetFetcher = new JdbcKeyFetcher(
                         targetDs, config.getTargetTable(), config.getPrimaryKey(),
                         config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition())) {

                sourceSegments = sourceFetcher.fetchSegmentChecksums(windowStart, windowEnd, compareColumns, segmentSize);
                targetSegments = targetFetcher.fetchSegmentChecksums(windowStart, windowEnd, compareColumns, segmentSize);
            }

            // 找出不一致段
            Set<Integer> diffSegments = new HashSet<>();
            for (Map.Entry<Integer, String> entry : sourceSegments.entrySet()) {
                int segmentId = entry.getKey();
                String sourceChecksum = entry.getValue();
                String targetChecksum = targetSegments.get(segmentId);
                if (targetChecksum == null || !sourceChecksum.equals(targetChecksum)) {
                    diffSegments.add(segmentId);
                }
            }
            for (int segmentId : targetSegments.keySet()) {
                if (!sourceSegments.containsKey(segmentId)) {
                    diffSegments.add(segmentId);
                }
            }

            log.info("校验和预检完成，总段数: {}, 不一致段数: {}", sourceSegments.size(), diffSegments.size());

            Set<String> missingKeys = new HashSet<>();
            Set<String> extraKeys = new HashSet<>();
            Set<String> contentDiffKeys = new HashSet<>();

            if (!diffSegments.isEmpty()) {
                try (JdbcKeyFetcher sourceFetcher = new JdbcKeyFetcher(
                        sourceDs, config.getSourceTable(), config.getPrimaryKey(),
                        config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition());
                     JdbcKeyFetcher targetFetcher = new JdbcKeyFetcher(
                             targetDs, config.getTargetTable(), config.getPrimaryKey(),
                             config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition())) {

                    for (int segmentId : diffSegments) {
                        Set<String> sourceKeys = sourceFetcher.fetchKeysForSegment(windowStart, windowEnd, segmentId, segmentSize, null);
                        Set<String> targetKeys = targetFetcher.fetchKeysForSegment(windowStart, windowEnd, segmentId, segmentSize, null);

                        Set<String> missing = new HashSet<>(sourceKeys);
                        missing.removeAll(targetKeys);
                        missingKeys.addAll(missing);

                        Set<String> extra = new HashSet<>(targetKeys);
                        extra.removeAll(sourceKeys);
                        extraKeys.addAll(extra);
                    }
                }
            }

            // 更新结果
            saveReconciliationResult(job, missingKeys, extraKeys, contentDiffKeys, config);

        } catch (Exception e) {
            log.error("核对执行失败", e);
            job.setStatus("FAILED");
            job.setErrorMsg(e.getMessage());
            job.setEndTime(new Date());
            throw e;
        } finally {
            jobRepo.save(job);
        }
    }

    // ========== 普通核对（降级方案） ==========

    private void executeSimpleReconciliation(ReconciliationConfigEntity config) throws Exception {
        log.info("执行普通核对（降级）: configId={}", config.getId());

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

            String compareColumns = config.getCompareColumns();

            Set<KeyChecksum> sourceChecksums = fetchDataWithChecksum(
                    config, sourceDs, config.getSourceTable(),
                    config.getSourceParams(), windowStart, windowEnd, compareColumns, null);
            Set<KeyChecksum> targetChecksums = fetchDataWithChecksum(
                    config, targetDs, config.getTargetTable(),
                    config.getTargetParams(), windowStart, windowEnd, compareColumns, null);

            Map<String, KeyChecksum> sourceMap = sourceChecksums.stream()
                    .collect(Collectors.toMap(KeyChecksum::getKey, Function.identity(), (a, b) -> a));
            Map<String, KeyChecksum> targetMap = targetChecksums.stream()
                    .collect(Collectors.toMap(KeyChecksum::getKey, Function.identity(), (a, b) -> a));

            Set<String> missingKeys = new HashSet<>(sourceMap.keySet());
            missingKeys.removeAll(targetMap.keySet());

            Set<String> extraKeys = new HashSet<>(targetMap.keySet());
            extraKeys.removeAll(sourceMap.keySet());

            Set<String> contentDiffKeys = new HashSet<>();
            if (compareColumns != null && !compareColumns.trim().isEmpty()) {
                for (String key : sourceMap.keySet()) {
                    if (targetMap.containsKey(key)) {
                        String srcChecksum = sourceMap.get(key).getChecksum();
                        String tgtChecksum = targetMap.get(key).getChecksum();
                        if (!Objects.equals(srcChecksum, tgtChecksum)) {
                            contentDiffKeys.add(key);
                        }
                    }
                }
            }

            saveReconciliationResult(job, missingKeys, extraKeys, contentDiffKeys, config);

        } catch (Exception e) {
            log.error("普通核对执行失败", e);
            job.setStatus("FAILED");
            job.setErrorMsg(e.getMessage());
            job.setEndTime(new Date());
            throw e;
        } finally {
            jobRepo.save(job);
        }
    }

    // ========== 辅助方法 ==========

    private Set<KeyChecksum> fetchDataWithChecksum(ReconciliationConfigEntity config,
                                                   DataSourceMetaEntity ds,
                                                   String table,
                                                   String paramsJson,
                                                   Date windowStart,
                                                   Date windowEnd,
                                                   String compareColumns,
                                                   Consumer<Integer> progressCallback) throws Exception {
        if ("kafka".equals(ds.getType())) {
            // Kafka 数据源暂不支持校验和（可扩展 KafkaKeyFetcher）
            return fetchKeysFromKafka(ds, table, paramsJson, windowStart, windowEnd);
        } else {
            try (JdbcKeyFetcher jf = new JdbcKeyFetcher(
                    ds, table, config.getPrimaryKey(),
                    config.getIncrementColumn(), config.getIncrementType(), config.getExtCondition())) {
                return jf.fetchKeysWithChecksum(windowStart, windowEnd, compareColumns, progressCallback);
            }
        }
    }

    private Set<KeyChecksum> fetchKeysFromKafka(DataSourceMetaEntity ds, String table, String paramsJson,
                                                Date windowStart, Date windowEnd) throws Exception {
        // 简化实现：只返回主键，不计算校验和
        Set<KeyChecksum> result = new HashSet<>();
        Map<String, Object> dsConfig = JsonUtil.fromJson(ds.getConfig(), Map.class);
        // 实际应实现 KafkaKeyFetcher
        log.warn("Kafka 数据源暂不支持校验和计算");
        return result;
    }

    private void saveReconciliationResult(ReconciliationJobEntity job,
                                          Set<String> missingKeys,
                                          Set<String> extraKeys,
                                          Set<String> contentDiffKeys,
                                          ReconciliationConfigEntity config) {
        int diffCount = missingKeys.size() + extraKeys.size() + contentDiffKeys.size();
        job.setSourceCount((long) (missingKeys.size() + contentDiffKeys.size()));
        job.setTargetCount((long) (extraKeys.size() + contentDiffKeys.size()));
        job.setSourceMissingCount((long) missingKeys.size());
        job.setTargetExtraCount((long) extraKeys.size());
        job.setDiffCount((long) diffCount);
        job.setStatus("SUCCESS");
        job.setEndTime(new Date());
        job.setProgressPercent(100);
        jobRepo.save(job);

        int threshold = config.getDiffThreshold() != null ? config.getDiffThreshold() : 1000;
        if (diffCount > threshold) {
            AlertClient.sendAlert("核对差异超阈值",
                    String.format("配置: %s, 差异数: %d", config.getName(), diffCount));
        }

        int count = 0;
        for (String pk : missingKeys) {
            if (count++ >= threshold) break;
            saveDiff(job.getJobId(), config.getId(), "MISSING", pk);
        }
        for (String pk : extraKeys) {
            if (count++ >= threshold) break;
            saveDiff(job.getJobId(), config.getId(), "EXTRA", pk);
        }
        for (String pk : contentDiffKeys) {
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

    private Date calculateWindowStart(Date now, ReconciliationConfigEntity config) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);

        // 先扣延迟
        if (config.getDelayMinutes() != null && config.getDelayMinutes() > 0) {
            cal.add(Calendar.MINUTE, -config.getDelayMinutes());
            log.info("延迟补偿 {} 分钟，窗口后移", config.getDelayMinutes());
        }

        // 再扣窗口
        String unit = config.getWindowUnit() != null ? config.getWindowUnit() : "HOUR";
        int size = config.getWindowSize() != null ? config.getWindowSize() : 1;
        switch (unit.toUpperCase()) {
            case "HOUR": cal.add(Calendar.HOUR, -size); break;
            case "DAY": cal.add(Calendar.DAY_OF_MONTH, -size); break;
            default: cal.add(Calendar.HOUR, -size); break;
        }
        return cal.getTime();
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

    // ========== 补偿 ==========

    @Transactional
    public void compensate(String configId, List<String> pkList) throws Exception {
        // ... 原有补偿逻辑 ...
    }
}