package com.dayu.smallfile.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * HDFS文件压缩配置类
 */
@Data
public class HdfsCompressConfig {
    private List<HdfsCompressPathConfig> paths;
    
    @JsonProperty("producer-threads")
    private int producerThreads = 3;  // 生产者线程数，默认3
    
    @JsonProperty("consumer-threads")
    private int consumerThreads = 10; // 消费者线程数，默认10
    
    /**
     * 报告输出目录
     */
    @JsonProperty("report-output-dir")
    private String reportOutputDir;
} 