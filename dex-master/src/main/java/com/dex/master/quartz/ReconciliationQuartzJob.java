package com.dex.master.quartz;

import com.dex.master.service.ReconciliationService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationQuartzJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationQuartzJob.class);

    @Autowired
    private ReconciliationService reconciliationService;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String configId = dataMap.getString("configId");

        log.info("Quartz 触发核对任务: configId={}", configId);

        try {
            reconciliationService.executeReconciliation(configId);
        } catch (Exception e) {
            log.error("Quartz 执行核对失败: {}", configId, e);
        }
    }
}