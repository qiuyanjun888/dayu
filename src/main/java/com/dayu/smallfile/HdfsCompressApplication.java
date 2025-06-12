package com.dayu.smallfile;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.HdfsCompressConfig;
import com.dayu.smallfile.executor.HdfsCompressExecutor;
import com.dayu.smallfile.model.HdfsCompressResult;
import com.dayu.smallfile.report.HdfsCompressReportGenerator;
import com.dayu.smallfile.utils.CommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * HDFS压缩应用主类
 */
public class HdfsCompressApplication {
    private static final Logger logger = LoggerFactory.getLogger(HdfsCompressApplication.class);
    
    private final Config config;
    private List<HdfsCompressResult> results;
    
    public HdfsCompressApplication() {
        this.config = new Config();
    }
    
    /**
     * 应用入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        HdfsCompressApplication app = new HdfsCompressApplication();
        try {
            app.run(args);
        } catch (Exception e) {
            logger.error("应用运行失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 运行应用
     *
     * @param args 命令行参数
     * @throws Exception 如果运行失败
     */
    public void run(String[] args) throws Exception {
        // 解析命令行参数
        CommandLineParser cmdParser = new CommandLineParser();
        CommandLine cmd;
        try {
            cmd = cmdParser.parse(args);
        } catch (org.apache.commons.cli.ParseException e) {
            logger.error("解析命令行参数失败: {}", e.getMessage());
            cmdParser.printHelp();
            throw e;
        }

        // 如果指定了帮助选项，则显示帮助信息并退出
        if (cmd.hasOption("h")) {
            cmdParser.printHelp();
            return;
        }
        
        // 加载配置文件
        String configPath = cmd.getOptionValue("conf");
        if (StringUtils.isEmpty(configPath)) {
            logger.error("缺少配置文件，无法执行程序");
            return;
        }
        logger.info("使用配置文件: {}", configPath);
        config.load(configPath);
        
        // 检查HDFS压缩配置
        HdfsCompressConfig hdfsCompressConfig = config.getHdfsCompress();
        if (hdfsCompressConfig == null || hdfsCompressConfig.getPaths() == null || hdfsCompressConfig.getPaths().isEmpty()) {
            logger.error("HDFS压缩配置为空或无效");
            return;
        }

        executeJob();
    }
    
    /**
     * 执行作业
     *
     * @throws Exception 如果执行失败
     */
    private void executeJob() throws Exception {
        try {
            // 创建执行器
            logger.info("开始HDFS文件压缩任务");
            HdfsCompressExecutor executor = new HdfsCompressExecutor(config.getHdfsCompress());
            
            // 执行压缩
            results = executor.execute();
            logger.info("压缩任务完成，结果数: {}", results.size());
            
            // 生成报告
            File reportFile = generateReport();
            logger.info("报告已生成: {}", reportFile != null ? reportFile.getAbsolutePath() : "生成失败");
        } catch (Exception e) {
            logger.error("作业执行失败", e);
            throw e;
        }
    }
    
    /**
     * 生成报告
     *
     * @return 生成的报告文件
     */
    public File generateReport() {
        if (results == null || results.isEmpty()) {
            logger.warn("没有压缩结果，跳过报告生成");
            return null;
        }
        
        try {
            HdfsCompressReportGenerator reportGenerator = new HdfsCompressReportGenerator(config.getReport(), results);
            return reportGenerator.generateReport();
        } catch (Exception e) {
            logger.error("生成报告失败", e);
            return null;
        }
    }
} 