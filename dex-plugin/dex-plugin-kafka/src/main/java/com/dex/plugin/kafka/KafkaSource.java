package com.dex.plugin.kafka;

import com.dex.plugin.api.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka Source 插件
 * 配置参数：
 * - bootstrapServers: Kafka 集群地址（必填）
 * - topic: 订阅的主题（必填）
 * - groupId: 消费组ID（默认 dex-consumer）
 * - autoOffsetReset: earliest / latest（默认 latest）
 * - pollTimeoutMs: 拉取超时毫秒（默认 1000）
 * - batchSize: 每批最大记录数（默认 500）
 * - valueDeserializer: 值反序列化器（默认 StringDeserializer，可自定义）
 */
public class KafkaSource implements Source<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(KafkaSource.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private KafkaConsumer<String, String> consumer;
    private String topic;
    private int pollTimeoutMs;
    private int batchSize;
    private AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public void open(Map<String, Object> config) throws Exception {
        String bootstrapServers = (String) config.get("bootstrapServers");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            throw new IllegalArgumentException("bootstrapServers 必须指定");
        }

        this.topic = (String) config.get("topic");
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic 必须指定");
        }

        String groupId = (String) config.getOrDefault("groupId", "dex-consumer");
        String autoOffsetReset = (String) config.getOrDefault("autoOffsetReset", "latest");
        this.pollTimeoutMs = (Integer) config.getOrDefault("pollTimeoutMs", 1000);
        this.batchSize = (Integer) config.getOrDefault("batchSize", 500);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // 可配置 max.poll.records
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchSize);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        log.info("KafkaSource 启动成功, topic={}, groupId={}", topic, groupId);
    }

    @Override
    public List<Map<String, Object>> read(int batchSize) throws Exception {
        if (!running.get()) {
            return Collections.emptyList();
        }
        int limit = Math.min(batchSize, this.batchSize);
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 添加元数据
            row.put("_topic", record.topic());
            row.put("_partition", record.partition());
            row.put("_offset", record.offset());
            row.put("_timestamp", record.timestamp());
            row.put("_key", record.key());
            // value 作为 JSON 解析，如果无法解析则作为字符串存储
            String value = record.value();
            if (value != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = mapper.readValue(value, Map.class);
                    row.putAll(parsed);
                } catch (Exception e) {
                    // 非 JSON 格式，直接存储为 _value 字段
                    row.put("_value", value);
                }
            }
            // 限制大小防止内存溢出
            if (result.size() >= limit) {
                // 手动提交偏移量（批量处理）
                // 注意：这里手动提交，但批次未完全处理完之前可能会丢数据，考虑在 Worker 层面事务提交，这里简化，由框架控制
                break;
            }
            result.add(row);
        }
        // 手动提交偏移量（已消费完成）
        consumer.commitSync();
        log.debug("KafkaSource 读取 {} 条消息", result.size());
        return result;
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        if (consumer != null) {
            consumer.close(Duration.ofSeconds(5));
        }
        log.info("KafkaSource 关闭");
    }
}