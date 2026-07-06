package com.dex.common.constants;

public interface ZkPaths {
    String ROOT = "/data-exchange";
    String MASTER_ELECTION = ROOT + "/master";
    String LEADER_INFO = MASTER_ELECTION + "/leader-info";
    String WORKERS_REGISTRY = ROOT + "/workers";
    String TASKS = ROOT + "/tasks";
}