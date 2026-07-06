package com.dex.common.service;

import com.dex.common.model.KeyChecksum;

import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;

public interface KeyFetcher  extends AutoCloseable {
    // 基本：只返回主键（兼容旧场景）
    Set<String> fetchKeys(Date windowStart, Date windowEnd) throws Exception;

    // 增强：返回主键+校验和（支持内容比对）
    default Set<KeyChecksum> fetchKeysWithChecksum(Date windowStart, Date windowEnd,
                                                   String compareColumns,
                                                   Consumer<Integer> progressCallback) throws Exception {
        // 默认实现：只返回主键，checksum 为 null
        Set<String> keys = fetchKeys(windowStart, windowEnd);
        Set<KeyChecksum> result = new java.util.HashSet<>();
        for (String k : keys) {
            result.add(new KeyChecksum(k, null));
        }
        return result;
    }

    // 获取删除的主键（用于 Kafka DELETE 事件）
    default Set<String> getDeletedKeys() {
        return java.util.Collections.emptySet();
    }

    default void cancel() {}

    void close() throws Exception;
}