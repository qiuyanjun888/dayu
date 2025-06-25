package com.dayu.smallfile.report;

import com.dayu.smallfile.config.SmallFileMergeConfig;
import com.dayu.smallfile.model.HiveTblMergeResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;




public class ReportGeneratorTest {

    private SmallFileMergeConfig mergeConfig;
    private List<HiveTblMergeResult> results;
    private final String outputDir = "D:\\var\\log\\merge-tool";

    @Before
    public void setUp() {
        // 初始化SmallFileMergeConfig
        mergeConfig = new SmallFileMergeConfig();
        mergeConfig.setReportOutputDir(outputDir);
        
        // 初始化测试数据
        results = new ArrayList<>();
        
        // 第一条测试数据
        HiveTblMergeResult result1 = new HiveTblMergeResult("test", "table1", 
                "/user/hive/warehouse/test.db/table1", "text");
        result1.setBeforeFileCount(7842);
        result1.setAfterFileCount(862);
        result1.setBeforeAvgSize(convertMBToBytes(17.8));
        result1.setAfterAvgSize(convertMBToBytes(108.5));
        result1.markSuccess();
        result1.setDuration(convertTimeToMillis("2m 45s"));
        results.add(result1);
        
        // 第二条测试数据
        HiveTblMergeResult result2 = new HiveTblMergeResult("test", "table2", 
                "/user/hive/warehouse/test.db/table2", "text");
        result2.setBeforeFileCount(3215);
        result2.setAfterFileCount(418);
        result2.setBeforeAvgSize(convertMBToBytes(12.1));
        result2.setAfterAvgSize(convertMBToBytes(92.4));
        result2.markSuccess();
        result2.setDuration(convertTimeToMillis("4m 12s"));
        results.add(result2);
    }
    
    /*@After
    public void tearDown() {
        // 清理测试生成的报告文件
        File dir = new File(outputDir);
        File[] files = dir.listFiles((d, name) -> name.startsWith("merge_report_") && name.endsWith(".xlsx"));
        if (files != null) {
            for (File file : files) {
                // 为了安全，仅删除测试期间生成的报告
                if (file.lastModified() > System.currentTimeMillis() - 60000) { // 1分钟内创建的文件
                    file.delete();
                }
            }
        }
    }*/

    @Test
    public void testGenerateReport() throws IOException {
        // 创建报告生成器
        ReportGenerator generator = new ReportGenerator(mergeConfig, results);
        
        // 生成报告
        File reportFile = generator.generateReport();
        
        // 验证报告文件是否生成
        Assert.assertTrue("报告文件应该已生成", reportFile.exists());
        Assert.assertTrue("报告文件不应为空", reportFile.length() > 0);
        Assert.assertTrue("报告文件名应以merge_report_开头", reportFile.getName().startsWith("merge_report_"));
        Assert.assertTrue("报告文件应为xlsx格式", reportFile.getName().endsWith(".xlsx"));
        
        System.out.println("生成的报告文件: " + reportFile.getAbsolutePath());
    }
    
    /**
     * 将MB转换为字节
     * @param mb 兆字节
     * @return 字节
     */
    private long convertMBToBytes(double mb) {
        return (long) (mb * 1024 * 1024);
    }
    
    /**
     * 将时间字符串转换为毫秒
     * @param timeStr 时间字符串，如 "2m 45s"
     * @return 毫秒
     */
    private long convertTimeToMillis(String timeStr) {
        long totalMillis = 0;
        String[] parts = timeStr.split(" ");
        
        for (String part : parts) {
            if (part.endsWith("m")) {
                long minutes = Long.parseLong(part.substring(0, part.length() - 1));
                totalMillis += minutes * 60 * 1000;
            } else if (part.endsWith("s")) {
                long seconds = Long.parseLong(part.substring(0, part.length() - 1));
                totalMillis += seconds * 1000;
            }
        }
        
        return totalMillis;
    }
} 