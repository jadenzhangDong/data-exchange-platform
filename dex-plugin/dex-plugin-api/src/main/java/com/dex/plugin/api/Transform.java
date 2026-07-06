package com.dex.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Transform<T, R> {
    R transform(T input);

    default List<R> transformBatch(List<T> inputs) {
        return inputs.stream().map(this::transform).collect(Collectors.toList());
    }

    /**
     * 初始化方法，子类可覆盖
     * @param config 配置参数
     */
    default void open(Map<String, Object> config) {
        // 默认空实现
    }
}