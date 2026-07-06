package com.dex.plugin.mock;

import com.dex.plugin.api.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MockSource implements Source<Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(MockSource.class);
    private int counter = 0;

    @Override
    public void open(Map<String, Object> config) throws Exception {
        log.info("MockSource 打开, config={}", config);
    }

    @Override
    public List<Map<String, Object>> read(int batchSize) throws Exception {
        if (counter++ > 5) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", i);
            row.put("name", "user_" + i);
            row.put("ts", System.currentTimeMillis());
            list.add(row);
        }
        return list;
    }

    @Override
    public void close() throws Exception {
        log.info("MockSource 关闭");
    }
}