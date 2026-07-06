package com.dex.plugin.mock;

import com.dex.plugin.api.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class MockSink implements Sink<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(MockSink.class);

    @Override
    public void open(Map<String, Object> config) throws Exception {
        log.info("MockSink 打开, config={}", config);
    }

    @Override
    public void write(List<Map<String, Object>> data) throws Exception {
        log.info("MockSink 写入 {} 条数据, 第一条: {}", data.size(), data.isEmpty() ? "空" : data.get(0));
    }

    @Override
    public void close() throws Exception {
        log.info("MockSink 关闭");
    }
}