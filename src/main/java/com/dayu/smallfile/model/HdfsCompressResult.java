package com.dayu.smallfile.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HDFS压缩结果统计类
 */
@Data
public class HdfsCompressResult {
    private String rootPath;                     // 根路径
    private AtomicInteger totalFiles;            // 总文件数
    private AtomicInteger processingFiles;       // 处理中文件数
    private AtomicInteger completedFiles;        // 已完成文件数
    private AtomicInteger successFiles;          // 成功文件数
    private AtomicInteger failedFiles;           // 失败文件数
    private AtomicLong originalTotalSize;        // 原始总大小
    private AtomicLong compressedTotalSize;      // 压缩后总大小
    private AtomicLong totalProcessingTime;      // 总处理时间
    private long startTime;                      // 开始时间
    private long endTime;                        // 结束时间
    private List<HdfsCompressTask> tasks;        // 任务列表
    
    public HdfsCompressResult(String rootPath) {
        this.rootPath = rootPath;
        this.totalFiles = new AtomicInteger(0);
        this.processingFiles = new AtomicInteger(0);
        this.completedFiles = new AtomicInteger(0);
        this.successFiles = new AtomicInteger(0);
        this.failedFiles = new AtomicInteger(0);
        this.originalTotalSize = new AtomicLong(0);
        this.compressedTotalSize = new AtomicLong(0);
        this.totalProcessingTime = new AtomicLong(0);
        this.startTime = System.currentTimeMillis();
        this.tasks = new ArrayList<>();
    }
    
    public synchronized void addTask(HdfsCompressTask task) {
        tasks.add(task);
        totalFiles.incrementAndGet();
        originalTotalSize.addAndGet(task.getOriginalSize());
    }
    
    public void taskStarted() {
        processingFiles.incrementAndGet();
    }
    
    public void taskCompleted(HdfsCompressTask task) {
        processingFiles.decrementAndGet();
        completedFiles.incrementAndGet();
        
        if ("SUCCESS".equals(task.getStatus())) {
            successFiles.incrementAndGet();
            compressedTotalSize.addAndGet(task.getCompressedSize());
            totalProcessingTime.addAndGet(task.getProcessingTime());
        } else if ("FAILED".equals(task.getStatus())) {
            failedFiles.incrementAndGet();
        }
    }
    
    public void complete() {
        this.endTime = System.currentTimeMillis();
    }
    
    public long getElapsedTime() {
        return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
    }
    
    public double getCompressionRatio() {
        if (originalTotalSize.get() == 0) {
            return 0;
        }
        return 1.0 - ((double) compressedTotalSize.get() / originalTotalSize.get());
    }
} 