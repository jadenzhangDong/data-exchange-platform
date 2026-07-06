package com.dex.web.controller;

import com.dex.common.model.entity.TaskDefinitionEntity;
import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.repository.TaskDefinitionRepository;
import com.dex.common.repository.TaskInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/task")
public class TaskWebController {

    @Autowired
    private TaskDefinitionRepository taskDefRepo;

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @GetMapping("/list")
    public List<TaskDefinitionEntity> listTasks() {
        return taskDefRepo.findAll();
    }

    @GetMapping("/instances/{taskId}")
    public List<TaskInstanceEntity> getInstances(@PathVariable String taskId) {
        return taskInstanceRepo.findByTaskId(taskId);
    }
}