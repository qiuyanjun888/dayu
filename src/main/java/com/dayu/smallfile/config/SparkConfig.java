package com.dayu.smallfile.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Spark配置类
 */
@Data
public class SparkConfig {
    @JsonProperty("job-parameters")
    private Map<String, String> jobParameters;
} 