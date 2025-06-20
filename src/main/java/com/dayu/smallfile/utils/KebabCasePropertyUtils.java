package com.dayu.smallfile.utils;

import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义PropertyUtils类，用于处理短横线命名转驼峰命名
 * 主要用于YAML配置文件解析时，将file-block-size这样的属性映射到fileBlockSize
 */
public class KebabCasePropertyUtils extends PropertyUtils {
    
    /**
     * 重写getProperty方法，支持kebab-case到camelCase的映射
     */
    @Override
    public Property getProperty(Class<?> type, String name) {
        // 先尝试直接用原始名称获取属性
        Property property = super.getProperty(type, name);
        
        // 如果找不到，尝试将短横线命名转换为驼峰命名
        if (property == null && name.contains("-")) {
            String camelCaseName = toCamelCase(name);
            property = super.getProperty(type, camelCaseName);
        }
        
        return property;
    }
    
    /**
     * 将短横线命名法转换为驼峰命名法
     * 例如: file-block-size -> fileBlockSize
     *
     * @param kebab 短横线命名的字符串
     * @return 驼峰命名的字符串
     */
    private String toCamelCase(String kebab) {
        if (kebab == null || kebab.isEmpty() || !kebab.contains("-")) {
            return kebab;
        }
        
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        
        for (int i = 0; i < kebab.length(); i++) {
            char c = kebab.charAt(i);
            if (c == '-') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
} 