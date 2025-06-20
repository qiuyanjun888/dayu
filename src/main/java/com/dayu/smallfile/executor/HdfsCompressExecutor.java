package com.dayu.smallfile.executor;

import com.dayu.smallfile.config.HdfsCompressConfig;
import com.dayu.smallfile.config.HdfsCompressPathConfig;
import com.dayu.smallfile.model.HdfsCompressResult;
import com.dayu.smallfile.model.HdfsCompressTask;
import com.dayu.smallfile.utils.DayuStringUtils;
import com.dayu.smallfile.utils.HdfsUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * HDFS压缩执行器
 */
public class HdfsCompressExecutor {
    private static final Logger logger = LoggerFactory.getLogger(HdfsCompressExecutor.class);
    
    private final HdfsCompressConfig config;
    private final ExecutorService producerExecutor;
    private final ExecutorService consumerExecutor;
    private final BlockingQueue<HdfsCompressTask> taskQueue;
    private final Map<String, HdfsCompressResult> resultMap;
    private final FileSystem fs;
    
    /**
     * 构造函数
     * 
     * @param config HDFS压缩配置
     * @throws IOException 如果初始化失败
     */
    public HdfsCompressExecutor(HdfsCompressConfig config) throws IOException {
        this.config = config;
        this.fs = HdfsUtils.getFileSystem();
        
        // 初始化线程池
        this.producerExecutor = Executors.newFixedThreadPool(config.getProducerThreads());
        this.consumerExecutor = Executors.newFixedThreadPool(config.getConsumerThreads());
        
        // 初始化任务队列
        this.taskQueue = new LinkedBlockingQueue<>();
        
        // 初始化结果映射
        this.resultMap = new HashMap<>();
    }
    
    /**
     * 执行压缩任务
     * 
     * @return 压缩结果列表
     * @throws Exception 如果执行失败
     */
    public List<HdfsCompressResult> execute() throws Exception {
        try {
            // 启动生产者线程
            List<Future<?>> producerFutures = new ArrayList<>();
            for (HdfsCompressPathConfig pathConfig : config.getPaths()) {
                // 创建结果对象
                HdfsCompressResult result = new HdfsCompressResult(pathConfig.getRootPath());
                resultMap.put(pathConfig.getRootPath(), result);
                
                // 提交生产者任务
                producerFutures.add(producerExecutor.submit(() -> {
                    try {
                        scanFiles(pathConfig, result);
                    } catch (Exception e) {
                        logger.error("扫描文件失败: " + pathConfig.getRootPath(), e);
                    }
                    return null;
                }));
            }
            
            // 启动消费者线程
            List<Future<?>> consumerFutures = new ArrayList<>();
            for (int i = 0; i < config.getConsumerThreads(); i++) {
                consumerFutures.add(consumerExecutor.submit(this::processFiles));
            }
            
            // 等待所有生产者完成
            for (Future<?> future : producerFutures) {
                future.get();
            }
            
            // 放置结束标记
            for (int i = 0; i < config.getConsumerThreads(); i++) {
                taskQueue.put(new HdfsCompressTask("END", "END", 0));
            }
            
            // 等待所有消费者完成
            for (Future<?> future : consumerFutures) {
                future.get();
            }
            
            // 完成所有结果
            for (HdfsCompressResult result : resultMap.values()) {
                result.complete();
            }
            
            return new ArrayList<>(resultMap.values());
        } finally {
            // 关闭线程池
            producerExecutor.shutdown();
            consumerExecutor.shutdown();
        }
    }
    
