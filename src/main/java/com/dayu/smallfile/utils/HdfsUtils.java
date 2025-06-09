package com.dayu.smallfile.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HDFS工具类
 */
public class HdfsUtils {
    private static final Logger logger = LoggerFactory.getLogger(HdfsUtils.class);
    
    /**
     * 获取HDFS默认块大小
     * 
     * @return HDFS默认块大小（字节）
     */
    public static long getHdfsDefaultBlockSize() throws Exception{
        try {
            Configuration hadoopConf = new Configuration();
            FileSystem fs = FileSystem.get(hadoopConf);
            return fs.getDefaultBlockSize(new Path("/"));
        } catch (Exception e) {
            logger.warn("获取HDFS默认块大小失败", e);
            throw e;
        }
    }
} 