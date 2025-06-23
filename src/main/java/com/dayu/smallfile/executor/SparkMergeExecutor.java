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
        
        HiveTblMergeResult result = initializeResult(path, fileFormat);
        long startTime = System.currentTimeMillis();
        
        SmallFileMergeConfig mergeConfig = config.getSmallFileMerge();

        try {
            if (mergeConfig.isDryRun()) {
                result.markSkipped("试运行模式，跳过合并操作");
                return result;
            }
            
            String tempDir = buildTempDirPath(mergeConfig);
            
            // 执行合并操作
            mergeSmallFiles(path, fileFormat, tempDir);
            
            // 校验合并结果
            boolean isValid = validateMergedData(path, tempDir, fileFormat, result);
            if (!isValid) {
                return result; // 校验失败，直接返回结果
            }
            
            // 更新结果统计信息
            updateResultStatistics(tempDir, result);
            
            // 替换原目录
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
     * 初始化合并结果对象
     */
    private HiveTblMergeResult initializeResult(String path, String fileFormat) {
        HiveTblMergeResult result = new HiveTblMergeResult(
            mergePath.getDbName(), 
            mergePath.getTableName(), 
            path, 
            fileFormat
        );
        result.setBeforeFileCount(mergePath.getFileCount());
        result.setBeforeAvgSize(mergePath.getDirSize() / mergePath.getFileCount());
        return result;
    }
    
    /**
     * 构建临时目录路径
     */
    private String buildTempDirPath(SmallFileMergeConfig mergeConfig) {
        return mergeConfig.getTempDir() + "/" + mergePath.getTargetPath();
    }
    
    /**
     * 执行小文件合并操作
     */
    private void mergeSmallFiles(String path, String fileFormat, String tempDir) {
        logger.info("合并路径: {}, 写入临时目录: {}", path, tempDir);
        
        Dataset<Row> df = spark.read().format(fileFormat).load(path);
        
        df.repartition(mergePath.getTargetNum())
          .write()
          .format(fileFormat)
          .mode("overwrite")
          .save(tempDir);
    }
    
    /**
     * 校验合并后的数据
     * @return 校验是否通过
     */
    private boolean validateMergedData(String path, String tempDir, String fileFormat, HiveTblMergeResult result) throws IOException {
        logger.info("开始校验合并后的数据: {}", tempDir);
        
        // 读取原路径和临时路径的数据
        Dataset<Row> originalDf = spark.read().format(fileFormat).load(path);
        Dataset<Row> tempDf = spark.read().format(fileFormat).load(tempDir);
        
        // 校验数据量
        if (!validateDataCount(originalDf, tempDf, tempDir, result)) {
            return false;
        }
        
        // 校验数据内容
        if (!validateDataContent(originalDf, tempDf, tempDir, result)) {
            return false;
        }
        
        logger.info("数据校验成功 - 合并前后数据完全一致");
        return true;
    }
    
    /**
     * 校验数据量
     * @return 校验是否通过
     */
    private boolean validateDataCount(Dataset<Row> originalDf, Dataset<Row> tempDf, String tempDir, HiveTblMergeResult result) throws IOException {
        long originalCount = originalDf.count();
        long tempCount = tempDf.count();
        
        logger.info("数据校验 - 原始数据量: {}, 合并后数据量: {}", originalCount, tempCount);
        
        if (originalCount != tempCount) {
            String errorMsg = String.format("数据校验失败 - 数据量不一致: 原始数据量 %d, 合并后数据量 %d", 
                                          originalCount, tempCount);
            logger.error(errorMsg);
            
            cleanupTempDir(tempDir);
            result.setErrorMessage(errorMsg);
            return false;
        }
        
        return true;
    }
    
    /**
     * 校验数据内容
     * @return 校验是否通过
     */
    private boolean validateDataContent(Dataset<Row> originalDf, Dataset<Row> tempDf, String tempDir, HiveTblMergeResult result) throws IOException {
        long minusCount1 = originalDf.except(tempDf).count();
        long minusCount2 = tempDf.except(originalDf).count();
        
        logger.info("数据校验 - minus比对结果: 原始数据-合并数据={}, 合并数据-原始数据={}", 
                   minusCount1, minusCount2);
        
        if (minusCount1 > 0 || minusCount2 > 0) {
            String errorMsg = String.format("数据校验失败 - 数据内容不一致: 原始数据-合并数据=%d, 合并数据-原始数据=%d", 
                                          minusCount1, minusCount2);
            logger.error(errorMsg);
            
            cleanupTempDir(tempDir);
            result.setErrorMessage(errorMsg);
            return false;
        }
        
        return true;
    }
    
    /**
     * 清理临时目录
     */
    private void cleanupTempDir(String tempDir) throws IOException {
        logger.info("删除临时目录: {}", tempDir);
        fs.delete(new Path(tempDir), true);
    }
    
    /**
     * 更新结果统计信息
     */
    private void updateResultStatistics(String tempDir, HiveTblMergeResult result) throws IOException {
        ContentSummary contentSummary = fs.getContentSummary(new Path(tempDir));
        result.setAfterFileCount(contentSummary.getFileCount());
        result.setAfterAvgSize(contentSummary.getLength() / contentSummary.getFileCount());
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
            deleteOriginalPath(fs, originalPath);
        } else {
            backupOriginalPath(fs, originalPath, mergeConfig);
        }
        
        // 将临时目录重命名为原目录
        logger.info("将临时目录重命名为原目录: {} -> {}", tempPath, originalPath);
        fs.rename(tempPath, originalPath);
    }
    
    /**
     * 删除原目录
     */
    private void deleteOriginalPath(FileSystem fs, Path originalPath) throws IOException {
        logger.info("删除原目录: {}", originalPath);
        if (fs.exists(originalPath)) {
            fs.delete(originalPath, true);
        }
    }
    
    /**
     * 备份原目录
     */
    private void backupOriginalPath(FileSystem fs, Path originalPath, SmallFileMergeConfig mergeConfig) throws IOException {
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
} 