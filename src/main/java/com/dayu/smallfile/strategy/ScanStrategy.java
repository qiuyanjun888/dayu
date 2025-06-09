package com.dayu.smallfile.strategy;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.ScanConfig;
import com.dayu.smallfile.model.HiveTblMergePath;

import java.util.List;

/**
 * 文件扫描策略接口
 * 负责扫描文件系统，识别需要合并的小文件路径
 */
public interface ScanStrategy {
    
    /**
     * 扫描文件系统，找出需要合并的小文件路径
     *
     * @param config 配置对象
     * @return 需要合并的路径列表
     */
    List<HiveTblMergePath> scan(Config config) throws Exception;
} 