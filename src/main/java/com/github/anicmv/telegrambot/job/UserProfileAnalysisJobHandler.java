package com.github.anicmv.telegrambot.job;

import com.github.anicmv.telegrambot.model.ProfileAnalysisStats;
import com.github.anicmv.telegrambot.service.ProfileAnalysisService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 用户画像每日分析任务，由 xxl-job 调度中心触发。
 * 调度中心建任务时 JobHandler 填 {@code userProfileAnalysisHandler}，
 * 阻塞处理策略建议“丢弃后续调度”，失败重试次数建议 0。
 */
@Log4j2
@Component
public class UserProfileAnalysisJobHandler {

    private final ProfileAnalysisService profileAnalysisService;

    public UserProfileAnalysisJobHandler(ProfileAnalysisService profileAnalysisService) {
        this.profileAnalysisService = profileAnalysisService;
    }

    @XxlJob("userProfileAnalysisHandler")
    public void execute() {
        XxlJobHelper.log("开始用户画像分析任务");
        try {
            ProfileAnalysisStats stats = profileAnalysisService.analyzeAll(XxlJobHelper::log);
            XxlJobHelper.log("分析完成: 待分析={} 成功={} 失败={} 跳过={}",
                    stats.total(), stats.success(), stats.failed(), stats.skipped());
            if (stats.failed() > 0) {
                XxlJobHelper.handleFail("存在分析失败用户: " + stats.failed() + " 个，详见执行日志");
            }
        } catch (Exception e) {
            log.error("用户画像分析任务异常", e);
            XxlJobHelper.handleFail("任务异常: " + e.getMessage());
        }
    }
}
