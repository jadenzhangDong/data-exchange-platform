package com.dex.common.repository;

import com.dex.common.model.entity.ReconciliationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJobEntity, String> {
    List<ReconciliationJobEntity> findByConfigId(String configId);
    List<ReconciliationJobEntity> findByStatus(String status);
}
