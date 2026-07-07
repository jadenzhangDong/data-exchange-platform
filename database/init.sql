-- ============================================================
-- 数据库: dex_platform
-- 说明: 数据交换平台完整建表脚本（含 Quartz 集群表）
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS `dex_platform`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `dex_platform`;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 数据源元数据
-- ============================================================
DROP TABLE IF EXISTS `data_source_meta`;
CREATE TABLE `data_source_meta` (
                                    `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
                                    `name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
                                    `type` VARCHAR(50) NOT NULL COMMENT '类型: mysql, postgresql, kafka, file, mock',
                                    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
                                    `config` JSON NOT NULL COMMENT '连接配置JSON',
                                    `status` VARCHAR(20) DEFAULT 'ONLINE' COMMENT '状态: ONLINE, OFFLINE',
                                    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_type` (`type`),
                                    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源元数据';

-- ============================================================
-- 2. 任务定义
-- ============================================================
DROP TABLE IF EXISTS `task_definition`;
CREATE TABLE `task_definition` (
                                   `task_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
                                   `task_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
                                   `mode` VARCHAR(20) NOT NULL COMMENT '模式: BATCH, STREAMING',
                                   `config_json` JSON NOT NULL COMMENT '完整的TaskConfig JSON',
                                   `cron_expression` VARCHAR(50) DEFAULT NULL COMMENT 'Cron表达式（BATCH定时模式使用）',
                                   `status` VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态: ENABLED, DISABLED',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`task_id`),
                                   KEY `idx_mode` (`mode`),
                                   KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务定义';

-- ============================================================
-- 3. 任务实例
-- ============================================================
DROP TABLE IF EXISTS `task_instance`;
CREATE TABLE `task_instance` (
                                 `instance_id` VARCHAR(32) NOT NULL COMMENT '实例ID',
                                 `task_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
                                 `parent_instance_id` VARCHAR(32) DEFAULT NULL COMMENT '父实例ID（分片任务）',
                                 `sub_task_index` INT DEFAULT 0 COMMENT '子任务索引',
                                 `state` VARCHAR(20) NOT NULL COMMENT '状态: PENDING, RUNNING, SUCCESS, FAILED, STOPPED',
                                 `assigned_worker_id` VARCHAR(100) DEFAULT NULL COMMENT '执行Worker ID',
                                 `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
                                 `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
                                 `processed_records` BIGINT DEFAULT 0 COMMENT '处理记录数',
                                 `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`instance_id`),
                                 KEY `idx_task_id` (`task_id`),
                                 KEY `idx_state` (`state`),
                                 KEY `idx_parent_instance` (`parent_instance_id`),
                                 CONSTRAINT `fk_task_instance_task` FOREIGN KEY (`task_id`) REFERENCES `task_definition`(`task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务实例';

-- ============================================================
-- 4. 任务日志（预留）
-- ============================================================
DROP TABLE IF EXISTS `task_log`;
CREATE TABLE `task_log` (
                            `id` BIGINT AUTO_INCREMENT COMMENT '自增主键',
                            `instance_id` VARCHAR(32) NOT NULL COMMENT '任务实例ID',
                            `level` VARCHAR(10) NOT NULL COMMENT '日志级别: INFO, WARN, ERROR',
                            `message` TEXT NOT NULL COMMENT '日志内容',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_instance_id` (`instance_id`),
                            CONSTRAINT `fk_task_log_instance` FOREIGN KEY (`instance_id`) REFERENCES `task_instance`(`instance_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务日志';

-- ============================================================
-- 5. 表结构元数据
-- ============================================================
DROP TABLE IF EXISTS `table_meta`;
CREATE TABLE `table_meta` (
                              `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
                              `data_source_id` VARCHAR(32) NOT NULL COMMENT '数据源ID',
                              `schema_name` VARCHAR(100) DEFAULT NULL COMMENT 'Schema名',
                              `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
                              `table_type` VARCHAR(20) DEFAULT NULL COMMENT '表类型: TABLE, VIEW',
                              `columns` JSON NOT NULL COMMENT '字段列表JSON',
                              `row_count` BIGINT DEFAULT 0 COMMENT '行数估算',
                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_datasource_table` (`data_source_id`, `table_name`),
                              CONSTRAINT `fk_table_meta_datasource` FOREIGN KEY (`data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表结构元数据';

-- ============================================================
-- 6. 字段映射规则
-- ============================================================
DROP TABLE IF EXISTS `field_mapping_rule`;
CREATE TABLE `field_mapping_rule` (
                                      `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
                                      `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
                                      `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
                                      `source_table_id` VARCHAR(32) DEFAULT NULL COMMENT '源表ID',
                                      `target_table_id` VARCHAR(32) DEFAULT NULL COMMENT '目标表ID',
                                      `mapping_json` JSON NOT NULL COMMENT '映射关系JSON',
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_source_table` (`source_table_id`),
                                      KEY `idx_target_table` (`target_table_id`),
                                      CONSTRAINT `fk_mapping_source` FOREIGN KEY (`source_table_id`) REFERENCES `table_meta`(`id`) ON DELETE SET NULL,
                                      CONSTRAINT `fk_mapping_target` FOREIGN KEY (`target_table_id`) REFERENCES `table_meta`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射规则';

-- ============================================================
-- 7. 任务模板
-- ============================================================
DROP TABLE IF EXISTS `task_template`;
CREATE TABLE `task_template` (
                                 `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
                                 `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
                                 `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
                                 `category` VARCHAR(50) DEFAULT NULL COMMENT '分类',
                                 `mode` VARCHAR(20) NOT NULL COMMENT '任务模式: BATCH, STREAMING',
                                 `source_template` JSON NOT NULL COMMENT 'Source插件配置模板',
                                 `sink_template` JSON NOT NULL COMMENT 'Sink插件配置模板',
                                 `transform_templates` JSON DEFAULT NULL COMMENT 'Transform插件配置模板列表',
                                 `default_batch_size` INT DEFAULT 1000 COMMENT '默认批次大小',
                                 `default_cron` VARCHAR(50) DEFAULT NULL COMMENT '默认Cron',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_mode` (`mode`),
                                 KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务模板';

-- ============================================================
-- 8. 水位线表
-- ============================================================
DROP TABLE IF EXISTS `task_watermark`;
CREATE TABLE `task_watermark` (
                                  `id` VARCHAR(32) NOT NULL COMMENT '主键',
                                  `task_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
                                  `source_table` VARCHAR(100) NOT NULL COMMENT '源表名',
                                  `increment_column` VARCHAR(50) NOT NULL COMMENT '增量字段名',
                                  `watermark_value` BIGINT NOT NULL COMMENT '水位线值（ID或时间戳毫秒）',
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_task_table_column` (`task_id`, `source_table`, `increment_column`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务水位线';

-- ============================================================
-- 9. Worker 状态持久化
-- ============================================================
DROP TABLE IF EXISTS `worker_state`;
CREATE TABLE `worker_state` (
                                `worker_id` VARCHAR(100) PRIMARY KEY COMMENT 'Worker ID',
                                `host` VARCHAR(50) NOT NULL COMMENT '主机地址',
                                `port` INT NOT NULL COMMENT 'HTTP端口',
                                `grpc_port` INT DEFAULT 19090 COMMENT 'gRPC端口',
                                `status` VARCHAR(20) DEFAULT 'ONLINE' COMMENT '状态: ONLINE, DRAINING, DISABLED, OFFLINE',
                                `weight` INT DEFAULT 10 COMMENT '权重(1-100)',
                                `tags` VARCHAR(200) DEFAULT '' COMMENT '标签，逗号分隔',
                                `last_heartbeat` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后心跳时间',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Worker状态持久化';

-- ============================================================
-- 10. 核对配置
-- ============================================================
DROP TABLE IF EXISTS `reconciliation_config`;
CREATE TABLE `reconciliation_config` (
                                         `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
                                         `name` VARCHAR(100) NOT NULL COMMENT '核对任务名称',
                                         `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
                                         `source_data_source_id` VARCHAR(32) NOT NULL COMMENT '源数据源ID',
                                         `target_data_source_id` VARCHAR(32) NOT NULL COMMENT '目标数据源ID',
                                         `source_table` VARCHAR(100) NOT NULL COMMENT '源表名',
                                         `target_table` VARCHAR(100) NOT NULL COMMENT '目标表名',
                                         `primary_key` VARCHAR(100) NOT NULL COMMENT '主键字段',
                                         `increment_column` VARCHAR(100) DEFAULT NULL COMMENT '增量字段',
                                         `increment_type` VARCHAR(20) DEFAULT 'TIMESTAMP' COMMENT '增量类型: TIMESTAMP, NUMBER',
                                         `shard_size` BIGINT DEFAULT 1000000 COMMENT 'ID分片大小（NUMBER类型）',
                                         `check_strategy` VARCHAR(20) DEFAULT 'COUNT_CHECK' COMMENT '核对策略: COUNT_CHECK, FULL_CHECK, SAMPLE_CHECK',
                                         `window_unit` VARCHAR(10) DEFAULT 'HOUR' COMMENT '窗口单位: HOUR, DAY',
                                         `window_size` INT DEFAULT 1 COMMENT '窗口大小',
                                         `delay_minutes` INT DEFAULT 5 COMMENT '延迟补偿（分钟）',
                                         `cron_expression` VARCHAR(50) NOT NULL COMMENT 'Cron表达式',
                                         `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
                                         `ext_condition` VARCHAR(500) DEFAULT NULL COMMENT '额外过滤条件',
                                         `compare_columns` TEXT DEFAULT NULL COMMENT '内容比对的列（逗号分隔）',
                                         `sample_rate` DECIMAL(5,4) DEFAULT 1.0000 COMMENT '采样率(0~1)',
                                         `extra_action` VARCHAR(20) DEFAULT 'IGNORE' COMMENT 'EXTRA数据处理: IGNORE, DELETE, REPORT',
                                         `source_params` JSON DEFAULT NULL COMMENT '源端额外参数（如Kafka topic/keyField）',
                                         `target_params` JSON DEFAULT NULL COMMENT '目标端额外参数',
                                         `diff_threshold` INT DEFAULT 1000 COMMENT '差异告警阈值',
                                         `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_source_ds` (`source_data_source_id`),
                                         KEY `idx_target_ds` (`target_data_source_id`),
                                         KEY `idx_enabled` (`enabled`),
                                         CONSTRAINT `fk_reco_config_source` FOREIGN KEY (`source_data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE,
                                         CONSTRAINT `fk_reco_config_target` FOREIGN KEY (`target_data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核对配置';

-- ============================================================
-- 11. 核对实例
-- ============================================================
DROP TABLE IF EXISTS `reconciliation_job`;
CREATE TABLE `reconciliation_job` (
                                      `job_id` VARCHAR(32) NOT NULL COMMENT '实例ID',
                                      `config_id` VARCHAR(32) NOT NULL COMMENT '核对配置ID',
                                      `window_start` DATETIME NOT NULL COMMENT '窗口开始时间',
                                      `window_end` DATETIME NOT NULL COMMENT '窗口结束时间',
                                      `source_count` BIGINT DEFAULT 0 COMMENT '源记录数',
                                      `target_count` BIGINT DEFAULT 0 COMMENT '目标记录数',
                                      `diff_count` BIGINT DEFAULT 0 COMMENT '差异总数',
                                      `source_missing_count` BIGINT DEFAULT 0 COMMENT '目标缺失数',
                                      `target_extra_count` BIGINT DEFAULT 0 COMMENT '目标多余数',
                                      `processed_count` BIGINT DEFAULT 0 COMMENT '已处理记录数（进度）',
                                      `total_count` BIGINT DEFAULT 0 COMMENT '总记录数（估算）',
                                      `progress_percent` INT DEFAULT 0 COMMENT '进度百分比',
                                      `source_checksum_count` BIGINT DEFAULT 0 COMMENT '源端校验和数',
                                      `target_checksum_count` BIGINT DEFAULT 0 COMMENT '目标端校验和数',
                                      `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, RUNNING, SUCCESS, FAILED, CANCELLED',
                                      `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
                                      `cancelled` TINYINT(1) DEFAULT 0 COMMENT '是否取消',
                                      `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
                                      `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      PRIMARY KEY (`job_id`),
                                      KEY `idx_config_id` (`config_id`),
                                      KEY `idx_status` (`status`),
                                      KEY `idx_create_time` (`create_time`),
                                      CONSTRAINT `fk_reco_job_config` FOREIGN KEY (`config_id`) REFERENCES `reconciliation_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核对实例';

-- ============================================================
-- 12. 核对差异明细
-- ============================================================
DROP TABLE IF EXISTS `reconciliation_diff`;
CREATE TABLE `reconciliation_diff` (
                                       `id` BIGINT AUTO_INCREMENT COMMENT '自增主键',
                                       `job_id` VARCHAR(32) NOT NULL COMMENT '核对实例ID',
                                       `config_id` VARCHAR(32) NOT NULL COMMENT '核对配置ID',
                                       `diff_type` VARCHAR(20) NOT NULL COMMENT '类型: MISSING, EXTRA, CONTENT_DIFF',
                                       `pk_value` VARCHAR(200) NOT NULL COMMENT '主键值',
                                       `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, FIXED, IGNORED',
                                       `fixed_time` DATETIME DEFAULT NULL COMMENT '修复时间',
                                       `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_job_id` (`job_id`),
                                       KEY `idx_config_id` (`config_id`),
                                       KEY `idx_status` (`status`),
                                       KEY `idx_pk_value` (`pk_value`),
                                       CONSTRAINT `fk_reco_diff_job` FOREIGN KEY (`job_id`) REFERENCES `reconciliation_job`(`job_id`) ON DELETE CASCADE,
                                       CONSTRAINT `fk_reco_diff_config` FOREIGN KEY (`config_id`) REFERENCES `reconciliation_config`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核对差异明细';

-- ============================================================
-- 13. 补偿记录（预留）
-- ============================================================
DROP TABLE IF EXISTS `reconciliation_compensation`;
CREATE TABLE `reconciliation_compensation` (
                                               `id` VARCHAR(32) NOT NULL COMMENT '补偿ID',
                                               `job_id` VARCHAR(32) NOT NULL COMMENT '核对实例ID',
                                               `diff_id` BIGINT NOT NULL COMMENT '差异明细ID',
                                               `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
                                               `compensate_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '补偿时间',
                                               `before_value` JSON DEFAULT NULL COMMENT '补偿前数据快照',
                                               `after_value` JSON DEFAULT NULL COMMENT '补偿后数据快照',
                                               PRIMARY KEY (`id`),
                                               KEY `idx_job_id` (`job_id`),
                                               KEY `idx_diff_id` (`diff_id`),
                                               CONSTRAINT `fk_compensation_diff` FOREIGN KEY (`diff_id`) REFERENCES `reconciliation_diff`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补偿记录';

-- ============================================================
-- 14. Quartz 集群表
-- ============================================================
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

CREATE TABLE QRTZ_JOB_DETAILS (
                                  SCHED_NAME VARCHAR(120) NOT NULL,
                                  JOB_NAME VARCHAR(200) NOT NULL,
                                  JOB_GROUP VARCHAR(200) NOT NULL,
                                  DESCRIPTION VARCHAR(250) NULL,
                                  JOB_CLASS_NAME VARCHAR(250) NOT NULL,
                                  IS_DURABLE VARCHAR(1) NOT NULL,
                                  IS_NONCONCURRENT VARCHAR(1) NOT NULL,
                                  IS_UPDATE_DATA VARCHAR(1) NOT NULL,
                                  REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
                                  JOB_DATA BLOB NULL,
                                  PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
);

CREATE TABLE QRTZ_TRIGGERS (
                               SCHED_NAME VARCHAR(120) NOT NULL,
                               TRIGGER_NAME VARCHAR(200) NOT NULL,
                               TRIGGER_GROUP VARCHAR(200) NOT NULL,
                               JOB_NAME VARCHAR(200) NOT NULL,
                               JOB_GROUP VARCHAR(200) NOT NULL,
                               DESCRIPTION VARCHAR(250) NULL,
                               NEXT_FIRE_TIME BIGINT(13) NULL,
                               PREV_FIRE_TIME BIGINT(13) NULL,
                               PRIORITY INTEGER NULL,
                               TRIGGER_STATE VARCHAR(16) NOT NULL,
                               TRIGGER_TYPE VARCHAR(8) NOT NULL,
                               START_TIME BIGINT(13) NOT NULL,
                               END_TIME BIGINT(13) NULL,
                               CALENDAR_NAME VARCHAR(200) NULL,
                               MISFIRE_INSTR SMALLINT(2) NULL,
                               JOB_DATA BLOB NULL,
                               PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_CRON_TRIGGERS (
                                    SCHED_NAME VARCHAR(120) NOT NULL,
                                    TRIGGER_NAME VARCHAR(200) NOT NULL,
                                    TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                    CRON_EXPRESSION VARCHAR(120) NOT NULL,
                                    TIME_ZONE_ID VARCHAR(80) NULL,
                                    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
                                      SCHED_NAME VARCHAR(120) NOT NULL,
                                      TRIGGER_NAME VARCHAR(200) NOT NULL,
                                      TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                      REPEAT_COUNT BIGINT(7) NOT NULL,
                                      REPEAT_INTERVAL BIGINT(12) NOT NULL,
                                      TIMES_TRIGGERED BIGINT(10) NOT NULL,
                                      PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_SIMPROP_TRIGGERS (
                                       SCHED_NAME VARCHAR(120) NOT NULL,
                                       TRIGGER_NAME VARCHAR(200) NOT NULL,
                                       TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                       STR_PROP_1 VARCHAR(512) NULL,
                                       STR_PROP_2 VARCHAR(512) NULL,
                                       STR_PROP_3 VARCHAR(512) NULL,
                                       INT_PROP_1 INT NULL,
                                       INT_PROP_2 INT NULL,
                                       LONG_PROP_1 BIGINT NULL,
                                       LONG_PROP_2 BIGINT NULL,
                                       DEC_PROP_1 NUMERIC(13,4) NULL,
                                       DEC_PROP_2 NUMERIC(13,4) NULL,
                                       BOOL_PROP_1 VARCHAR(1) NULL,
                                       BOOL_PROP_2 VARCHAR(1) NULL,
                                       PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_BLOB_TRIGGERS (
                                    SCHED_NAME VARCHAR(120) NOT NULL,
                                    TRIGGER_NAME VARCHAR(200) NOT NULL,
                                    TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                    BLOB_DATA BLOB NULL,
                                    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_CALENDARS (
                                SCHED_NAME VARCHAR(120) NOT NULL,
                                CALENDAR_NAME VARCHAR(200) NOT NULL,
                                CALENDAR BLOB NOT NULL,
                                PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
);

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
                                          SCHED_NAME VARCHAR(120) NOT NULL,
                                          TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                          PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_FIRED_TRIGGERS (
                                     SCHED_NAME VARCHAR(120) NOT NULL,
                                     ENTRY_ID VARCHAR(95) NOT NULL,
                                     TRIGGER_NAME VARCHAR(200) NOT NULL,
                                     TRIGGER_GROUP VARCHAR(200) NOT NULL,
                                     INSTANCE_NAME VARCHAR(200) NOT NULL,
                                     FIRED_TIME BIGINT(13) NOT NULL,
                                     SCHED_TIME BIGINT(13) NOT NULL,
                                     PRIORITY INTEGER NOT NULL,
                                     STATE VARCHAR(16) NOT NULL,
                                     JOB_NAME VARCHAR(200) NULL,
                                     JOB_GROUP VARCHAR(200) NULL,
                                     IS_NONCONCURRENT VARCHAR(1) NULL,
                                     REQUESTS_RECOVERY VARCHAR(1) NULL,
                                     PRIMARY KEY (SCHED_NAME,ENTRY_ID)
);

CREATE TABLE QRTZ_SCHEDULER_STATE (
                                      SCHED_NAME VARCHAR(120) NOT NULL,
                                      INSTANCE_NAME VARCHAR(200) NOT NULL,
                                      LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
                                      CHECKIN_INTERVAL BIGINT(13) NOT NULL,
                                      PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
);

CREATE TABLE QRTZ_LOCKS (
                            SCHED_NAME VARCHAR(120) NOT NULL,
                            LOCK_NAME VARCHAR(40) NOT NULL,
                            PRIMARY KEY (SCHED_NAME,LOCK_NAME)
);

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME,CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE);

-- ============================================================
-- 完成
-- ============================================================

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ dex_platform 数据库初始化完成（含 Quartz 集群表）' AS Status;
SELECT '📊 共创建 13 张业务表 + 1 张水位线表 + 1 张Worker状态表 + 11 张Quartz表' AS Info;

ALTER TABLE reconciliation_config
    ADD COLUMN segment_size INT DEFAULT 10000 COMMENT '校验和分段大小（行数）';

-- 任务实例表增加字段
ALTER TABLE task_instance
    ADD COLUMN max_retries INT DEFAULT 0 COMMENT '最大重试次数',
    ADD COLUMN retry_count INT DEFAULT 0 COMMENT '当前已重试次数',
    ADD COLUMN last_heartbeat DATETIME DEFAULT NULL COMMENT '最后一次心跳时间',
    ADD COLUMN task_timeout_ms BIGINT DEFAULT 3600000 COMMENT '任务超时时间(毫秒)';

-- 更新已有的 RUNNING 任务的 last_heartbeat 为 start_time
UPDATE task_instance SET last_heartbeat = start_time WHERE state = 'RUNNING' AND last_heartbeat IS NULL;

-- 创建索引
CREATE INDEX idx_state_last_heartbeat ON task_instance (state, last_heartbeat);