package com.babylog.biz.media.retention;

import com.babylog.biz.media.service.MediaRetentionService;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.babylog.starter.job.api.JobExecutionHelper;
import com.babylog.starter.job.api.JobExecutionRecorder;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 媒体淘汰 xxl-job（K2 / M69，契约 {@code 24} ④）。
 *
 * <p>照既有 job 体例（{@code MessageCleanupJob}）用 {@code @XxlJob} +
 * {@code JobExecutionHelper.run(...)}，任务本体与运维诊断端点共用
 * {@link MediaRetentionService#run()} 同一个实现，避免两套口径。
 *
 * <p><b>刻意不加 {@code @PostConstruct} 本地兜底注册</b>：既有 job 会自注册到
 * {@code JobHandlerRegistry} / {@code RetentionJobRegistry}，那等于 AI 侧启动了本地调度；
 * 按 {@code 23} §五 K2 与工程纪律，<b>xxl-job 执行器 / 任务启停交龙哥，AI 不启 xxl-job</b>。
 * 本类只提供 handler 代码；配置清单见
 * {@code babylog-plugin-biz/docs/media-endpoints-M69.md} §六。
 */
@Component
public class MediaRetentionJob {

    /** XXL-Job handler 名（配置清单中的 JobHandler 值，须逐字一致） */
    public static final String HANDLER = "mediaRetentionCleanup";

    private static final Logger log = LoggerFactory.getLogger(MediaRetentionJob.class);

    private final MediaRetentionService retentionService;
    private final ObjectProvider<JobExecutionRecorder> recorder;

    public MediaRetentionJob(MediaRetentionService retentionService,
                             ObjectProvider<JobExecutionRecorder> recorder) {
        this.retentionService = retentionService;
        this.recorder = recorder;
    }

    /** 建议 cron：{@code 0 15 * * * ?}（每小时一次；三条触发本身允许误差） */
    @XxlJob(HANDLER)
    public void execute() {
        JobExecutionHelper.run(HANDLER, recorder, this::doExecute);
    }

    private void doExecute() {
        MediaRetentionVO vo = retentionService.run();
        log.info("[BabyLog][media][job] {} 完成 totalDeleted={} physicalDeleteFailed={}",
                HANDLER, vo.getTotalDeleted(), vo.getPhysicalDeleteFailed());
    }
}
