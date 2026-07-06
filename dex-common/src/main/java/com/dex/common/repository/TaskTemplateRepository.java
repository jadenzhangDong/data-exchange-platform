package com.dex.common.repository;

import com.dex.common.model.entity.TaskTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplateEntity, String> {
}
