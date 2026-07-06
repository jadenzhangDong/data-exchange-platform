package com.dex.plugin.kafka;

import com.dex.plugin.api.Sink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka Sink 插件
 * 配置参数：
 * - bootstrapServers: Kafka 集群地址（必填）
 * - topic: 目标主题（必填）
 * - keyField: 用作消息 Key 的字段名（可选）
 * - valueField: 用作消息 Value 的字段名（可选，如果不指定则整个 Map 转为 JSON）
 */
public class KafkaSink implements Sink<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(KafkaSink.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private KafkaProducer<String, String> producer;
    private String topic;
    private String keyField;
    private String valueField;
    private boolean useValueField;

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

        this.keyField = (String) config.get("keyField");
        this.valueField = (String) config.get("valueField");
        this.useValueField = valueField != null && !valueField.isEmpty();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 可配置 acks、retries 等
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        producer = new KafkaProducer<>(props);
        log.info("KafkaSink 启动成功, topic={}", topic);
    }

    @Override
    public void write(List<Map<String, Object>> data) throws Exception {
        if (data == null || data.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : data) {
            String key = null;
            if (keyField != null) {
                Object keyObj = row.get(keyField);
                if (keyObj != null) {
                    key = keyObj.toString();
                }
            }

            String value;
            if (useValueField) {
                Object valObj = row.get(valueField);
                value = valObj != null ? valObj.toString() : null;
            } else {
                try {
                    value = mapper.writeValueAsString(row);
                } catch (JsonProcessingException e) {
                    log.error("序列化消息失败", e);
                    value = row.toString();
                }
            }
            if (value == null) {
                log.warn("消息值为空，跳过发送");
                continue;
            }

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("发送消息失败", exception);
                } else {
                    log.debug("发送消息成功, partition={}, offset={}", metadata.partition(), metadata.offset());
                }
            });
        }
        producer.flush();
        log.debug("KafkaSink 写入 {} 条消息", data.size());
    }

    @Override
    public void close() throws Exception {
        if (producer != null) {
            producer.flush();
            producer.close();
        }
        log.info("KafkaSink 关闭");
    }
}