    /**
     * 扫描文件
     * 
     * @param pathConfig 路径配置
     * @param result 压缩结果
     * @throws IOException 如果扫描失败
     */
    private void scanFiles(HdfsCompressPathConfig pathConfig, HdfsCompressResult result) throws IOException, InterruptedException {
        logger.info("开始扫描路径: {}", pathConfig.getRootPath());
        
        // 解析阈值
        long threshold = DayuStringUtils.parseSize(pathConfig.getThreshold());
        
        // 编译正则表达式
        List<Pattern> includePatterns = HdfsUtils.compilePatterns(pathConfig.getIncludes());
        List<Pattern> excludePatterns = HdfsUtils.compilePatterns(pathConfig.getExcludes());
        
        // 扫描文件
        Path rootPath = new Path(pathConfig.getRootPath());
        List<Path> files = HdfsUtils.listFiles(fs, rootPath, pathConfig.isRecursive());
        
        int totalFiles = 0;
        int eligibleFiles = 0;
        
        for (Path file : files) {
            totalFiles++;
            
            // 获取文件状态
            FileStatus status = fs.getFileStatus(file);
            long fileSize = status.getLen();
            
            // 检查文件是否需要压缩
            boolean shouldCompress = fileSize >= threshold && 
                    HdfsUtils.shouldProcessFile(file, includePatterns, excludePatterns);
            
            if (shouldCompress) {
                eligibleFiles++;
                
                // 创建压缩任务
                HdfsCompressTask task = new HdfsCompressTask(pathConfig.getRootPath(), file.toString(), fileSize);
                result.addTask(task);
                
                // 添加到任务队列
                taskQueue.put(task);
                
                logger.debug("添加压缩任务: {}", file);
            }
        }
        
        logger.info("路径扫描完成: {}, 总文件数: {}, 符合压缩条件的文件数: {}", 
                pathConfig.getRootPath(), totalFiles, eligibleFiles);
    }
    
    /**
     * 处理文件（消费者）
     */
    private void processFiles() {
        while (true) {
            try {
                // 获取任务
                HdfsCompressTask task = taskQueue.take();
                
                // 检查结束标记
                if ("END".equals(task.getRootPath()) && "END".equals(task.getFilePath())) {
                    break;
                }
                
                // 获取结果对象
                HdfsCompressResult result = resultMap.get(task.getRootPath());
                if (result == null) {
                    logger.error("找不到结果对象: {}", task.getRootPath());
                    continue;
                }
                
                // 标记任务开始处理
                task.markProcessing();
                result.taskStarted();
                
                logger.info("开始压缩文件: {}", task.getFilePath());
                
                try {
                    // 记录开始时间
                    long startTime = System.currentTimeMillis();
                    
                    // 压缩文件
                    Path inputPath = new Path(task.getFilePath());
                    Path outputPath = HdfsUtils.compressFile(fs, inputPath);
                    
                    // 获取压缩后的文件大小
                    FileStatus outputStatus = fs.getFileStatus(outputPath);
                    long compressedSize = outputStatus.getLen();
                    
                    // 删除原始文件
                    boolean deleted = fs.delete(inputPath, false);
                    if (!deleted) {
                        logger.warn("无法删除原始文件: {}", inputPath);
                    }
                    
                    // 记录结束时间和处理时间
                    long endTime = System.currentTimeMillis();
                    long processingTime = endTime - startTime;
                    
                    // 标记任务成功
                    task.markSuccess(compressedSize, processingTime);
                    
                    logger.info("文件压缩成功: {}, 原始大小: {}, 压缩后大小: {}, 压缩率: {}, 耗时: {} ms", 
                            task.getFilePath(), task.getOriginalSize(), compressedSize, 
                            String.format("%.2f%%", (1.0 - (double)compressedSize/task.getOriginalSize()) * 100),
                            processingTime);
                } catch (Exception e) {
                    // 标记任务失败
                    task.markFailed(e.getMessage());
                    logger.error("文件压缩失败: " + task.getFilePath(), e);
                } finally {
                    // 标记任务完成
                    result.taskCompleted(task);
                    
                    // 打印进度
                    logProgress(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("处理文件时发生错误", e);
            }
        }
    }
    
    /**
     * 记录进度
     * 
     * @param result 压缩结果
     */
    private void logProgress(HdfsCompressResult result) {
        logger.info("路径: {}, 进度: 总文件数={}, 进行中={}, 已完成={}, 成功={}, 失败={}, 运行时间={} 秒", 
                result.getRootPath(), 
                result.getTotalFiles().get(),
                result.getProcessingFiles().get(),
                result.getCompletedFiles().get(),
                result.getSuccessFiles().get(),
                result.getFailedFiles().get(),
                result.getElapsedTime() / 1000);
    }
} 