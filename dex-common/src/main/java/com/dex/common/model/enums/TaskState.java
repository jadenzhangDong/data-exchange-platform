package com.dex.common.model.enums;

public enum TaskState {
    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    STOPPED("已停止"),
    TIMEOUT("超时"),
    RETRYING("重试中"),
    CANCELLING("取消中");

    private final String desc;

    TaskState(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 判断当前状态是否可以转换为目标状态
     */
    public boolean canTransitionTo(TaskState target) {
        if (this == target) return true;
        switch (this) {
            case PENDING:
                return target == RUNNING || target == STOPPED;
            case RUNNING:
                return target == SUCCESS || target == FAILED || target == STOPPED
                        || target == TIMEOUT || target == CANCELLING;
            case FAILED:
                return target == RETRYING || target == STOPPED;
            case RETRYING:
                return target == RUNNING || target == STOPPED || target == FAILED;
            case CANCELLING:
                return target == STOPPED;
            case SUCCESS:
            case STOPPED:
            case TIMEOUT:
                return false; // 终态不可转换
            default:
                return false;
        }
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == STOPPED || this == TIMEOUT;
    }

    public boolean isRunning() {
        return this == RUNNING || this == RETRYING || this == CANCELLING;
    }
}