package com.dayu.smallfile.config;

import lombok.Data;

/**
 * 线程池配置类
 */
@Data
public class ThreadPoolConfig {
    private int size;
    private String timeout;
    private int queueSize;
} 