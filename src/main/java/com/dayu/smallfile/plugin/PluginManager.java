package com.dayu.smallfile.plugin;

import com.dayu.smallfile.strategy.MergeStrategy;
import com.dayu.smallfile.strategy.ScanStrategy;
import com.dayu.smallfile.strategy.impl.HiveTableMergeStrategy;
import com.dayu.smallfile.strategy.impl.HiveTableScanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 插件管理器
 * 负责加载和管理自定义策略插件
 */
public class PluginManager {
    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);
    
    private ClassLoader pluginClassLoader;
    private ScanStrategy scanStrategy;
    private MergeStrategy mergeStrategy;
    
    public PluginManager() {
        this.pluginClassLoader = getClass().getClassLoader();
    }
    
    /**
     * 加载插件JAR文件
     *
     * @param jarPath JAR文件路径
     * @throws Exception 如果加载失败
     */
    public void loadPluginJar(String jarPath) throws Exception {
        if (jarPath == null || jarPath.isEmpty()) {
            logger.info("未指定插件JAR路径，使用默认类加载器");
            return;
        }
        
        File jarFile = new File(jarPath);
        if (!jarFile.exists() || !jarFile.isFile()) {
            throw new IllegalArgumentException("插件JAR文件不存在: " + jarPath);
        }
        
        URL[] urls = new URL[]{jarFile.toURI().toURL()};
        this.pluginClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
        logger.info("已加载插件JAR: {}", jarPath);
    }
    
    /**
     * 注册策略类
     *
     * @param className 策略类名
     * @param type 策略类型（scan或merge）
     * @throws Exception 如果注册失败
     */
    public void registerStrategy(String className, String type) throws Exception {
        if (className == null || className.isEmpty()) {
            if ("scan".equals(type)) {
                this.scanStrategy = new HiveTableScanStrategy();
                logger.info("使用默认扫描策略: {}", HiveTableScanStrategy.class.getName());
            } else if ("merge".equals(type)) {
                this.mergeStrategy = new HiveTableMergeStrategy();
                logger.info("使用默认合并策略: {}", HiveTableMergeStrategy.class.getName());
            }
            return;
        }
        
        try {
            Class<?> strategyClass = pluginClassLoader.loadClass(className);
            
            if ("scan".equals(type)) {
                if (ScanStrategy.class.isAssignableFrom(strategyClass)) {
                    Constructor<?> constructor = strategyClass.getConstructor();
                    this.scanStrategy = (ScanStrategy) constructor.newInstance();
                    logger.info("已注册扫描策略: {}", className);
                } else {
                    throw new IllegalArgumentException("类 " + className + " 不是有效的FileScanStrategy实现");
                }
            } else if ("merge".equals(type)) {
                if (MergeStrategy.class.isAssignableFrom(strategyClass)) {
                    Constructor<?> constructor = strategyClass.getConstructor();
                    this.mergeStrategy = (MergeStrategy) constructor.newInstance();
                    logger.info("已注册合并策略: {}", className);
                } else {
                    throw new IllegalArgumentException("类 " + className + " 不是有效的FileMergeStrategy实现");
                }
            } else {
                throw new IllegalArgumentException("无效的策略类型: " + type);
            }
        } catch (ClassNotFoundException e) {
            logger.error("找不到策略类: {}", className, e);
            throw e;
        } catch (Exception e) {
            logger.error("注册策略类失败: {}", className, e);
            throw e;
        }
    }
    
    /**
     * 获取扫描策略
     *
     * @return 扫描策略实例
     */
    public ScanStrategy getScanStrategy() {
        if (scanStrategy == null) {
            scanStrategy = new HiveTableScanStrategy();
            logger.info("使用默认扫描策略: {}", HiveTableScanStrategy.class.getName());
        }
        return scanStrategy;
    }
    
    /**
     * 获取合并策略
     *
     * @return 合并策略实例
     */
    public MergeStrategy getMergeStrategy() {
        if (mergeStrategy == null) {
            mergeStrategy = new HiveTableMergeStrategy();
            logger.info("使用默认合并策略: {}", HiveTableMergeStrategy.class.getName());
        }
        return mergeStrategy;
    }
} 