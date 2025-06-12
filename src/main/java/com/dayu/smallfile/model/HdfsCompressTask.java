package com.dayu.smallfile.model;

import lombok.Data;

/**
 * HDFS压缩任务模型类
 */
@Data
public class HdfsCompressTask {
    private String rootPath;           // 根路径
    private String filePath;           // 文件路径
    private long originalSize;         // 原始文件大小
    private long compressedSize;       // 压缩后文件大小
    private long processingTime;       // 处理时间（毫秒）
    private String status;             // 状态：PENDING, PROCESSING, SUCCESS, FAILED
    private String errorMessage;       // 错误信息
    
    public HdfsCompressTask(String rootPath, String filePath, long originalSize) {
        this.rootPath = rootPath;
        this.filePath = filePath;
        this.originalSize = originalSize;
        this.status = "PENDING";
    }
    
    public void markProcessing() {
        this.status = "PROCESSING";
    }
    
    public void markSuccess(long compressedSize, long processingTime) {
        this.status = "SUCCESS";
        this.compressedSize = compressedSize;
        this.processingTime = processingTime;
    }
    
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
    }
} 