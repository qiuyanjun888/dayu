package com.dayu.smallfile.config;

import lombok.Data;

/**
 * 合并配置类
 */
@Data
public class MergeConfig {
    private Long targetFileSize;
    private String tempDir;
    private String strategyClass;
} 