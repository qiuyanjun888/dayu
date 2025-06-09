package com.dayu.smallfile.strategy;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.model.HiveTblMergePath;
import com.dayu.smallfile.model.HiveTblMergeResult;

import java.util.List;

/**
 * 文件合并策略接口
 * 负责执行小文件合并操作
 */
public interface MergeStrategy {
    
    /**
     * 执行小文件合并
     *
     * @param mergePaths 需要合并的路径列表
     * @param config 配置对象
     * @return 合并结果列表
     */
    List<HiveTblMergeResult> merge(List<HiveTblMergePath> mergePaths, Config config) throws Exception;
} 