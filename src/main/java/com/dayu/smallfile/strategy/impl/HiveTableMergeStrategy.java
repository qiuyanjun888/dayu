package com.dayu.smallfile.strategy.impl;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.executor.SparkMergeExecutor;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.model.HiveTblMergeResult;
import com.dayu.smallfile.strategy.MergeStrategy;
import com.dayu.smallfile.utils.DayuStringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * 默认文件合并策略实现
 * 使用Spark并行执行小文件合并
 */
public class HiveTableMergeStrategy implements MergeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(HiveTableMergeStrategy.class);

    @Override
    public List<HiveTblMergeResult> merge(List<HiveTblMergePath> mergePaths, Config config) throws Exception {
        List<HiveTblMergeResult> results = new ArrayList<>();
        
        if (mergePaths == null || mergePaths.isEmpty()) {
            logger.info("没有需要合并的路径");
            return results;
        }
        
        // 记录开始时间
        Instant startTime = Instant.now();
        
        // 任务统计计数器
        int totalTasks = mergePaths.size();
        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicInteger successTasks = new AtomicInteger(0);
        AtomicInteger failedTasks = new AtomicInteger(0);
        
        SparkSession spark = null;
        FileSystem fs = null;
        try {
            // 创建Spark会话
            spark = SparkSession.builder().enableHiveSupport().getOrCreate();
            Configuration conf = new Configuration();
            fs = FileSystem.get(conf);
            // 创建线程池
            int threadCount = config.getThreadPool().getQueueSize();
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            
            logger.info("使用 {} 个线程执行合并任务", threadCount);
            
            // 提交合并任务
            List<Future<HiveTblMergeResult>> futures = new ArrayList<>();
            for (HiveTblMergePath mergePath : mergePaths) {
                SparkMergeExecutor executor = new SparkMergeExecutor(mergePath, config, spark, fs);
                futures.add(executorService.submit(executor));
            }
            
            // 收集结果
            for (Future<HiveTblMergeResult> future : futures) {
                try {
                    HiveTblMergeResult result = future.get();
                    results.add(result);
                    successTasks.incrementAndGet();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("合并任务执行失败", e);
                    failedTasks.incrementAndGet();
                } finally {
                    int completed = completedTasks.incrementAndGet();
                    int remaining = totalTasks - completed;
                    Duration duration = Duration.between(startTime, Instant.now());
                    String runTime = String.format("%02d:%02d:%02d", 
                            duration.toHours(), 
                            duration.toMinutesPart(), 
                            duration.toSecondsPart());
                    
                    logger.info("任务进度 - 总任务数: {}, 已完成: {}, 成功: {}, 失败: {}, 剩余: {}, 运行时间: {}", 
                            totalTasks, completed, successTasks.get(), failedTasks.get(), remaining, runTime);
                }
            }
            
            // 关闭线程池
            executorService.shutdown();
            long timeout = DayuStringUtils.parseTime(config.getThreadPool().getTimeout());
            if (!executorService.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                logger.warn("线程池未能在指定时间内关闭");
                executorService.shutdownNow();
            }
        } catch (Exception e) {
            logger.error("合并过程中发生错误", e);
            throw e;
        } finally {
            // 最终任务统计
            Duration totalDuration = Duration.between(startTime, Instant.now());
            String totalRunTime = String.format("%02d:%02d:%02d", 
                    totalDuration.toHours(), 
                    totalDuration.toMinutesPart(), 
                    totalDuration.toSecondsPart());
            
            logger.info("任务完成统计 - 总任务数: {}, 已完成: {}, 成功: {}, 失败: {}, 剩余: 0, 总运行时间: {}", 
                    totalTasks, completedTasks.get(), successTasks.get(), failedTasks.get(), totalRunTime);
            
            // 关闭Spark会话
            if (spark != null) {
                spark.close();
            }
            if (fs != null) {
                fs.close();
            }
        }
        return results;
    }
} 