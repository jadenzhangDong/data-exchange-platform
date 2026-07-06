package com.dex.common.repository;

import com.dex.common.model.entity.TaskDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDefinitionRepository extends JpaRepository<TaskDefinitionEntity, String> {
    List<TaskDefinitionEntity> findByStatus(String status);
}