package com.dayu.smallfile.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进度条工具类，提供类似Spark风格的进度条显示
 */
public class ProgressBarUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProgressBarUtil.class);
    
    // 进度条长度
    private static final int BAR_LENGTH = 30;
    // 进度条字符
    private static final char PROGRESS_CHAR = '=';
    private static final char REMAINING_CHAR = ' ';
    private static final char ARROW_CHAR = '>';
    
    private final int totalTasks;
    private final AtomicInteger completedTasks;
    private final AtomicInteger successTasks;
    private final AtomicInteger failedTasks;
    private final Instant startTime;
    private String taskName;
    
    /**
     * 创建进度条工具
     *
     * @param totalTasks 总任务数
     * @param taskName 任务名称
     */
    public ProgressBarUtil(int totalTasks, String taskName) {
        this.totalTasks = totalTasks;
        this.completedTasks = new AtomicInteger(0);
        this.successTasks = new AtomicInteger(0);
        this.failedTasks = new AtomicInteger(0);
        this.startTime = Instant.now();
        this.taskName = taskName;
    }
    
    /**
     * 更新进度
     *
     * @param success 是否成功
     */
    public void update(boolean success) {
        if (success) {
            successTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
        }
        int completed = completedTasks.incrementAndGet();
        printProgress(completed);
    }
    
    /**
     * 打印进度条
     *
     * @param completed 已完成任务数
     */
    private void printProgress(int completed) {
        if (totalTasks <= 0) return;
        
        // 计算进度百分比
        double percentage = (double) completed / totalTasks;
        int progressChars = (int) (percentage * BAR_LENGTH);
        
        // 构建进度条
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < progressChars) {
                bar.append(PROGRESS_CHAR);
            } else if (i == progressChars && completed < totalTasks) {
                bar.append(ARROW_CHAR);
            } else {
                bar.append(REMAINING_CHAR);
            }
        }
        bar.append("]");
        
        // 计算运行时间
        Duration duration = Duration.between(startTime, Instant.now());
        String runTime = String.format("%02d:%02d:%02d", 
                duration.toHours(), 
                (duration.toMinutes() % 60), 
                (duration.getSeconds() % 60));
        
        // 计算剩余时间（基于平均处理速度）
        String remainingTime = "--:--:--";
        if (completed > 0) {
            double timePerTask = duration.toMillis() / (double) completed;
            long remainingMillis = (long) (timePerTask * (totalTasks - completed));
            Duration remainingDuration = Duration.ofMillis(remainingMillis);
            remainingTime = String.format("%02d:%02d:%02d", 
                    remainingDuration.toHours(), 
                    (remainingDuration.toMinutes() % 60), 
                    (remainingDuration.getSeconds() % 60));
        }
        
        // 打印进度信息
        logger.info("{} 进度: {} {}/{} ({}%) [成功: {}, 失败: {}] 已用时间: {} 预计剩余: {}", 
                taskName,
                bar.toString(), 
                completed, 
                totalTasks, 
                String.format("%.1f", percentage * 100),
                successTasks.get(),
                failedTasks.get(),
                runTime,
                remainingTime);
    }
    
    /**
     * 完成所有任务
     */
    public void complete() {
        Duration totalDuration = Duration.between(startTime, Instant.now());
        String totalRunTime = String.format("%02d:%02d:%02d", 
                totalDuration.toHours(), 
                (totalDuration.toMinutes() % 60), 
                (totalDuration.getSeconds() % 60));
        
        logger.info("{} 完成! 总任务数: {}, 成功: {}, 失败: {}, 总运行时间: {}", 
                taskName,
                totalTasks, 
                successTasks.get(), 
                failedTasks.get(), 
                totalRunTime);
    }
} 