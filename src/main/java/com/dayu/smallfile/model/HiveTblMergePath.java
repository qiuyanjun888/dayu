package com.dayu.smallfile.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hive表合并路径模型类，表示需要进行小文件合并的HDFS路径
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HiveTblMergePath {
    private String dbName;
    private String tableName;
    private String targetPath;
    private String fileFormat;
    private long modificationTime;
    private long dirSize;
    private long fileCount;
    private int targetNum;
    
    public HiveTblMergePath(String dbName, String tableName, String targetPath, String fileFormat) {
        this.dbName = dbName;
        this.tableName = tableName;
        this.targetPath = targetPath;
        this.fileFormat = fileFormat;
    }
} 