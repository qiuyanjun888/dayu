package com.dayu.smallfile.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置类，负责加载和管理应用配置
 */
@Data
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    
    // 默认策略类
    public static final String DEFAULT_SCAN_STRATEGY = "com.dayu.smallfile.strategy.impl.HiveTableScanStrategy";
    public static final String DEFAULT_MERGE_STRATEGY = "com.dayu.smallfile.strategy.impl.DefaultMergeStrategy";
    
    // 配置类引用
    private SparkConfig spark;
    private MergeConfig merge;
    private ScanConfig scan;
    private ThreadPoolConfig threadPool;
    private String fileBlockSize;
    
    // 高级配置
    private AdvancedConfig advanced;
    
    // 报告配置
    private ReportConfig report;
    
    /**
     * 从YAML文件加载配置
     *
     * @param configPath 配置文件路径
     * @throws IOException 如果文件读取失败
     */
    public void load(String configPath) throws IOException {
        logger.info("加载配置文件: {}", configPath);
        
        try (InputStream input = new FileInputStream(configPath)) {
            Yaml yaml = new Yaml(new Constructor(Config.class));
            Config config = yaml.load(input);
            
            // 将加载的配置复制到当前对象
            this.spark = config.getSpark();
            this.merge = config.getMerge();
            this.scan = config.getScan();
            this.threadPool = config.getThreadPool();
            this.advanced = config.getAdvanced();
            this.report = config.getReport();
            
            logger.info("配置加载完成");
        }
    }
    
   /* *//**
     * 获取扫描策略类名
     * 
     * @return 扫描策略类名
     *//*
    public String getScanStrategyClass() {
        if (scan != null && scan.getStrategy() != null && scan.getStrategy().getClassName() != null) {
            return scan.getStrategy().getClassName();
        }
        return DEFAULT_SCAN_STRATEGY;
    }
    
    *//**
     * 获取合并策略类名
     * 
     * @return 合并策略类名
     *//*
    public String getMergeStrategyClass() {
        if (merge != null && merge.getStrategy() != null && merge.getStrategy().getClassName() != null) {
            return merge.getStrategy().getClassName();
        }
        return DEFAULT_MERGE_STRATEGY;
    }
    
    *//**
     * 获取线程池大小
     * 
     * @return 线程池大小
     *//*
    public int getThreadCount() {
        if (threadPool != null) {
            return threadPool.getSize();
        }
        return 10; // 默认值
    }
    
    *//**
     * 获取配置值
     *
     * @param path 配置路径，如 "merge.target.file-size"
     * @param defaultValue 默认值
     * @return 配置值，如果不存在则返回默认值
     *//*
    @SuppressWarnings("unchecked")
    public <T> T getValue(String path, T defaultValue) {
        String[] keys = path.split("\\.");
        Object current = this;
        
        try {
            for (String key : keys) {
                // 将key转换为驼峰命名
                String camelKey = toCamelCase(key);
                
                // 使用反射获取属性值
                java.lang.reflect.Method getter = current.getClass().getMethod("get" + camelKey.substring(0, 1).toUpperCase() + camelKey.substring(1));
                current = getter.invoke(current);
                
                if (current == null) {
                    return defaultValue;
                }
            }
            
            return (T) current;
        } catch (Exception e) {
            logger.warn("获取配置项 {} 失败，使用默认值", path, e);
            return defaultValue;
        }
    }
    
    *//**
     * 将短横线分隔的字符串转换为驼峰命名
     * 
     * @param str 短横线分隔的字符串
     * @return 驼峰命名的字符串
     *//*
    private String toCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }
        
        return result.toString();
    }*/
    


   /* *//**
     * 获取目标文件大小配置
     *
     * @return 目标文件大小（字节）
     *//*
    public long getTargetFileSize() throws Exception {
        if (merge != null && merge.getTarget() != null && merge.getTarget().getFileSize() != null) {
            return parseSize(merge.getTarget().getFileSize());
        } else {
            // 如果没有配置，则使用HDFS默认块大小
            return HdfsUtils.getHdfsDefaultBlockSize();
        }
    }
    
    *//**
     * 获取线程池超时时间（毫秒）
     *
     * @return 超时时间（毫秒）
     *//*
    public long getThreadPoolTimeout() {
        if (threadPool != null && threadPool.getTimeout() != null) {
            return parseTime(threadPool.getTimeout());
        }
        return parseTime("2m"); // 默认2分钟
    }*/

} 