package com.dex.plugin.cdc;

import com.dex.plugin.api.Source;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CDC Source 插件
 * <p>
 * 配置参数：
 * <ul>
 *   <li>host: 数据库主机（必填）</li>
 *   <li>port: 数据库端口（默认 3306）</li>
 *   <li>user: 数据库用户（必填）</li>
 *   <li>password: 数据库密码（必填）</li>
 *   <li>database: 数据库名（必填）</li>
 *   <li>tableWhitelist: 表白名单（可选，如 test\\.users）</li>
 *   <li>offsetStorageFile: 偏移量存储文件（默认 ./cdc-offset.dat）</li>
 *   <li>queueCapacity: 队列容量（默认 100）</li>
 *   <li>pollTimeoutMs: 拉取超时毫秒（默认 500）</li>
 * </ul>
 */
public class CdcSource implements Source<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(CdcSource.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private ExecutorService executor;
    private BlockingQueue<List<Map<String, Object>>> queue;
    private volatile boolean running = true;
    private volatile Throwable engineError = null;
    private AtomicBoolean engineStarted = new AtomicBoolean(false);

    private String offsetStorageFile;
    private int queueCapacity = 100;
    private long pollTimeoutMs = 500;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        String host = (String) config.get("host");
        int port = (int) config.getOrDefault("port", 3306);
        String user = (String) config.get("user");
        String password = (String) config.get("password");
        String database = (String) config.get("database");
        String tableWhitelist = (String) config.get("tableWhitelist");
        offsetStorageFile = (String) config.getOrDefault("offsetStorageFile", "./cdc-offset.dat");
        queueCapacity = (Integer) config.getOrDefault("queueCapacity", 100);
        pollTimeoutMs = (Long) config.getOrDefault("pollTimeoutMs", 500L);

        if (host == null || user == null || password == null || database == null) {
            throw new IllegalArgumentException("host, user, password, database 为必填参数");
        }

        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        Configuration.Builder builder = Configuration.create()
                .with("name", "cdc-" + UUID.randomUUID())
                .with("connector.class", "io.debezium.connector.mysql.MySqlConnector")
                .with("database.hostname", host)
                .with("database.port", port)
                .with("database.user", user)
                .with("database.password", password)
                .with("database.server.id", getUniqueServerId())
                .with("database.server.name", "cdc-server")
                .with("database.include.list", database)
                .with("offset.storage", "io.debezium.storage.file.OffsetBackingStore")
                .with("offset.storage.file.filename", offsetStorageFile)
                .with("include.schema.changes", false)
                .with("snapshot.mode", "initial")
                .with("event.processing.failure.handling.mode", "skip")
                .with("offset.flush.interval.ms", "5000")
                .with("max.queue.size", queueCapacity * 2)
                .with("max.batch.size", 2048);

        if (tableWhitelist != null && !tableWhitelist.isEmpty()) {
            builder.with("table.include.list", tableWhitelist);
        }
        Configuration debeziumConfig = builder.build();

        this.engine = DebeziumEngine.create(Json.class)
                .using(debeziumConfig.asProperties())
                .notifying(this::handleChangeEvent)
                .using((success, message, error) -> {
                    if (error != null) {
                        log.error("Debezium 引擎异常", error);
                        engineError = error;
                    } else {
                        log.info("Debezium 引擎正常停止");
                    }
                })
                .build();

        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "debezium-engine"));
        executor.submit(() -> {
            try {
                engineStarted.set(true);
                engine.run();
            } catch (Exception e) {
                log.error("Debezium 引擎运行异常", e);
                engineError = e;
            } finally {
                engineStarted.set(false);
            }
        });

        long start = System.currentTimeMillis();
        while (!engineStarted.get() && System.currentTimeMillis() - start < 10000) {
            Thread.sleep(200);
        }
        if (!engineStarted.get()) {
            throw new RuntimeException("Debezium 引擎启动超时");
        }
        log.info("CDC Source 启动成功，offset 文件: {}", offsetStorageFile);
    }

    private void handleChangeEvent(ChangeEvent<String, String> event) {
        if (!running) return;
        String value = event.value();
        if (value == null) return;
        try {
            Map<String, Object> row = parseDebeziumEvent(value);
            if (row != null) {
                List<Map<String, Object>> batch = Collections.singletonList(row);
                boolean offered = queue.offer(batch, 10, TimeUnit.MILLISECONDS);
                if (!offered) {
                    log.warn("队列满，丢弃事件");
                }
            }
        } catch (Exception e) {
            log.error("处理变更事件失败", e);
        }
    }

    private Map<String, Object> parseDebeziumEvent(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode after = node.get("after");
            if (after != null && !after.isNull()) {
                return mapper.convertValue(after, Map.class);
            }
            return null;
        } catch (IOException e) {
            log.warn("解析事件失败: {}", json);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> read(int batchSize) throws Exception {
        if (engineError != null) {
            throw new RuntimeException("Debezium 引擎异常", engineError);
        }
        List<Map<String, Object>> batch = new ArrayList<>();
        long start = System.currentTimeMillis();
        while (batch.size() < batchSize && (System.currentTimeMillis() - start) < pollTimeoutMs) {
            List<Map<String, Object>> chunk = queue.poll(100, TimeUnit.MILLISECONDS);
            if (chunk != null) {
                batch.addAll(chunk);
            } else {
                Thread.sleep(50);
            }
        }
        return batch;
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (engine != null) {
            try {
                engine.close();
            } catch (IOException e) {
                log.warn("关闭引擎异常", e);
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("CDC Source 关闭");
    }

    private int getUniqueServerId() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return (ip + pid).hashCode() & Integer.MAX_VALUE;
        } catch (Exception e) {
            return new Random().nextInt(10000) + 10000;
        }
    }
}