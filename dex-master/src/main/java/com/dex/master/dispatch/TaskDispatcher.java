package com.dex.master.dispatch;

import com.dex.common.model.task.TaskConfig;

public interface TaskDispatcher {
    void dispatchTask(TaskConfig task, String instanceId);

    default void dispatchTask(TaskConfig task, String instanceId, String requiredTag) {
        // 默认实现：忽略标签
        dispatchTask(task, instanceId);
    }

    void stopTask(String taskId, String instanceId);
}