package com.dex.plugin.api;

import java.util.List;
import java.util.Map;

public interface Source<T> {
    /**
     * 初始化 Source
     */
    void open(Map<String, Object> config) throws Exception;

    /**
     * 读取一批数据
     */
    List<T> read(int batchSize) throws Exception;

    /**
     * 提交水位线（在 Sink 写入成功后调用）
     * 默认空实现，需要水位线管理的 Source 可重写此方法
     */
    default void commitWatermark() {
        // 默认无操作
    }

    /**
     * 关闭释放资源
     */
    void close() throws Exception;
}