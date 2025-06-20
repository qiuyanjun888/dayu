package com.dayu.smallfile.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 配置类，负责加载和管理应用配置
 */
@Data
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    /**
     * 小文件合并配置
     */
    @JsonProperty("small-file-merge")
    private SmallFileMergeConfig smallFileMerge;
    
    /**
     * HDFS压缩配置
     */
    @JsonProperty("hdfs-compress")
    private HdfsCompressConfig hdfsCompress;
    
    /**
     * 从YAML文件加载配置
     *
     * @param configPath 配置文件路径
     * @throws IOException 如果文件读取失败
     */
    public void load(String configPath) throws IOException {
        logger.info("加载配置文件: {}", configPath);
        
        // 使用Jackson解析YAML
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Config loadedConfig = mapper.readValue(new File(configPath), Config.class);
        
        // 将加载的配置复制到当前对象
        this.smallFileMerge = loadedConfig.getSmallFileMerge();
        this.hdfsCompress = loadedConfig.getHdfsCompress();
        
        logger.info("配置加载完成");
    }
}