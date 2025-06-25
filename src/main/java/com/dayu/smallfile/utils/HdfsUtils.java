package com.dayu.smallfile.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.GzipCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    
    /**
     * 获取HDFS文件系统实例
     * 
     * @return HDFS文件系统实例
     * @throws IOException 如果获取失败
     */
    public static FileSystem getFileSystem() throws IOException {
        Configuration hadoopConf = new Configuration();
        return FileSystem.get(hadoopConf);
    }
    
    /**
     * 压缩HDFS文件
     * 
     * @param fs HDFS文件系统
     * @param inputPath 输入文件路径
     * @return 压缩后的文件路径
     * @throws IOException 如果压缩失败
     */
    public static Path compressFile(FileSystem fs, Path inputPath) throws IOException {
        // 创建GZIP压缩编解码器
        CompressionCodec codec = new GzipCodec();
        
        // 设置输出文件路径（添加编解码器默认扩展名）
        Path outputPath = new Path(inputPath.toString() + codec.getDefaultExtension());
        
        // 打开输入流和输出流
        try (InputStream in = fs.open(inputPath);
             OutputStream out = codec.createOutputStream(fs.create(outputPath))) {
            
            // 复制数据
            IOUtils.copyBytes(in, out, fs.getConf(), false);
        }
        
        return outputPath;
    }
    
    /**
     * 列出目录中的所有文件（可选递归）
     * 
     * @param fs HDFS文件系统
     * @param dirPath 目录路径
     * @param recursive 是否递归
     * @return 文件路径列表
     * @throws IOException 如果列出文件失败
     */
    public static List<Path> listFiles(FileSystem fs, Path dirPath, boolean recursive) throws IOException {
        List<Path> fileList = new ArrayList<>();
        
        RemoteIterator<LocatedFileStatus> fileIterator = fs.listFiles(dirPath, recursive);
        while (fileIterator.hasNext()) {
            LocatedFileStatus fileStatus = fileIterator.next();
            if (fileStatus.isFile()) {
                fileList.add(fileStatus.getPath());
            }
        }
        
        return fileList;
    }
    
    /**
     * 检查文件是否匹配包含/排除模式
     * 
     * @param path 文件路径
     * @param includePatterns 包含模式列表
     * @param excludePatterns 排除模式列表
     * @return 如果文件应该被处理，则返回true
     */
    public static boolean shouldProcessFile(Path path, List<Pattern> includePatterns, List<Pattern> excludePatterns) {
        String fileName = path.getName();
        
        // 检查排除模式
        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(fileName).matches()) {
                    return false;
                }
            }
        }
        
        // 如果没有包含模式，则包含所有文件
        if (includePatterns == null || includePatterns.isEmpty()) {
            return true;
        }
        
        // 检查包含模式
        for (Pattern pattern : includePatterns) {
            if (pattern.matcher(fileName).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 将字符串模式列表转换为正则表达式模式列表
     * 
     * @param patternStrs 字符串模式列表，以逗号分隔
     * @return 正则表达式模式列表
     */
    public static List<Pattern> compilePatterns(String patternStrs) {
        List<Pattern> patterns = new ArrayList<>();
        
        if (patternStrs != null && !patternStrs.trim().isEmpty()) {
            String[] patternArr = patternStrs.split(",");
            for (String patternStr : patternArr) {
                patternStr = patternStr.trim();
                if (!patternStr.isEmpty()) {
                    // 将通配符模式转换为正则表达式
                    String regex = patternStr
                            .replace(".", "\\.")
                            .replace("*", ".*")
                            .replace("?", ".");
                    patterns.add(Pattern.compile(regex));
                }
            }
        }
        
        return patterns;
    }
} 