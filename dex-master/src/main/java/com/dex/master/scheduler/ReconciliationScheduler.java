package com.dex.master.scheduler;

import com.dex.common.model.entity.ReconciliationConfigEntity;
import com.dex.common.repository.ReconciliationConfigRepository;
import com.dex.master.quartz.ReconciliationQuartzJob;
import com.dex.master.service.ReconciliationService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class ReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    @Autowired
    private ReconciliationConfigRepository configRepo;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private Scheduler quartzScheduler;

    private static final String JOB_GROUP = "DEX_RECONCILIATION_GROUP";
    private static final String TRIGGER_GROUP = "DEX_RECONCILIATION_TRIGGER_GROUP";

    @PostConstruct
    public void init() {
        scheduleAll();
        log.info("Quartz 核对调度器初始化完成");
    }

    public void scheduleAll() {
        try {
            List<ReconciliationConfigEntity> configs = configRepo.findByEnabledTrue();
            for (ReconciliationConfigEntity config : configs) {
                scheduleConfig(config);
            }
            log.info("注册 {} 个 Quartz 核对任务", configs.size());
        } catch (Exception e) {
            log.error("注册核对定时任务失败", e);
        }
    }

    public void scheduleConfig(ReconciliationConfigEntity config) {
        try {
            String configId = config.getId();
            String cron = config.getCronExpression();
            if (cron == null || cron.isEmpty()) return;

            JobKey jobKey = JobKey.jobKey(configId, JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.deleteJob(jobKey);
            }

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("configId", configId);

            JobDetail jobDetail = JobBuilder.newJob(ReconciliationQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(jobDataMap)
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(configId, TRIGGER_GROUP))
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);
            log.info("Quartz 核对任务注册成功: configId={}, cron={}", configId, cron);
        } catch (Exception e) {
            log.error("注册核对定时任务失败", e);
        }
    }

    public void cancelConfig(String configId) {
        try {
            JobKey jobKey = JobKey.jobKey(configId, JOB_GROUP);
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.deleteJob(jobKey);
                log.info("取消 Quartz 核对任务: {}", configId);
            }
        } catch (Exception e) {
            log.error("取消核对定时任务失败: {}", configId, e);
        }
    }

    public void rescheduleConfig(ReconciliationConfigEntity config) {
        cancelConfig(config.getId());
        scheduleConfig(config);
    }
}