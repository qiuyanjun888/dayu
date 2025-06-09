package com.dayu.smallfile;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.model.HiveTblMergeResult;
import com.dayu.smallfile.plugin.PluginManager;
import com.dayu.smallfile.report.ReportGenerator;
import com.dayu.smallfile.strategy.MergeStrategy;
import com.dayu.smallfile.strategy.ScanStrategy;
import com.dayu.smallfile.utils.CommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * 小文件合并应用主类
 */
public class SmallFileMergeApplication {
    private static final Logger logger = LoggerFactory.getLogger(SmallFileMergeApplication.class);
    
    private Config config;
    private PluginManager pluginManager;
    private List<HiveTblMergeResult> results;
    
    public SmallFileMergeApplication() {
        this.config = new Config();
        this.pluginManager = new PluginManager();
    }
    
    /**
     * 应用入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SmallFileMergeApplication app = new SmallFileMergeApplication();
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
        
        // 加载插件JAR
        String pluginJar = cmd.getOptionValue("jar", "");
        if (pluginJar != null && !pluginJar.isEmpty()) {
            logger.info("加载插件JAR: {}", pluginJar);
            pluginManager.loadPluginJar(pluginJar);
        }
        
        // 注册策略类
        pluginManager.registerStrategy(config.getScan().getStrategyClass(), "scan");
        pluginManager.registerStrategy(config.getMerge().getStrategyClass(), "merge");


        executeJob();

    }
    
    /**
     * 执行作业
     *
     * @throws Exception 如果执行失败
     */
    private void executeJob() throws Exception {
        try {
            // 执行扫描
            logger.info("开始扫描小文件路径");
            ScanStrategy scanStrategy = pluginManager.getScanStrategy();
            List<HiveTblMergePath> mergePaths = scanStrategy.scan(config);
            logger.info("扫描完成，找到 {} 个需要合并的路径", mergePaths.size());
            
            // 执行合并
            logger.info("开始执行合并");
            MergeStrategy mergeStrategy = pluginManager.getMergeStrategy();
            results = mergeStrategy.merge(mergePaths, config);
            logger.info("合并完成，结果数: {}", results.size());
            
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
            logger.warn("没有合并结果，跳过报告生成");
            return null;
        }
        
        try {
            ReportGenerator reportGenerator = new ReportGenerator(config.getReport(), results);
            return reportGenerator.generateReport();
        } catch (Exception e) {
            logger.error("生成报告失败", e);
            return null;
        }
    }
} 