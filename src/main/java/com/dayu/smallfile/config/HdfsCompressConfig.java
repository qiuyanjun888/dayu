package com.dayu.smallfile.config;

import lombok.Data;
import java.util.List;

/**
 * HDFS文件压缩配置类
 */
@Data
public class HdfsCompressConfig {
    private List<HdfsCompressPathConfig> paths;
    private int producerThreads = 3;  // 生产者线程数，默认3
    private int consumerThreads = 10; // 消费者线程数，默认10
} 