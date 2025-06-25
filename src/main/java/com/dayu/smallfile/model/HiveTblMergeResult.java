package com.dayu.smallfile.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hive表合并结果模型类，表示小文件合并的结果信息
 */
@Data
@NoArgsConstructor
public class HiveTblMergeResult {
    private String dbName;
    private String tableName;
    private String path;
    private String fileFormat;
    private long beforeFileCount;
    private long afterFileCount;
    private long beforeAvgSize;
    private long afterAvgSize;
    private String status;
    private long duration;
    private String errorMessage;

    public HiveTblMergeResult(String dbName, String tableName, String path, String fileFormat) {
        this.dbName = dbName;
        this.tableName = tableName;
        this.path = path;
        this.fileFormat = fileFormat;
        this.status = "PENDING";
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (errorMessage != null && !errorMessage.isEmpty()) {
            this.status = "FAILED";
        }
    }

    public void markSuccess() {
        this.status = "SUCCESS";
    }

    public void markSkipped(String reason) {
        this.status = "SKIPPED";
        this.errorMessage = reason;
    }
} 