package com.dex.master.service;

import com.dex.common.model.entity.*;
import com.dex.common.repository.*;
import com.dex.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;
import java.util.Date;

@Service
public class CompensationService {
    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    @Autowired
    private ReconciliationDiffRepository diffRepo;
    @Autowired
    private ReconciliationCompensationRepository compensationRepo;
    @Autowired
    private ReconciliationConfigRepository configRepo;
    @Autowired
    private DataSourceMetaRepository dsRepo;

    /**
     * 单条补偿
     */
    @Transactional
    public void compensateSingle(Long diffId, String action, String compensatedBy) throws Exception {
        ReconciliationDiffEntity diff = diffRepo.findById(diffId).orElse(null);
        if (diff == null) {
            throw new IllegalArgumentException("差异不存在");
        }
        // 执行补偿操作
        doCompensate(diff, action, compensatedBy);
        // 更新差异状态
        diff.setStatus("FIXED");
        diff.setFixedTime(new Date());
        diffRepo.save(diff);
    }

    /**
     * 批量补偿（按条件）
     */
    @Transactional
    public void compensateBatch(String configId, String diffType, String compensatedBy) throws Exception {
        List<ReconciliationDiffEntity> diffs;
        if (diffType != null && !diffType.isEmpty()) {
            // 按类型
            // 目前 Diff 没有直接按类型查询方法，我们用 JPA 查询
            diffs = diffRepo.findByConfigIdAndStatus(configId, "PENDING"); // 需要扩展
            // 过滤类型
            diffs.removeIf(d -> !diffType.equals(d.getDiffType()));
        } else {
            diffs = diffRepo.findByConfigIdAndStatus(configId, "PENDING");
        }
        if (diffs.isEmpty()) {
            throw new IllegalArgumentException("没有待补偿的差异");
        }
        for (ReconciliationDiffEntity diff : diffs) {
            try {
                doCompensate(diff, "AUTO", compensatedBy);
                diff.setStatus("FIXED");
                diff.setFixedTime(new Date());
                diffRepo.save(diff);
            } catch (Exception e) {
                log.error("补偿失败: diffId={}", diff.getId(), e);
                // 记录失败但继续
            }
        }
    }

    private void doCompensate(ReconciliationDiffEntity diff, String action, String compensatedBy) throws Exception {
        ReconciliationConfigEntity config = configRepo.findById(diff.getConfigId()).orElse(null);
        if (config == null) {
            throw new IllegalArgumentException("核对配置不存在");
        }
        DataSourceMetaEntity sourceDs = dsRepo.findById(config.getSourceDataSourceId()).orElse(null);
        DataSourceMetaEntity targetDs = dsRepo.findById(config.getTargetDataSourceId()).orElse(null);
        if (sourceDs == null || targetDs == null) {
            throw new IllegalArgumentException("数据源不存在");
        }

        Map<String, Object> sourceConfigMap = JsonUtil.fromJson(sourceDs.getConfig(), Map.class);
        Map<String, Object> targetConfigMap = JsonUtil.fromJson(targetDs.getConfig(), Map.class);

        String pkValue = diff.getPkValue();
        String sourceTable = config.getSourceTable();
        String targetTable = config.getTargetTable();
        String pk = config.getPrimaryKey();

        Connection sourceConn = getConnection(sourceConfigMap);
        Connection targetConn = getConnection(targetConfigMap);

        try {
            if ("MISSING".equals(diff.getDiffType())) {
                // 源有，目标无 → INSERT
                // 从源查询数据
                String selectSql = "SELECT * FROM " + sourceTable + " WHERE " + pk + " = ?";
                Map<String, Object> row = querySingleRow(sourceConn, selectSql, pkValue);
                if (row != null) {
                    // 插入目标
                    insertRow(targetConn, targetTable, row);
                    log.info("补偿插入: pk={}", pkValue);
                    // 记录补偿日志
                    saveCompensationLog(diff, "INSERT", "SUCCESS", null, compensatedBy);
                } else {
                    throw new RuntimeException("源表无此主键数据: " + pkValue);
                }
            } else if ("EXTRA".equals(diff.getDiffType())) {
                // 目标有，源无 → DELETE 或 IGNORE
                // 这里默认 IGNORE，也可配置
                log.info("补偿忽略多余数据: pk={}", pkValue);
                saveCompensationLog(diff, "IGNORE", "SUCCESS", null, compensatedBy);
            }
        } catch (Exception e) {
            log.error("补偿执行失败", e);
            saveCompensationLog(diff, "AUTO", "FAILED", e.getMessage(), compensatedBy);
            throw e;
        } finally {
            if (sourceConn != null) sourceConn.close();
            if (targetConn != null) targetConn.close();
        }
    }

    private void insertRow(Connection conn, String table, Map<String, Object> row) throws SQLException {
        // 动态生成 INSERT
        List<String> columns = new ArrayList<>(row.keySet());
        String colStr = String.join(",", columns);
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + colStr + ") VALUES (" + placeholders + ")";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < columns.size(); i++) {
                pstmt.setObject(i + 1, row.get(columns.get(i)));
            }
            pstmt.executeUpdate();
        }
    }

    private Map<String, Object> querySingleRow(Connection conn, String sql, String pkValue) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pkValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    return row;
                }
            }
        }
        return null;
    }

    private void saveCompensationLog(ReconciliationDiffEntity diff, String action, String status, String error, String compensatedBy) {
        ReconciliationCompensationEntity comp = new ReconciliationCompensationEntity();
        comp.setDiffId(diff.getId());
        comp.setJobId(diff.getJobId());
        comp.setConfigId(diff.getConfigId());
        comp.setAction(action);
        comp.setStatus(status);
        comp.setErrorMsg(error);
        comp.setCompensatedBy(compensatedBy != null ? compensatedBy : "system");
        comp.setCreateTime(new Date());
        comp.setUpdateTime(new Date());
        compensationRepo.save(comp);
    }

    private Connection getConnection(Map<String, Object> config) throws SQLException {
        String url = (String) config.get("jdbcUrl");
        if (url == null) {
            String host = (String) config.get("host");
            Integer port = (Integer) config.get("port");
            String database = (String) config.get("database");
            url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    host, port != null ? port : 3306, database);
        }
        String user = (String) config.get("user");
        String password = (String) config.get("password");
        return DriverManager.getConnection(url, user, password);
    }
}