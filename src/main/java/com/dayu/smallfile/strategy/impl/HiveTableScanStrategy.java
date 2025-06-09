package com.dayu.smallfile.strategy.impl;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.ScanConfig;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.strategy.ScanStrategy;
import com.dayu.smallfile.utils.DayuStringUtils;
import com.dayu.smallfile.utils.HdfsUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hive表扫描策略实现
 * 扫描Hive表的存储位置，识别需要合并的小文件
 */
public class HiveTableScanStrategy implements ScanStrategy {
    private static final Logger logger = LoggerFactory.getLogger(HiveTableScanStrategy.class);

    @Override
    public List<HiveTblMergePath> scan(Config config) throws Exception {
        List<HiveTblMergePath> mergePaths = new ArrayList<>();
        IMetaStoreClient metaStoreClient = null;

        try {
            // 创建Hive元数据客户端
            metaStoreClient = createMetaStoreClient();
            
            // 获取Hive数据库配置
            List<ScanConfig.HiveDatabase> databases = config.getScan().getHiveDatabases();

            // 目标文件大小，用于判断是否为小文件
            long fileBlockSize = 0l;
            String size = config.getFileBlockSize();
            if (StringUtils.isNotEmpty(size)) {
                fileBlockSize = DayuStringUtils.parseSize(size);
            } else {
                fileBlockSize = HdfsUtils.getHdfsDefaultBlockSize();
            }

            
            // 遍历配置的数据库
            for (ScanConfig.HiveDatabase db : databases) {

                    String dbName = db.getDbName();
                    logger.info("扫描数据库: {}", dbName);
                    String includeStr = db.getIncludes();
                    String excludesStr = db.getExcludes();

                    List<String> includes = new ArrayList<>();
                    if (StringUtils.isNotEmpty(includeStr)) {
                        includes = Arrays.asList(includeStr.split(","));
                    }


                    List<String> excludes = new ArrayList<>();
                    if (StringUtils.isNotEmpty(excludesStr)) {
                        excludes = Arrays.asList(excludesStr.split(","));
                    }

                    // 编译正则表达式
                    List<Pattern> includePatterns = compilePatterns(includes);
                    List<Pattern> excludePatterns = compilePatterns(excludes);

                    try {
                        // 获取数据库中的所有表
                        List<String> allTables = metaStoreClient.getAllTables(dbName);

                        // 根据正则表达式过滤表名
                        List<String> matchedTables = allTables.stream()
                                .filter(tableName -> (includePatterns.isEmpty() || matchesAny(tableName, includePatterns))
                                        && (excludePatterns.isEmpty() || !matchesAny(tableName, excludePatterns)))
                                .toList();

                        logger.info("数据库 {} 中匹配的表数量: {}", dbName, matchedTables.size());

                        // 遍历匹配的表，获取存储位置
                        for (String tableName : matchedTables) {
                            logger.info("处理表: {}.{}", dbName, tableName);

                            try {
                                // 获取表详情
                                Table table = metaStoreClient.getTable(dbName, tableName);
                                String location = table.getSd().getLocation();
                                String inputFormat = table.getSd().getInputFormat();

                                if (location != null && inputFormat != null) {
                                    // 从InputFormat推断文件格式
                                    String fileFormat = getFileFormatFromInputFormat(inputFormat);
                                    if (fileFormat == null) {
                                        logger.error("不支持的文件格式: {}, 表: {}.{}", inputFormat, dbName, tableName);
                                        continue;
                                    }

                                    // 创建HDFS文件系统客户端
                                    Configuration hadoopConf = new Configuration();
                                    FileSystem fs = FileSystem.get(hadoopConf);

                                    // 递归扫描表目录，找到所有根目录
                                    scanTableLocation(fs, new Path(location), fileFormat, fileBlockSize, mergePaths, dbName, tableName);
                                }
                            } catch (Exception e) {
                                logger.warn("处理表 {}.{} 时出错: {}", dbName, tableName, e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("获取数据库 {} 的表列表失败: {}", dbName, e.getMessage());
                    }

            }
            
            // 关闭Hive元数据客户端
            metaStoreClient.close();
            
            logger.info("扫描完成，共找到 {} 个需要合并的路径", mergePaths.size());
        } catch (Exception e) {
            logger.error("扫描Hive表失败", e);
            throw e;
        } finally {
            metaStoreClient.close();
        }
        return mergePaths;
    }
    
    /**
     * 创建Hive元数据客户端
     *
     * @return Hive元数据客户端
     * @throws MetaException 如果创建失败
     */
    private IMetaStoreClient createMetaStoreClient() throws MetaException {
        HiveConf hiveConf = new HiveConf();
        return new HiveMetaStoreClient(hiveConf);
    }
    
    /**
     * 递归扫描表目录，找到所有根目录（只包含文件的目录）
     *
     * @param fs 文件系统
     * @param path 路径
     * @param fileFormat 文件格式
     * @param fileBlockSize 目标文件大小
     * @param mergePaths 合并路径列表
     * @param dbName 数据库名
     * @param tableName 表名
     * @throws IOException 如果读取文件系统失败
     */
    private void scanTableLocation(FileSystem fs, Path path, String fileFormat, long fileBlockSize,
                                  List<HiveTblMergePath> mergePaths, String dbName, String tableName) throws IOException {
        if (!fs.exists(path)) {
            logger.warn("路径不存在: {}", path);
            return;
        }
        
        // 获取目录下的所有文件和子目录
        FileStatus[] statuses = fs.listStatus(path);
        
        // 检查是否为根目录（只包含文件的目录）
        boolean hasSubDir = false;
        boolean hasFiles = false;
        
        for (FileStatus status : statuses) {
            if (status.isDirectory()) {
                hasSubDir = true;
                // 递归扫描子目录
                scanTableLocation(fs, status.getPath(), fileFormat, fileBlockSize, mergePaths, dbName, tableName);
            } else if (!status.getPath().getName().startsWith("_")) {
                hasFiles = true;
            }
        }
        
        // 如果是根目录（有文件且没有子目录），则检查是否需要合并
        if (hasFiles && !hasSubDir) {
            checkAndAddMergePath(fs, path, fileFormat, fileBlockSize, mergePaths, dbName, tableName);
        }
    }
    
    /**
     * 检查目录是否需要合并，如果需要则添加到合并路径列表
     *
     * @param fs 文件系统
     * @param path 目录路径
     * @param fileFormat 文件格式
     * @param fileBlockSize 目标文件大小
     * @param mergePaths 合并路径列表
     * @param dbName 数据库名
     * @param tableName 表名
     * @throws IOException 如果读取文件系统失败
     */
    private void checkAndAddMergePath(FileSystem fs, Path path, String fileFormat, long fileBlockSize,
                                     List<HiveTblMergePath> mergePaths, String dbName, String tableName) throws IOException {
        // 获取目录统计信息
        FileStatus[] files = fs.listStatus(path);
        int fileCount = 0;
        long totalSize = 0;
        
        for (FileStatus status : files) {
            if (!status.isDirectory() && !status.getPath().getName().startsWith("_")) {
                fileCount++;
                totalSize += status.getLen();
            }
        }
        
        // 计算平均文件大小
        long avgFileSize = fileCount > 0 ? totalSize / fileCount : 0;
        
        // 如果平均文件大小小于目标文件大小，则添加到合并路径
        if (fileCount > 0 && avgFileSize < fileBlockSize) {
            HiveTblMergePath mergePath = new HiveTblMergePath(dbName, tableName, path.toString(), fileFormat);
            mergePath.setFileCount(fileCount);

            mergePaths.add(mergePath);
            logger.info("添加合并路径: db={}, table={}, path={}, 格式={}",
                    dbName, tableName, path, fileFormat);
        }
    }
    
    /**
     * 编译正则表达式模式列表
     *
     * @param patterns 正则表达式字符串列表
     * @return 编译后的Pattern列表
     */
    private List<Pattern> compilePatterns(List<String> patterns) {
        List<Pattern> result = new ArrayList<>();
        if (patterns != null && !patterns.isEmpty()) {
            for (String pattern : patterns) {
                if (pattern != null && !pattern.trim().isEmpty()) {
                    result.add(Pattern.compile(pattern.trim()));
                }
            }
        }
        return result;
    }
    
    /**
     * 检查字符串是否匹配任一模式
     *
     * @param str 要检查的字符串
     * @param patterns 模式列表
     * @return 是否匹配
     */
    private boolean matchesAny(String str, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return true; // 如果没有模式，默认匹配
        }
        
        for (Pattern pattern : patterns) {
            if (pattern.matcher(str).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 从InputFormat类名推断文件格式
     *
     * @param inputFormat InputFormat类名
     * @return 文件格式，如果不支持则返回null
     */
    private String getFileFormatFromInputFormat(String inputFormat) {
        if (inputFormat == null) {
            logger.error("InputFormat为空");
            return null;
        }
        
        inputFormat = inputFormat.toLowerCase();
        
        if (inputFormat.contains("parquet")) {
            return "parquet";
        } else if (inputFormat.contains("orc")) {
            return "orc";
        } else if (inputFormat.contains("avro")) {
            return "avro";
        } else if (inputFormat.contains("text") || inputFormat.contains("textfile")) {
            return "text";
        } else {
            logger.error("不支持的文件格式: {}", inputFormat);
            return null;
        }
    }
}