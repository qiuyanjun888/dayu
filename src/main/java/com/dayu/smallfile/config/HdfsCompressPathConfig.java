package com.dayu.smallfile.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * HDFS文件压缩路径配置类
 */
@Data
public class HdfsCompressPathConfig {
    @JsonProperty("root-path")
    private String rootPath;        // HDFS根路径
    private boolean recursive;      // 是否递归
    private String threshold;       // 文件大小阈值
    private String includes;        // 需要压缩的文件正则表达式，多个用逗号分隔
    private String excludes;        // 不需要压缩的文件正则表达式，多个用逗号分隔
} 