package com.dayu.smallfile.config;

import com.dayu.smallfile.model.HiveDatabase;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 小文件合并配置类
 * 整合了之前的merge、scan和advanced的配置
 */
@Data
public class SmallFileMergeConfig {
    
    /**
     * HDFS块大小
     */
    @JsonProperty("hdfs-block-size")
    private String hdfsBlockSize;
    
    /**
     * 临时目录
     */
    @JsonProperty("temp-dir")
    private String tempDir;
    
    /**
     * Hive数据库配置
     */
    @JsonProperty("hive-databases")
    private List<HiveDatabase> hiveDatabases;
    
    /**
     * 线程池大小
     */
    @JsonProperty("thread-pool-size")
    private int threadPoolSize;
    
    /**
     * 报告输出目录
     */
    @JsonProperty("report-output-dir")
    private String reportOutputDir;
    
    /**
     * 是否试运行模式
     */
    @JsonProperty("dry-run")
    private boolean dryRun;
    
    /**
     * 是否清理原文件
     */
    private boolean cleanup;
    
    /**
     * 备份目录
     */
    @JsonProperty("backup-dir")
    private String backupDir;
} 