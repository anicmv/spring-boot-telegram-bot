package com.github.anicmv.telegrambot.model;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 画像分析任务统计。
 */
public record ProfileAnalysisStats(int total, int success, int failed, int skipped) {
}
