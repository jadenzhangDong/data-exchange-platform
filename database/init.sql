CREATE DATABASE IF NOT EXISTS dex_platform DEFAULT CHARSET utf8mb4;

USE dex_platform;

-- 数据源元数据表
CREATE TABLE `data_source_meta` (
                                    `id` VARCHAR(32) PRIMARY KEY,
                                    `name` VARCHAR(100) NOT NULL,
                                    `type` VARCHAR(50) NOT NULL,
                                    `description` VARCHAR(255),
                                    `config` JSON NOT NULL,
                                    `status` VARCHAR(20) DEFAULT 'ONLINE',
                                    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 任务定义表
CREATE TABLE `task_definition` (
                                   `task_id` VARCHAR(32) PRIMARY KEY,
                                   `task_name` VARCHAR(100) NOT NULL,
                                   `mode` VARCHAR(20) NOT NULL,  -- ONESHOT, BATCH, SCHEDULED, STREAMING
                                   `config_json` JSON NOT NULL,
                                   `cron_expression` VARCHAR(50),
                                   `status` VARCHAR(20) DEFAULT 'ENABLED', -- ENABLED, DISABLED
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 任务实例表
CREATE TABLE `task_instance` (
                                 `instance_id` VARCHAR(32) PRIMARY KEY,
                                 `task_id` VARCHAR(32) NOT NULL,
                                 `state` VARCHAR(20) NOT NULL, -- PENDING, RUNNING, SUCCESS, FAILED, STOPPED
                                 `assigned_worker_id` VARCHAR(100),
                                 `start_time` DATETIME,
                                 `end_time` DATETIME,
                                 `processed_records` BIGINT DEFAULT 0,
                                 `error_message` TEXT,
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 INDEX idx_task_id (`task_id`),
                                 INDEX idx_state (`state`)
);

-- 任务执行日志表（可选）
CREATE TABLE `task_log` (
                            `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                            `instance_id` VARCHAR(32) NOT NULL,
                            `level` VARCHAR(10) NOT NULL, -- INFO, WARN, ERROR
                            `message` TEXT,
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_instance (`instance_id`)
);

-- 表结构元数据（存储数据源下的所有表和字段）
CREATE TABLE `table_meta` (
                              `id` VARCHAR(32) PRIMARY KEY,
                              `data_source_id` VARCHAR(32) NOT NULL,
                              `schema_name` VARCHAR(100),
                              `table_name` VARCHAR(100) NOT NULL,
                              `table_type` VARCHAR(20),
                              `columns` JSON NOT NULL,  -- 字段列表 JSON 数组
                              `row_count` BIGINT DEFAULT 0,
                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                              `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              FOREIGN KEY (`data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE,
                              UNIQUE KEY `uk_datasource_table` (`data_source_id`, `table_name`)
);

-- 字段映射规则表（用户自定义的字段映射关系，可复用）
CREATE TABLE `field_mapping_rule` (
                                      `id` VARCHAR(32) PRIMARY KEY,
                                      `name` VARCHAR(100) NOT NULL,
                                      `description` VARCHAR(255),
                                      `source_table_id` VARCHAR(32),
                                      `target_table_id` VARCHAR(32),
                                      `mapping_json` JSON NOT NULL,  -- 映射关系 JSON
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      FOREIGN KEY (`source_table_id`) REFERENCES `table_meta`(`id`) ON DELETE SET NULL,
                                      FOREIGN KEY (`target_table_id`) REFERENCES `table_meta`(`id`) ON DELETE SET NULL
);

-- 任务模板表
CREATE TABLE `task_template` (
                                 `id` VARCHAR(32) PRIMARY KEY,
                                 `name` VARCHAR(100) NOT NULL,
                                 `description` VARCHAR(255),
                                 `category` VARCHAR(50),  -- 如 "全量同步", "增量同步", "CDC" 等
                                 `mode` VARCHAR(20) NOT NULL,  -- ONESHOT, BATCH, SCHEDULED, STREAMING
                                 `source_template` JSON NOT NULL,  -- Source 插件配置模板
                                 `sink_template` JSON NOT NULL,    -- Sink 插件配置模板
                                 `transform_templates` JSON,       -- Transform 插件配置模板列表
                                 `default_batch_size` INT DEFAULT 1000,
                                 `default_cron` VARCHAR(50),
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 核对配置表
CREATE TABLE `reconciliation_config` (
                                         `id` VARCHAR(32) PRIMARY KEY,
                                         `name` VARCHAR(100) NOT NULL COMMENT '核对任务名称',
                                         `description` VARCHAR(255) COMMENT '描述',
                                         `source_data_source_id` VARCHAR(32) NOT NULL COMMENT '源数据源ID',
                                         `target_data_source_id` VARCHAR(32) NOT NULL COMMENT '目标数据源ID',
                                         `source_table` VARCHAR(100) NOT NULL COMMENT '源表名',
                                         `target_table` VARCHAR(100) NOT NULL COMMENT '目标表名',
                                         `primary_key` VARCHAR(100) NOT NULL COMMENT '主键字段',
                                         `increment_column` VARCHAR(100) DEFAULT NULL COMMENT '增量字段（用于分片）',
                                         `check_strategy` VARCHAR(20) DEFAULT 'COUNT_CHECK' COMMENT '核对策略: COUNT_CHECK, FULL_CHECK, SAMPLE_CHECK',
                                         `window_unit` VARCHAR(10) DEFAULT 'HOUR' COMMENT '窗口单位: HOUR, DAY',
                                         `window_size` INT DEFAULT 1 COMMENT '窗口大小',
                                         `delay_minutes` INT DEFAULT 0 COMMENT '延迟补偿（分钟）',
                                         `cron_expression` VARCHAR(50) NOT NULL COMMENT '定时触发表达式',
                                         `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
                                         `ext_condition` VARCHAR(500) DEFAULT NULL COMMENT '额外过滤条件',
                                         `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                         `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                         FOREIGN KEY (`source_data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE,
                                         FOREIGN KEY (`target_data_source_id`) REFERENCES `data_source_meta`(`id`) ON DELETE CASCADE
);

-- 核对实例表（每次执行记录）
CREATE TABLE `reconciliation_job` (
                                      `job_id` VARCHAR(32) PRIMARY KEY,
                                      `config_id` VARCHAR(32) NOT NULL,
                                      `window_start` DATETIME NOT NULL COMMENT '核对窗口开始时间',
                                      `window_end` DATETIME NOT NULL COMMENT '核对窗口结束时间',
                                      `source_count` BIGINT DEFAULT 0,
                                      `target_count` BIGINT DEFAULT 0,
                                      `diff_count` BIGINT DEFAULT 0,
                                      `source_missing_count` BIGINT DEFAULT 0,
                                      `target_extra_count` BIGINT DEFAULT 0,
                                      `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, SUCCESS, FAILED',
                                      `error_msg` VARCHAR(500) DEFAULT NULL,
                                      `start_time` DATETIME DEFAULT NULL,
                                      `end_time` DATETIME DEFAULT NULL,
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      FOREIGN KEY (`config_id`) REFERENCES `reconciliation_config`(`id`) ON DELETE CASCADE
);

-- 核对差异明细表
CREATE TABLE `reconciliation_diff` (
                                       `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       `job_id` VARCHAR(32) NOT NULL,
                                       `config_id` VARCHAR(32) NOT NULL,
                                       `diff_type` VARCHAR(10) NOT NULL COMMENT 'MISSING, EXTRA',
                                       `pk_value` VARCHAR(200) NOT NULL COMMENT '主键值',
                                       `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, FIXED, IGNORED',
                                       `fixed_time` DATETIME DEFAULT NULL,
                                       `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                       FOREIGN KEY (`job_id`) REFERENCES `reconciliation_job`(`job_id`) ON DELETE CASCADE,
                                       FOREIGN KEY (`config_id`) REFERENCES `reconciliation_config`(`id`) ON DELETE CASCADE,
                                       INDEX idx_job (`job_id`),
                                       INDEX idx_config_status (`config_id`, `status`)
);

CREATE TABLE `reconciliation_compensation` (
                                               `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               `diff_id` BIGINT NOT NULL COMMENT '关联差异ID',
                                               `job_id` VARCHAR(32) NOT NULL,
                                               `config_id` VARCHAR(32) NOT NULL,
                                               `action` VARCHAR(20) NOT NULL COMMENT 'INSERT, UPDATE, DELETE, IGNORE',
                                               `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, SUCCESS, FAILED',
                                               `error_msg` TEXT,
                                               `compensated_by` VARCHAR(50) DEFAULT 'system' COMMENT 'system 或 用户名',
                                               `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                               `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                               FOREIGN KEY (`diff_id`) REFERENCES `reconciliation_diff`(`id`) ON DELETE CASCADE,
                                               FOREIGN KEY (`job_id`) REFERENCES `reconciliation_job`(`job_id`) ON DELETE CASCADE,
                                               FOREIGN KEY (`config_id`) REFERENCES `reconciliation_config`(`id`) ON DELETE CASCADE
);

-- Quartz 集群表 (MySQL)
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


-- ========== reconciliation_config 新增字段 ==========
ALTER TABLE reconciliation_config
    ADD COLUMN compare_columns TEXT COMMENT '要比较的列，逗号分隔，空表示比较所有列',
    ADD COLUMN sample_rate DECIMAL(5,4) DEFAULT 1.0000 COMMENT '采样率 0~1',
    ADD COLUMN extra_action VARCHAR(20) DEFAULT 'IGNORE' COMMENT 'EXTRA数据处理: IGNORE, DELETE, REPORT';

-- ========== reconciliation_job 新增字段 ==========
ALTER TABLE reconciliation_job
    ADD COLUMN progress_percent INT DEFAULT 0,
    ADD COLUMN cancelled TINYINT(1) DEFAULT 0,
    ADD COLUMN source_checksum_count BIGINT DEFAULT 0,
    ADD COLUMN target_checksum_count BIGINT DEFAULT 0;

-- ========== reconciliation_diff 增加类型 ==========
ALTER TABLE reconciliation_diff MODIFY diff_type VARCHAR(20) NOT NULL COMMENT 'MISSING, EXTRA, CONTENT_DIFF';


ALTER TABLE reconciliation_config
    ADD COLUMN increment_type VARCHAR(20) DEFAULT 'TIMESTAMP' COMMENT '增量列类型: TIMESTAMP, NUMBER',
    ADD COLUMN shard_size BIGINT DEFAULT 1000000 COMMENT 'ID分片大小（仅NUMBER类型有效）';