package com.dex.common.watermark;

public interface WatermarkStore {

    /**
     * 获取水位线
     * @param taskId 任务ID
     * @param table 源表名
     * @param incrementColumn 增量字段
     * @return 水位线值（ID 或时间戳毫秒），若不存在返回 null
     */
    Long getWatermark(String taskId, String table, String incrementColumn);

    /**
     * 更新水位线（在 Sink 写入成功后调用）
     * @param taskId 任务ID
     * @param table 源表名
     * @param incrementColumn 增量字段
     * @param watermark 水位线值
     */
    void updateWatermark(String taskId, String table, String incrementColumn, Long watermark);

    /**
     * 删除任务的所有水位线（任务删除时清理）
     */
    void removeWatermark(String taskId);
}