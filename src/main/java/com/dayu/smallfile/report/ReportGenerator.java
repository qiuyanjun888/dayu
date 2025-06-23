package com.dayu.smallfile.report;

import com.dayu.smallfile.config.SmallFileMergeConfig;
import com.dayu.smallfile.model.HiveTblMergeResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告生成器
 * 负责生成合并结果报告
 */
public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    
    private final SmallFileMergeConfig config;
    private final List<HiveTblMergeResult> results;
    
    public ReportGenerator(SmallFileMergeConfig config, List<HiveTblMergeResult> results) {
        this.config = config;
        this.results = results;
    }
    
    /**
     * 生成报告
     *
     * @return 生成的报告文件
     * @throws IOException 如果生成报告失败
     */
    public File generateReport() throws IOException {
        String outputDir = config.getReportOutputDir();
        
        // 创建输出目录
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建报告输出目录: " + outputDir);
        }
        
        // 生成报告文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "merge_report_" + timestamp + ".xlsx";
        File reportFile = new File(dir, fileName);
        
        // 生成Excel报告
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 创建数据表格
            XSSFSheet dataSheet = createDataSheet(workbook);
            
            // 创建图表sheet
            createChartSheet(workbook, dataSheet);
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(reportFile)) {
                workbook.write(fos);
            }
        }
        
        logger.info("报告已生成: {}", reportFile.getAbsolutePath());
        return reportFile;
    }
    
    /**
     * 创建数据表格
     *
     * @param workbook 工作簿
     * @return 创建的数据表格
     */
    private XSSFSheet createDataSheet(XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("合并结果数据");
        
        // 设置列宽
        sheet.setColumnWidth(0, 15 * 256);  // 数据库名
        sheet.setColumnWidth(1, 15 * 256);  // 表名
        sheet.setColumnWidth(2, 40 * 256);  // 路径
        sheet.setColumnWidth(3, 10 * 256);  // 文件格式
        sheet.setColumnWidth(4, 12 * 256);  // 合并前文件数
        sheet.setColumnWidth(5, 12 * 256);  // 合并后文件数
        sheet.setColumnWidth(6, 15 * 256);  // 合并前平均大小
        sheet.setColumnWidth(7, 15 * 256);  // 合并后平均大小
        sheet.setColumnWidth(8, 10 * 256);  // 状态
        sheet.setColumnWidth(9, 10 * 256);  // 耗时(秒)
        sheet.setColumnWidth(10, 30 * 256); // 错误信息
        
        // 创建表头样式 - 只加粗，不设置背景颜色
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"数据库名", "表名", "路径", "文件格式", "合并前文件数", "合并后文件数", 
                           "合并前平均大小(MB)", "合并后平均大小(MB)", "状态", "耗时(秒)", "错误信息"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 填充数据
        int rowNum = 1;
        for (HiveTblMergeResult result : results) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(result.getDbName());
            row.createCell(1).setCellValue(result.getTableName());
            row.createCell(2).setCellValue(result.getPath());
            row.createCell(3).setCellValue(result.getFileFormat());
            row.createCell(4).setCellValue(result.getBeforeFileCount());
            row.createCell(5).setCellValue(result.getAfterFileCount());
            row.createCell(6).setCellValue(formatSize(result.getBeforeAvgSize()));
            row.createCell(7).setCellValue(formatSize(result.getAfterAvgSize()));
            row.createCell(8).setCellValue(result.getStatus());
            row.createCell(9).setCellValue(result.getDuration() / 1000.0);
            row.createCell(10).setCellValue(result.getErrorMessage() != null ? result.getErrorMessage() : "");
        }
        
        return sheet;
    }
    
    /**
     * 创建图表表格
     *
     * @param workbook 工作簿
     * @param dataSheet 数据表格，用于引用数据
     */
    private void createChartSheet(XSSFWorkbook workbook, XSSFSheet dataSheet) {
        XSSFSheet chartSheet = workbook.createSheet("数据库统计图表");
        
        // 获取按数据库分组的统计数据
        Map<String, DatabaseStats> dbStatsMap = calculateDatabaseStats();
        
        if (dbStatsMap.isEmpty()) {
            logger.warn("没有数据库统计数据，无法创建图表");
            return;
        }
        
        // 创建隐藏的数据表用于图表
        XSSFSheet hiddenSheet = workbook.createSheet("ChartData");
        workbook.setSheetHidden(workbook.getSheetIndex(hiddenSheet), true);
        
        // 在隐藏表中填充数据库统计数据
        populateHiddenSheetData(hiddenSheet, dbStatsMap);
        
        // 创建文件数量对比图表
        createFileCountChart(workbook, hiddenSheet, chartSheet);
        
        // 创建平均文件大小对比图表
        createFileSizeChart(workbook, hiddenSheet, chartSheet);
    }
    
    /**
     * 计算数据库统计信息
     */
    private Map<String, DatabaseStats> calculateDatabaseStats() {
        // 按数据库分组统计
        Map<String, List<HiveTblMergeResult>> dbResults = results.stream()
                .collect(Collectors.groupingBy(HiveTblMergeResult::getDbName));
        
        Map<String, DatabaseStats> dbStatsMap = new HashMap<>();
        
        for (Map.Entry<String, List<HiveTblMergeResult>> entry : dbResults.entrySet()) {
            String dbName = entry.getKey();
            List<HiveTblMergeResult> dbTables = entry.getValue();
            
            // 计算统计数据
            int tableCount = dbTables.size();
            long beforeFileCount = dbTables.stream().mapToLong(HiveTblMergeResult::getBeforeFileCount).sum();
            long afterFileCount = dbTables.stream().mapToLong(HiveTblMergeResult::getAfterFileCount).sum();
            
            // 计算平均文件大小
            double beforeTotalSize = dbTables.stream()
                    .mapToDouble(r -> formatSize(r.getBeforeAvgSize()) * r.getBeforeFileCount())
                    .sum();
            double afterTotalSize = dbTables.stream()
                    .mapToDouble(r -> formatSize(r.getAfterAvgSize()) * r.getAfterFileCount())
                    .sum();
            
            double beforeAvgSize = beforeFileCount > 0 ? beforeTotalSize / beforeFileCount : 0;
            double afterAvgSize = afterFileCount > 0 ? afterTotalSize / afterFileCount : 0;
            
            // 计算成功和失败表数
            long successCount = dbTables.stream()
                    .filter(r -> "SUCCESS".equals(r.getStatus()))
                    .count();
            long failCount = tableCount - successCount;
            
            // 计算总耗时
            double totalDuration = dbTables.stream()
                    .mapToDouble(r -> r.getDuration() / 1000.0)
                    .sum();
            
            DatabaseStats stats = new DatabaseStats(
                dbName, tableCount, beforeFileCount, afterFileCount,
                beforeAvgSize, afterAvgSize, successCount, failCount, totalDuration
            );
            
            dbStatsMap.put(dbName, stats);
        }
        
        return dbStatsMap;
    }
    
    /**
     * 在隐藏表中填充数据库统计数据
     */
    private void populateHiddenSheetData(XSSFSheet hiddenSheet, Map<String, DatabaseStats> dbStatsMap) {
        // 创建表头
        Row headerRow = hiddenSheet.createRow(0);
        String[] headers = {"数据库名", "表数量", "合并前文件数", "合并后文件数", 
                           "合并前平均大小(MB)", "合并后平均大小(MB)", 
                           "成功表数", "失败表数", "总耗时(秒)"};
        
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        // 填充数据
        int rowNum = 1;
        for (DatabaseStats stats : dbStatsMap.values()) {
            Row row = hiddenSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(stats.getDbName());
            row.createCell(1).setCellValue(stats.getTableCount());
            row.createCell(2).setCellValue(stats.getBeforeFileCount());
            row.createCell(3).setCellValue(stats.getAfterFileCount());
            row.createCell(4).setCellValue(stats.getBeforeAvgSize());
            row.createCell(5).setCellValue(stats.getAfterAvgSize());
            row.createCell(6).setCellValue(stats.getSuccessCount());
            row.createCell(7).setCellValue(stats.getFailCount());
            row.createCell(8).setCellValue(stats.getTotalDuration());
        }
    }
    
    /**
     * 创建文件数量对比图表
     */
    private void createFileCountChart(XSSFWorkbook workbook, XSSFSheet dataSheet, XSSFSheet chartSheet) {
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, 1, 8, 15);
        
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("数据库文件数量对比");
        chart.setTitleOverlay(false);
        
        // 设置图例
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        
        // 创建坐标轴
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        categoryAxis.setTitle("数据库名");
        
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setTitle("文件数量");
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        
        try {
            // 获取数据范围
            int lastRow = dataSheet.getLastRowNum();
            if (lastRow < 1) {
                logger.warn("没有足够的数据创建图表");
                return;
            }
            
            String categoryRange = dataSheet.getSheetName() + "!$A$2:$A$" + (lastRow + 1);
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(categoryRange));
            
            // 创建图表数据
            XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            
            // 合并前文件数
            String beforeRange = dataSheet.getSheetName() + "!$C$2:$C$" + (lastRow + 1);
            XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(beforeRange));
            XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
            beforeSeries.setTitle("合并前文件数", null);
            
            // 合并后文件数
            String afterRange = dataSheet.getSheetName() + "!$D$2:$D$" + (lastRow + 1);
            XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(afterRange));
            XDDFChartData.Series afterSeries = data.addSeries(categories, afterValues);
            afterSeries.setTitle("合并后文件数", null);
            
            // 设置为柱状图
            XDDFBarChartData barChartData = (XDDFBarChartData) data;
            barChartData.setBarDirection(BarDirection.COL);
            barChartData.setBarGrouping(BarGrouping.CLUSTERED);
            barChartData.setVaryColors(true);
            barChartData.setOverlap((byte)0);
            barChartData.setGapWidth(100);
            
            // 绘制图表
            chart.plot(data);
            
        } catch (Exception e) {
            logger.error("创建文件数量对比图表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建平均文件大小对比图表
     */
    private void createFileSizeChart(XSSFWorkbook workbook, XSSFSheet dataSheet, XSSFSheet chartSheet) {
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, 16, 8, 30);
        
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("数据库平均文件大小对比(MB)");
        chart.setTitleOverlay(false);
        
        // 设置图例
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        
        // 创建坐标轴
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        categoryAxis.setTitle("数据库名");
        
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setTitle("平均大小(MB)");
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        
        try {
            // 获取数据范围
            int lastRow = dataSheet.getLastRowNum();
            if (lastRow < 1) {
                logger.warn("没有足够的数据创建图表");
                return;
            }
            
            String categoryRange = dataSheet.getSheetName() + "!$A$2:$A$" + (lastRow + 1);
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(categoryRange));
            
            // 创建图表数据
            XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            
            // 合并前平均大小
            String beforeRange = dataSheet.getSheetName() + "!$E$2:$E$" + (lastRow + 1);
            XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(beforeRange));
            XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
            beforeSeries.setTitle("合并前平均大小(MB)", null);
            
            // 合并后平均大小
            String afterRange = dataSheet.getSheetName() + "!$F$2:$F$" + (lastRow + 1);
            XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(afterRange));
            XDDFChartData.Series afterSeries = data.addSeries(categories, afterValues);
            afterSeries.setTitle("合并后平均大小(MB)", null);
            
            // 设置为柱状图
            XDDFBarChartData barChartData = (XDDFBarChartData) data;
            barChartData.setBarDirection(BarDirection.COL);
            barChartData.setBarGrouping(BarGrouping.CLUSTERED);
            barChartData.setVaryColors(true);
            barChartData.setOverlap((byte)0);
            barChartData.setGapWidth(100);
            
            // 绘制图表
            chart.plot(data);
            
        } catch (Exception e) {
            logger.error("创建平均文件大小对比图表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 格式化文件大小为MB
     *
     * @param bytes 字节数
     * @return MB
     */
    private double formatSize(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
    
    /**
     * 数据库统计数据类
     */
    private static class DatabaseStats {
        private final String dbName;
        private final int tableCount;
        private final long beforeFileCount;
        private final long afterFileCount;
        private final double beforeAvgSize;
        private final double afterAvgSize;
        private final long successCount;
        private final long failCount;
        private final double totalDuration;
        
        public DatabaseStats(String dbName, int tableCount, long beforeFileCount, long afterFileCount,
                            double beforeAvgSize, double afterAvgSize, long successCount, long failCount,
                            double totalDuration) {
            this.dbName = dbName;
            this.tableCount = tableCount;
            this.beforeFileCount = beforeFileCount;
            this.afterFileCount = afterFileCount;
            this.beforeAvgSize = beforeAvgSize;
            this.afterAvgSize = afterAvgSize;
            this.successCount = successCount;
            this.failCount = failCount;
            this.totalDuration = totalDuration;
        }
        
        public String getDbName() {
            return dbName;
        }
        
        public int getTableCount() {
            return tableCount;
        }
        
        public long getBeforeFileCount() {
            return beforeFileCount;
        }
        
        public long getAfterFileCount() {
            return afterFileCount;
        }
        
        public double getBeforeAvgSize() {
            return beforeAvgSize;
        }
        
        public double getAfterAvgSize() {
            return afterAvgSize;
        }
        
        public long getSuccessCount() {
            return successCount;
        }
        
        public long getFailCount() {
            return failCount;
        }
        
        public double getTotalDuration() {
            return totalDuration;
        }
    }
} 