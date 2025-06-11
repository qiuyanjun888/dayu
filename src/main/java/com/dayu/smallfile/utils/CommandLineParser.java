package com.dayu.smallfile.utils;

import org.apache.commons.cli.*;

/**
 * 命令行参数解析器
 * 负责解析和处理命令行参数
 */
public class CommandLineParser {

    private final Options options;
    
    public CommandLineParser() {
        options = new Options();
        
        // 配置文件选项
        options.addOption(Option.builder("f")
                .longOpt("conf")
                .hasArg()
                .argName("FILE")
                .desc("配置文件路径")
                .build());
        
        // 插件JAR选项
        options.addOption(Option.builder("j")
                .longOpt("jar")
                .hasArg()
                .argName("JAR")
                .desc("插件JAR文件路径")
                .build());

        // 帮助选项
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("显示帮助信息")
                .build());
    }
    
    /**
     * 解析命令行参数
     *
     * @param args 命令行参数数组
     * @return 解析后的命令行对象
     * @throws ParseException 如果解析失败
     */
    public CommandLine parse(String[] args) throws ParseException {
        DefaultParser parser = new DefaultParser();
        return parser.parse(options, args);
    }
    
    /**
     * 打印帮助信息
     */
    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar dayu-small-file.jar [options]", options);
    }
} 