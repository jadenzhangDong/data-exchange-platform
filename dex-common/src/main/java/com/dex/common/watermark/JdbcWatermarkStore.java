package com.dex.common.watermark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class JdbcWatermarkStore implements WatermarkStore {
    private static final Logger log = LoggerFactory.getLogger(JdbcWatermarkStore.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcWatermarkStore(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Long getWatermark(String taskId, String table, String incrementColumn) {
        String sql = "SELECT watermark_value FROM task_watermark WHERE task_id = ? AND source_table = ? AND increment_column = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, taskId, table, incrementColumn);
        } catch (Exception e) {
            log.debug("水位线不存在: taskId={}, table={}, col={}", taskId, table, incrementColumn);
            return null;
        }
    }

    @Override
    public void updateWatermark(String taskId, String table, String incrementColumn, Long watermark) {
        String selectSql = "SELECT COUNT(*) FROM task_watermark WHERE task_id = ? AND source_table = ? AND increment_column = ?";
        Integer count = jdbcTemplate.queryForObject(selectSql, Integer.class, taskId, table, incrementColumn);

        if (count != null && count > 0) {
            String updateSql = "UPDATE task_watermark SET watermark_value = ?, update_time = ? WHERE task_id = ? AND source_table = ? AND increment_column = ?";
            jdbcTemplate.update(updateSql, watermark, new Timestamp(System.currentTimeMillis()), taskId, table, incrementColumn);
        } else {
            String insertSql = "INSERT INTO task_watermark (id, task_id, source_table, increment_column, watermark_value) VALUES (?, ?, ?, ?, ?)";
            String id = UUID.randomUUID().toString().replace("-", "");
            jdbcTemplate.update(insertSql, id, taskId, table, incrementColumn, watermark);
        }
        log.debug("水位线更新成功: taskId={}, table={}, col={}, value={}", taskId, table, incrementColumn, watermark);
    }

    @Override
    public void removeWatermark(String taskId) {
        String sql = "DELETE FROM task_watermark WHERE task_id = ?";
        jdbcTemplate.update(sql, taskId);
        log.info("删除水位线: taskId={}", taskId);
    }
}