package com.dayu.smallfile.executor;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.SmallFileMergeConfig;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.model.HiveTblMergeResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Spark合并执行器
 * 使用Spark执行小文件合并任务
 */
public class SparkMergeExecutor implements Callable<HiveTblMergeResult> {
    private static final Logger logger = LoggerFactory.getLogger(SparkMergeExecutor.class);
    
    private final HiveTblMergePath mergePath;
    private final Config config;
    private final SparkSession spark;
    private final FileSystem fs;
    
    public SparkMergeExecutor(HiveTblMergePath mergePath, Config config, SparkSession spark, FileSystem fs) {
        this.mergePath = mergePath;
        this.config = config;
        this.spark = spark;
        this.fs = fs;
    }
    
    @Override
    public HiveTblMergeResult call() throws Exception {
        String path = mergePath.getTargetPath();
        String fileFormat = mergePath.getFileFormat();
        
        HiveTblMergeResult result = new HiveTblMergeResult(mergePath.getDbName(), mergePath.getTableName(), path, fileFormat);
        result.setBeforeFileCount(mergePath.getFileCount());
        result.setBeforeAvgSize(mergePath.getDirSize() / mergePath.getFileCount());
        
        long startTime = System.currentTimeMillis();
        
        // 获取小文件合并配置
        SmallFileMergeConfig mergeConfig = config.getSmallFileMerge();

        try {
            // 如果是试运行模式，跳过实际合并操作
            if (mergeConfig.isDryRun()) {
                result.markSkipped("试运行模式，跳过合并操作");
                return result;
            }
            
            // 构建临时目录路径
            String tempDir = mergeConfig.getTempDir() + "/" + mergePath.getTargetPath();
            
            // 读取源数据
            Dataset<Row> df = spark.read().format(fileFormat).load(path);
            
            // 重新分区并写入临时目录
            logger.info("合并路径: {}, 写入临时目录: {}", path, tempDir);
            df.repartition(mergePath.getTargetNum())
              .write()
              .format(fileFormat)
              .mode("overwrite")
              .save(tempDir);
            
            // 获取合并后的文件统计信息
            ContentSummary contentSummary = fs.getContentSummary(new Path(tempDir));

            result.setAfterFileCount(contentSummary.getFileCount());
            result.setAfterAvgSize(contentSummary.getLength() / contentSummary.getFileCount());
            
            // 如果不是试运行模式，替换原目录
            if (!mergeConfig.isDryRun()) {
                replaceOriginalPath(fs, new Path(tempDir), new Path(path), mergeConfig);
            }
            
            result.markSuccess();
        } catch (Exception e) {
            logger.error("合并路径 {} 失败: {}", path, e.getMessage(), e);
            result.setErrorMessage(e.getMessage());
        }
        
        result.setDuration(System.currentTimeMillis() - startTime);
        return result;
    }

    
    /**
     * 替换原目录
     *
     * @param fs 文件系统
     * @param tempPath 临时目录路径
     * @param originalPath 原目录路径
     * @param mergeConfig 小文件合并配置
     * @throws IOException 如果操作文件系统失败
     */
    private void replaceOriginalPath(FileSystem fs, Path tempPath, Path originalPath, SmallFileMergeConfig mergeConfig) throws IOException {
        if (mergeConfig.isCleanup()) {
            // 如果配置为清理原文件，则直接删除原目录
            logger.info("删除原目录: {}", originalPath);
            if (fs.exists(originalPath)) {
                fs.delete(originalPath, true);
            }
        } else {
            // 否则将原目录移动到备份目录
            String backupDir = mergeConfig.getBackupDir();
            if (StringUtils.isEmpty(backupDir)) {
                throw new IOException("未配置备份目录，无法执行备份操作");
            }
            
            Path backupPath = new Path(backupDir + "/" + originalPath.getName() + "_" + System.currentTimeMillis());
            logger.info("备份原目录: {} -> {}", originalPath, backupPath);
            if (fs.exists(originalPath)) {
                fs.rename(originalPath, backupPath);
            }
        }
        
        // 将临时目录重命名为原目录
        logger.info("将临时目录重命名为原目录: {} -> {}", tempPath, originalPath);
        fs.rename(tempPath, originalPath);
    }
} 