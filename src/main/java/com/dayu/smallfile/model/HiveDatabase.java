package com.dayu.smallfile.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Hive数据库配置
 */
@Data
public class HiveDatabase {
    /**
     * 数据库名称
     */
    @JsonProperty("db-name")
    private String dbName;
    
    /**
     * 包含的表名模式(正则表达式)
     */
    private String includes;
    
    /**
     * 排除的表名模式(正则表达式)
     */
    private String excludes;
} 