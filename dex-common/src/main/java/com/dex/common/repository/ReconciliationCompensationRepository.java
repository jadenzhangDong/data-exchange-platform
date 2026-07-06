package com.dex.common.repository;

import com.dex.common.model.entity.ReconciliationCompensationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationCompensationRepository extends JpaRepository<ReconciliationCompensationEntity, Long> {
    List<ReconciliationCompensationEntity> findByDiffId(Long diffId);
    List<ReconciliationCompensationEntity> findByJobId(String jobId);
    List<ReconciliationCompensationEntity> findByStatus(String status);
}
