package com.dayu.smallfile.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
    
    // HDFS压缩配置
    private HdfsCompressConfig hdfsCompress;
    
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
            this.hdfsCompress = config.getHdfsCompress();
            
            logger.info("配置加载完成");
        }
    }
}