package com.dex.common.model;

import lombok.Data;

import java.util.List;

@Data
public class WorkerInfo {
    private String workerId;
    private String host;
    private int port;
    private int grpcPort = 19090;

    // 状态: ONLINE, OFFLINE, DISABLED, DRAINING
    private String status = "ONLINE";

    // 负载（当前运行任务数）
    private int load;

    // 权重（1-100，默认10，影响任务分发概率）
    private int weight = 10;

    // 标签（用于任务路由，如 env=prod, zone=az1）
    private List<String> tags;

    private long lastHeartbeat;
}