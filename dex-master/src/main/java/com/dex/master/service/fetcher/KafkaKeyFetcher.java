package com.dex.master.service.fetcher;

import com.dex.common.model.KeyChecksum;
import com.dex.common.service.KeyFetcher;
import com.dex.common.util.JsonUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KafkaKeyFetcher implements KeyFetcher {
    private static final Logger log = LoggerFactory.getLogger(KafkaKeyFetcher.class);
    private static final Map<String, KafkaConsumer<String, String>> consumerCache = new ConcurrentHashMap<>();

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final String keyField;
    private final String timestampField;
    private final String timestampFormat;
    private final boolean useRecordTimestamp;
    private final boolean isDebeziumFormat;
    private volatile boolean cancelled = false;
    private final Set<String> deletedKeys = new HashSet<>();

    public KafkaKeyFetcher(Map<String, Object> config) {
        String bootstrapServers = (String) config.get("bootstrapServers");
        this.topic = (String) config.get("topic");
        this.keyField = (String) config.get("keyField");
        this.timestampField = (String) config.get("timestampField");
        this.timestampFormat = (String) config.getOrDefault("timestampFormat", "yyyy-MM-dd HH:mm:ss");
        this.useRecordTimestamp = (Boolean) config.getOrDefault("useRecordTimestamp", true);
        this.isDebeziumFormat = (Boolean) config.getOrDefault("isDebeziumFormat", true);
        String groupId = (String) config.getOrDefault("groupId", "reconciliation-" + UUID.randomUUID());

        String cacheKey = bootstrapServers + ":" + groupId;
        consumer = consumerCache.computeIfAbsent(cacheKey, k -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new KafkaConsumer<>(props);
        });
    }

    @Override
    public Set<String> fetchKeys(Date windowStart, Date windowEnd) throws Exception {
        Set<KeyChecksum> checksums = fetchKeysWithChecksum(windowStart, windowEnd, null, null);
        return checksums.stream().map(KeyChecksum::getKey).collect(Collectors.toSet());
    }

    @Override
    public Set<KeyChecksum> fetchKeysWithChecksum(Date windowStart, Date windowEnd,
                                                  String compareColumns,
                                                  Consumer<Integer> progressCallback) throws Exception {
        Set<KeyChecksum> keys = new HashSet<>();
        deletedKeys.clear();

        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        List<TopicPartition> partitions = partitionInfos.stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .collect(Collectors.toList());

        // ========== 修正点 ==========
        Map<TopicPartition, Long> startOffsets = new HashMap<>();
        if (useRecordTimestamp) {
            Map<TopicPartition, OffsetAndTimestamp> offsetMap = consumer.offsetsForTimes(
                    partitions.stream().collect(Collectors.toMap(tp -> tp, tp -> windowStart.getTime()))
            );
            for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : offsetMap.entrySet()) {
                if (entry.getValue() != null) {
                    startOffsets.put(entry.getKey(), entry.getValue().offset());
                } else {
                    // 找不到对应时间戳的 offset，则从最早开始
                    startOffsets.put(entry.getKey(), 0L);
                }
            }
        } else {
            for (TopicPartition tp : partitions) {
                startOffsets.put(tp, 0L);
            }
        }

        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

        long totalEstimate = 0;
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            Long start = startOffsets.get(entry.getKey());
            if (start != null) {
                totalEstimate += (entry.getValue() - start);
            }
        }
        if (progressCallback != null) {
            progressCallback.accept(0);
        }

        consumer.assign(partitions);
        for (Map.Entry<TopicPartition, Long> entry : startOffsets.entrySet()) {
            if (entry.getValue() != null) {
                consumer.seek(entry.getKey(), entry.getValue());
            } else {
                consumer.seekToBeginning(Collections.singletonList(entry.getKey()));
            }
        }

        long processed = 0;
        long endTime = windowEnd.getTime();

        while (!cancelled) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            if (records.isEmpty()) break;
            for (ConsumerRecord<String, String> record : records) {
                if (useRecordTimestamp) {
                    long ts = record.timestamp();
                    if (ts < windowStart.getTime() || ts > endTime) continue;
                } else if (timestampField != null) {
                    // 业务时间过滤（略）
                }

                // 解析 JSON
                Map<String, Object> valueMap;
                try {
                    valueMap = JsonUtil.fromJson(record.value(), Map.class);
                } catch (Exception e) {
                    log.warn("解析消息失败", e);
                    continue;
                }

                // 处理 Debezium 格式
                String op = null;
                if (isDebeziumFormat && valueMap.containsKey("op")) {
                    op = (String) valueMap.get("op");
                    if ("d".equals(op)) {
                        Map<String, Object> before = (Map<String, Object>) valueMap.get("before");
                        if (before != null && keyField != null) {
                            Object keyObj = before.get(keyField);
                            if (keyObj != null) {
                                deletedKeys.add(keyObj.toString());
                            }
                        }
                        continue;
                    }
                    valueMap = (Map<String, Object>) valueMap.get("after");
                    if (valueMap == null) continue;
                }

                // 提取主键
                String key = null;
                if (keyField != null) {
                    Object keyObj = valueMap.get(keyField);
                    if (keyObj != null) key = keyObj.toString();
                } else {
                    key = record.key();
                }
                if (key == null) continue;

                // 计算校验和
                String checksum = null;
                if (compareColumns != null && !compareColumns.trim().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String col : compareColumns.split(",")) {
                        Object val = valueMap.get(col.trim());
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
                keys.add(new KeyChecksum(key, checksum));
                processed++;
            }

            if (progressCallback != null) {
                int progress = (int) (processed * 100 / (totalEstimate > 0 ? totalEstimate : 1));
                progressCallback.accept(Math.min(progress, 100));
            }

            // 检查是否全部消费完毕
            boolean allDone = true;
            for (TopicPartition tp : partitions) {
                long position = consumer.position(tp);
                long end = endOffsets.getOrDefault(tp, Long.MAX_VALUE);
                if (position < end) {
                    allDone = false;
                    break;
                }
            }
            if (allDone) break;
        }
        return keys;
    }

    @Override
    public Set<String> getDeletedKeys() {
        return new HashSet<>(deletedKeys);
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void close() throws Exception {
        // 不关闭 consumer（复用）
        log.debug("Kafka consumer 复用，不关闭");
    }
}