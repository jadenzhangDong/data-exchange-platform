package com.dex.common.repository;

import com.dex.common.model.entity.TaskDefinition;
import com.dex.common.model.entity.TaskInstance;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存任务仓库（用于测试或单机模式）
 */
@Repository
public class InMemoryTaskRepository {
    private final Map<String, TaskDefinition> defs = new ConcurrentHashMap<>();
    private final Map<String, TaskInstance> instances = new ConcurrentHashMap<>();

    public void saveTaskDef(TaskDefinition d) {
        defs.put(d.getTaskId(), d);
    }

    public TaskDefinition getTaskDef(String id) {
        return defs.get(id);
    }

    public List<TaskDefinition> getAllEnabled() {
        List<TaskDefinition> list = new ArrayList<>();
        for (TaskDefinition d : defs.values()) {
            if ("ENABLED".equals(d.getStatus())) {
                list.add(d);
            }
        }
        return list;
    }

    public List<TaskInstance> getRunningInstances() {
        List<TaskInstance> list = new ArrayList<>();
        for (TaskInstance i : instances.values()) {
            if ("RUNNING".equals(i.getState()) || "PENDING".equals(i.getState())) {
                list.add(i);
            }
        }
        return list;
    }

    public List<TaskInstance> getInstancesByWorker(String workerId) {
        List<TaskInstance> list = new ArrayList<>();
        for (TaskInstance i : instances.values()) {
            if (workerId.equals(i.getAssignedWorkerId()) && "RUNNING".equals(i.getState())) {
                list.add(i);
            }
        }
        return list;
    }

    public void saveInstance(TaskInstance i) {
        instances.put(i.getInstanceId(), i);
    }

    public void updateState(String instanceId, String state) {
        TaskInstance i = instances.get(instanceId);
        if (i != null) {
            i.setState(state);
        }
    }

    public void deleteTaskDef(String taskId) {
        defs.remove(taskId);
    }

    public void deleteInstance(String instanceId) {
        instances.remove(instanceId);
    }

    public List<TaskDefinition> getAllTaskDefs() {
        return new ArrayList<>(defs.values());
    }

    public List<TaskInstance> getAllInstances() {
        return new ArrayList<>(instances.values());
    }
}