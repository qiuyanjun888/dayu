package com.dayu.smallfile;

import com.dayu.smallfile.config.Config;
import org.apache.spark.launcher.SparkAppHandle;
import org.apache.spark.launcher.SparkLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Spark作业启动器
 * 负责使用SparkLauncher启动Spark作业
 */
public class SmallFileMergeLauncher {
    private static final Logger logger = LoggerFactory.getLogger(SmallFileMergeLauncher.class);
    
    private final Config config;
    
    public SmallFileMergeLauncher(Config config) {
        this.config = config;
    }
    
    /**
     * 使用SparkLauncher启动Spark作业
     *
     * @param args 命令行参数
     * @throws Exception 如果启动失败
     */
    public void launchSparkJob(String[] args) throws Exception {
        logger.info("使用SparkLauncher启动Spark作业");
        
        // 获取当前JAR路径
        String jarPath = new File(SmallFileMergeLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
        
        // 构建SparkLauncher
        SparkLauncher launcher = new SparkLauncher()
                .setAppResource(jarPath)
                .setMainClass(SmallFileMergeLauncher.class.getName())
                .setMaster( "yarn")
                .setDeployMode("cluster")
                .setAppName("small-file-merge");
        
        // 添加命令行参数
        StringBuilder argsStr = new StringBuilder();
        for (String arg : args) {
            argsStr.append(arg).append(" ");
        }
        launcher.addAppArgs(argsStr.toString().split(" "));
        
        // 设置Spark配置
//        Map<String, String> sparkConfig = config.getSpark().getJobParameters();
//        for (Map.Entry<String, String> entry : sparkConfig.entrySet()) {
//            launcher.setConf(entry.getKey(), entry.getValue());
//        }
        
        // 启动Spark作业
        final CountDownLatch latch = new CountDownLatch(1);
        final Map<String, String> appStatus = new HashMap<>();
        
        SparkAppHandle handle = launcher.startApplication(new SparkAppHandle.Listener() {
            @Override
            public void stateChanged(SparkAppHandle handle) {
                logger.info("Spark作业状态变更: {}", handle.getState());
                appStatus.put("state", handle.getState().toString());
                
                if (handle.getState().isFinal()) {
                    latch.countDown();
                }
            }
            
            @Override
            public void infoChanged(SparkAppHandle handle) {
                if (handle.getAppId() != null) {
                    logger.info("Spark应用ID: {}", handle.getAppId());
                    appStatus.put("appId", handle.getAppId());
                }
            }
        });
        
        logger.info("Spark作业已提交，等待完成...");
        latch.await();
        
        logger.info("Spark作业执行完成，最终状态: {}", appStatus.get("state"));
        if ("FAILED".equals(appStatus.get("state"))) {
            throw new RuntimeException("Spark作业执行失败");
        }
    }
} 