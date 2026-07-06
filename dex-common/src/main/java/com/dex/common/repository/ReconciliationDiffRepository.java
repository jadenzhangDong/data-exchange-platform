package com.dex.common.repository;

import com.dex.common.model.entity.ReconciliationDiffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationDiffRepository extends JpaRepository<ReconciliationDiffEntity, Long> {

    // 根据 jobId 查询差异
    List<ReconciliationDiffEntity> findByJobId(String jobId);

    // 根据 jobId 和 diffType 和 status 查询
    List<ReconciliationDiffEntity> findByJobIdAndDiffTypeAndStatus(String jobId, String diffType, String status);

    // 根据 configId 查询所有差异
    List<ReconciliationDiffEntity> findByConfigId(String configId);

    // 根据 configId 和 status 查询
    List<ReconciliationDiffEntity> findByConfigIdAndStatus(String configId, String status);

    // 根据 configId 和 status 和 diffType 查询
    List<ReconciliationDiffEntity> findByConfigIdAndStatusAndDiffType(String configId, String status, String diffType);

    // 根据 configId 和 pkValue 和 status 查询（用于补偿后更新状态）
    List<ReconciliationDiffEntity> findByConfigIdAndPkValueAndStatus(String configId, String pkValue, String status);

    List<ReconciliationDiffEntity> findByJobIdAndDiffType(String jobId, String diffType);

}