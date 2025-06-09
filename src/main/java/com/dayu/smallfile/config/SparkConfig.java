package com.dayu.smallfile.config;

import lombok.Data;

import java.util.Map;

/**
 * Spark配置类
 */
@Data
public class SparkConfig {
    private Map<String, String> jobParameters;
} 