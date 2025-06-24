package com.dayu.smallfile.strategy.impl;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.SmallFileMergeConfig;
import com.dayu.smallfile.model.HiveDatabase;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.strategy.ScanStrategy;
import com.dayu.smallfile.utils.DayuStringUtils;
import com.dayu.smallfile.utils.HdfsUtils;
import com.dayu.smallfile.utils.ProgressBarUtil;
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
    private static final int PROGRESS_REPORT_INTERVAL = 10;
    
    private ProgressBarUtil progressBar;
    private int directoriesScanned = 0;
    private int totalDirectories = 0;

    @Override
    public List<HiveTblMergePath> scan(Config config) throws Exception {
        List<HiveTblMergePath> mergePaths = new ArrayList<>();
        IMetaStoreClient metaStoreClient = null;

        try {
            metaStoreClient = createMetaStoreClient();
            SmallFileMergeConfig mergeConfig = config.getSmallFileMerge();
            List<HiveDatabase> databases = mergeConfig.getHiveDatabases();
            long fileBlockSize = getTargetFileSize(mergeConfig);

            // 第一步：确定需要处理的表
            Map<String, List<String>> dbTableMap = identifyTablesToProcess(metaStoreClient, databases);
            int totalMatchedTables = countTotalTables(dbTableMap);
            
            if (totalMatchedTables == 0) {
                logger.warn("没有找到符合条件的表，扫描结束");
                return mergePaths;
            }
            
            // 第二步：处理符合条件的表
            processTables(metaStoreClient, dbTableMap, fileBlockSize, mergePaths);
            
            logger.info("扫描完成，共找到 {} 个需要合并的路径", mergePaths.size());
            return mergePaths;
        } catch (Exception e) {
            logger.error("扫描Hive表失败", e);
            throw e;
        } finally {
            closeClientSafely(metaStoreClient);
        }
    }
    
    private long getTargetFileSize(SmallFileMergeConfig mergeConfig) throws Exception {
        String size = mergeConfig.getHdfsBlockSize();
        return StringUtils.isNotEmpty(size) ? 
               DayuStringUtils.parseSize(size) : 
               HdfsUtils.getHdfsDefaultBlockSize();
    }
    
    private Map<String, List<String>> identifyTablesToProcess(IMetaStoreClient metaStoreClient, 
                                                             List<HiveDatabase> databases) {
        Map<String, List<String>> dbTableMap = new HashMap<>();
        
        for (HiveDatabase db : databases) {
            String dbName = db.getDbName();
            try {
                if (!databaseExists(metaStoreClient, dbName)) {
                    continue;
                }

                List<Pattern> includePatterns = compilePatterns(getIncludesList(db));
                List<Pattern> excludePatterns = compilePatterns(getExcludesList(db));
                List<String> allTables = metaStoreClient.getAllTables(dbName);
                List<String> matchedTables = filterTablesByPattern(allTables, includePatterns, excludePatterns);

                if (!matchedTables.isEmpty()) {
                    dbTableMap.put(dbName, matchedTables);
                    logger.info("数据库 {} 中符合条件的表数量: {}", dbName, matchedTables.size());
                }
            } catch (Exception e) {
                logger.error("获取数据库 {} 的表信息失败: {}", dbName, e.getMessage());
            }
        }
        
        return dbTableMap;
    }
    
    private boolean databaseExists(IMetaStoreClient client, String dbName) throws Exception {
        List<String> allDatabases = client.getAllDatabases();
        if (!allDatabases.contains(dbName)) {
            logger.error("数据库 {} 在Metastore中不存在，跳过处理", dbName);
            return false;
        }
        return true;
    }
    
    private List<String> getIncludesList(HiveDatabase db) {
        String includeStr = db.getIncludes();
        return StringUtils.isNotEmpty(includeStr) ? 
               Arrays.asList(includeStr.split(",")) : 
               Collections.emptyList();
    }
    
    private List<String> getExcludesList(HiveDatabase db) {
        String excludesStr = db.getExcludes();
        return StringUtils.isNotEmpty(excludesStr) ? 
               Arrays.asList(excludesStr.split(",")) : 
               Collections.emptyList();
    }
    
    private List<String> filterTablesByPattern(List<String> allTables, 
                                              List<Pattern> includePatterns, 
                                              List<Pattern> excludePatterns) {
        return allTables.stream()
                .filter(tableName -> (includePatterns.isEmpty() || matchesAny(tableName, includePatterns))
                        && (excludePatterns.isEmpty() || !matchesAny(tableName, excludePatterns)))
                .collect(Collectors.toList());
    }
    
    private int countTotalTables(Map<String, List<String>> dbTableMap) {
        return dbTableMap.values().stream().mapToInt(List::size).sum();
    }
    
    private void processTables(IMetaStoreClient metaStoreClient, 
                              Map<String, List<String>> dbTableMap, 
                              long fileBlockSize,
                              List<HiveTblMergePath> mergePaths) {
        int totalTables = countTotalTables(dbTableMap);
        progressBar = new ProgressBarUtil(totalTables, "扫描Hive表");
        int processedTables = 0;
        
        for (Map.Entry<String, List<String>> entry : dbTableMap.entrySet()) {
            String dbName = entry.getKey();
            List<String> tables = entry.getValue();
            
            logger.info("开始处理数据库: {}, 表数量: {}", dbName, tables.size());
            
            for (String tableName : tables) {
                try {
                    processTable(metaStoreClient, dbName, tableName, fileBlockSize, mergePaths);
                    progressBar.update(true);
                } catch (Exception e) {
                    logger.warn("处理表 {}.{} 时出错: {}", dbName, tableName, e.getMessage());
                    progressBar.update(false);
                }
                processedTables++;
            }
        }
        
        progressBar.complete();
    }
    
    private void processTable(IMetaStoreClient metaStoreClient, 
                             String dbName, 
                             String tableName, 
                             long fileBlockSize,
                             List<HiveTblMergePath> mergePaths) throws Exception {
        Table table = metaStoreClient.getTable(dbName, tableName);
        String location = table.getSd().getLocation();
        String inputFormat = table.getSd().getInputFormat();

        if (location == null || inputFormat == null) {
            logger.warn("表 {}.{} 的位置或输入格式为空", dbName, tableName);
            return;
        }
        
        String fileFormat = getFileFormatFromInputFormat(inputFormat);
        if (fileFormat == null) {
            logger.error("不支持的文件格式: {}, 表: {}.{}", inputFormat, dbName, tableName);
            return;
        }

        FileSystem fs = FileSystem.get(new Configuration());
        Path tablePath = new Path(location);
        
        // 重置目录计数器并预计算目录数量
        resetDirectoryCounters();
        countDirectories(fs, tablePath);
        
        // 扫描表目录
        scanTableLocation(fs, tablePath, fileFormat, fileBlockSize, mergePaths, dbName, tableName);
    }
    
    private void resetDirectoryCounters() {
        directoriesScanned = 0;
        totalDirectories = 0;
    }
    
    private void countDirectories(FileSystem fs, Path path) throws IOException {
        if (!fs.exists(path) || isHiddenDirectory(path)) {
            return;
        }
        
        totalDirectories++;
        
        for (FileStatus status : fs.listStatus(path)) {
            if (status.isDirectory() && !isHiddenPath(status.getPath())) {
                countDirectories(fs, status.getPath());
            }
        }
    }
    
    private boolean isHiddenDirectory(Path path) {
        return path.getName().startsWith(".");
    }
    
    private boolean isHiddenPath(Path path) {
        return path.getName().startsWith(".");
    }
    
    private IMetaStoreClient createMetaStoreClient() throws MetaException {
        return new HiveMetaStoreClient(new HiveConf());
    }
    
    private void closeClientSafely(IMetaStoreClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("关闭Hive元数据客户端时出错: {}", e.getMessage());
            }
        }
    }
    
    private void scanTableLocation(FileSystem fs, Path path, String fileFormat, long fileBlockSize,
                                  List<HiveTblMergePath> mergePaths, String dbName, String tableName) throws IOException {
        if (!fs.exists(path) || isHiddenDirectory(path)) {
            return;
        }
        
        updateDirectoryScanProgress();
        
        FileStatus[] statuses = fs.listStatus(path);
        boolean hasSubDir = false;
        boolean hasFiles = false;
        
        for (FileStatus status : statuses) {
            if (status.isDirectory()) {
                if (!isHiddenPath(status.getPath())) {
                    hasSubDir = true;
                    scanTableLocation(fs, status.getPath(), fileFormat, fileBlockSize, mergePaths, dbName, tableName);
                }
            } else if (!status.getPath().getName().startsWith("_")) {
                hasFiles = true;
            }
        }
        
        if (hasFiles && !hasSubDir) {
            checkAndAddMergePath(fs, path, fileFormat, fileBlockSize, mergePaths, dbName, tableName);
        }
    }
    
    private void updateDirectoryScanProgress() {
        directoriesScanned++;
        
        if (totalDirectories > 0 && directoriesScanned % PROGRESS_REPORT_INTERVAL == 0) {
            int progressPercentage = (int)((directoriesScanned * 100.0) / totalDirectories);
            logger.debug("目录扫描进度: {}/{} ({}%)", directoriesScanned, totalDirectories, progressPercentage);
        }
    }
    
    private void checkAndAddMergePath(FileSystem fs, Path path, String fileFormat, long fileBlockSize,
                                     List<HiveTblMergePath> mergePaths, String dbName, String tableName) throws IOException {
        FileStatistics stats = calculateDirectoryStatistics(fs, path);
        
        if (shouldMergeFiles(stats.fileCount, stats.avgFileSize, fileBlockSize)) {
            HiveTblMergePath mergePath = new HiveTblMergePath(dbName, tableName, path.toString(), fileFormat);
            mergePath.setFileCount(stats.fileCount);
            mergePath.setDirSize(stats.totalSize);
            int targetNum = (int) Math.ceil((double) stats.totalSize / fileBlockSize);
            targetNum = Math.max(1, targetNum);
            mergePath.setTargetNum(targetNum);
            mergePaths.add(mergePath);
            
            logger.info("添加合并路径: db={}, table={}, path={}, 格式={}, 文件数={}, 总大小={}",
                    dbName, tableName, path, fileFormat, stats.fileCount, stats.totalSize);
        }
    }
    
    private FileStatistics calculateDirectoryStatistics(FileSystem fs, Path path) throws IOException {
        FileStatus[] files = fs.listStatus(path);
        int fileCount = 0;
        long totalSize = 0;
        
        for (FileStatus status : files) {
            if (!status.isDirectory() && !status.getPath().getName().startsWith("_")) {
                fileCount++;
                totalSize += status.getLen();
            }
        }
        
        long avgFileSize = fileCount > 0 ? totalSize / fileCount : 0;
        return new FileStatistics(fileCount, totalSize, avgFileSize);
    }
    
    private boolean shouldMergeFiles(int fileCount, long avgFileSize, long targetSize) {
        return fileCount >= 2 && avgFileSize < targetSize;
    }
    
    private List<Pattern> compilePatterns(List<String> patterns) {
        return patterns.stream()
                .filter(pattern -> pattern != null && !pattern.trim().isEmpty())
                .map(pattern -> Pattern.compile(pattern.trim()))
                .collect(Collectors.toList());
    }
    
    private boolean matchesAny(String str, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        
        return patterns.stream().anyMatch(pattern -> pattern.matcher(str).matches());
    }
    
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
    
    private static class FileStatistics {
        final int fileCount;
        final long totalSize;
        final long avgFileSize;
        
        FileStatistics(int fileCount, long totalSize, long avgFileSize) {
            this.fileCount = fileCount;
            this.totalSize = totalSize;
            this.avgFileSize = avgFileSize;
        }
    }
}