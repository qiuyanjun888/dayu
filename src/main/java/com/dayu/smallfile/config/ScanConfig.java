package com.dayu.smallfile.config;

import lombok.Data;

import java.util.List;

/**
 * 扫描配置类
 */
@Data
public class ScanConfig {
    private String strategyClass;

    private List<HiveDatabase> hiveDatabases;


    @Data
    public static class HiveDatabase {
        private String dbName;
        private String includes;
        private String excludes;
    }
} 