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
    
    // 在类级别存储数据库统计的行范围
    private int dbStatsStartRow;
    private int dbStatsEndRow;
    
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
            
            // 在第一个sheet中添加数据库维度统计数据
            addDatabaseStatsData(workbook, dataSheet);
            
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
     * 在第一个Sheet添加数据库维度的统计数据
     *
     * @param workbook 工作簿
     * @param sheet 数据Sheet
     */
    private void addDatabaseStatsData(XSSFWorkbook workbook, XSSFSheet sheet) {
        // 获取最后一行的行号
        int lastRowNum = sheet.getLastRowNum();
        dbStatsStartRow = lastRowNum + 3; // 留出2行空白
        
        // 创建表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        // 创建数据库统计表头
        Row headerRow = sheet.createRow(dbStatsStartRow);
        String[] dbStatsHeaders = {"数据库名", "表数量", "合并前文件数", "合并后文件数", 
                                  "合并前平均大小(MB)", "合并后平均大小(MB)", 
                                  "成功表数", "失败表数", "总耗时(秒)"};
        
        for (int i = 0; i < dbStatsHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(dbStatsHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 按数据库分组统计
        Map<String, List<HiveTblMergeResult>> dbResults = results.stream()
                .collect(Collectors.groupingBy(HiveTblMergeResult::getDbName));
        
        int rowNum = dbStatsStartRow + 1;
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
            
            // 创建行
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(dbName);
            row.createCell(1).setCellValue(tableCount);
            row.createCell(2).setCellValue(beforeFileCount);
            row.createCell(3).setCellValue(afterFileCount);
            row.createCell(4).setCellValue(beforeAvgSize);
            row.createCell(5).setCellValue(afterAvgSize);
            row.createCell(6).setCellValue(successCount);
            row.createCell(7).setCellValue(failCount);
            row.createCell(8).setCellValue(totalDuration);
        }
        
        // 保存数据库统计结束行
        dbStatsEndRow = rowNum - 1;
        
        // 输出日志确认行范围
        logger.info("数据库统计数据行范围: {} - {}", dbStatsStartRow+1, dbStatsEndRow);
    }
    
    /**
     * 创建图表表格
     *
     * @param workbook 工作簿
     * @param dataSheet 数据表格，用于引用数据
     */
    private void createChartSheet(XSSFWorkbook workbook, XSSFSheet dataSheet) {
        XSSFSheet chartSheet = workbook.createSheet("数据库统计图表");
        
        // 计算行数
        int rowCount = dbStatsEndRow - (dbStatsStartRow + 1) + 1;
        
        if (rowCount <= 0) {
            logger.warn("没有数据库统计数据，无法创建图表");
            return;
        }
        
        // 创建文件数量对比图表
        createFileCountChart(workbook, dataSheet, chartSheet, dbStatsStartRow + 1, dbStatsEndRow);
        
        // 创建平均文件大小对比图表
        createFileSizeChart(workbook, dataSheet, chartSheet, dbStatsStartRow + 1, dbStatsEndRow);
    }
    
    /**
     * 创建文件数量对比图表
     * 
     * @param workbook 工作簿
     * @param dataSheet 数据表格
     * @param chartSheet 图表表格
     * @param startRow 数据起始行
     * @param endRow 数据结束行
     */
    private void createFileCountChart(XSSFWorkbook workbook, XSSFSheet dataSheet, XSSFSheet chartSheet, int startRow, int endRow) {
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
            String categoryRange = "'" + dataSheet.getSheetName() + "'!$A$" + startRow + ":$A$" + endRow;
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(categoryRange));
            
            // 创建图表数据
            XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            data.setVaryColors(false);
            
            // 合并前文件数
            String beforeRange = "'" + dataSheet.getSheetName() + "'!$C$" + startRow + ":$C$" + endRow;
            XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), CellRangeAddress.valueOf(beforeRange));
            XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
            beforeSeries.setTitle("合并前文件数", null);
            
            // 合并后文件数
            String afterRange = "'" + dataSheet.getSheetName() + "'!$D$" + startRow + ":$D$" + endRow;
            XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), CellRangeAddress.valueOf(afterRange));
            XDDFChartData.Series afterSeries = data.addSeries(categories, afterValues);
            afterSeries.setTitle("合并后文件数", null);
            
            // 设置为柱状图
            XDDFBarChartData barChartData = (XDDFBarChartData) data;
            barChartData.setBarDirection(BarDirection.COL);
            barChartData.setBarGrouping(BarGrouping.CLUSTERED); // 确保柱子并排显示
            barChartData.setOverlap((byte)0); // 设置柱子之间不重叠
            barChartData.setGapWidth(150); // 设置组间距
            
            // 绘制图表
            chart.plot(data);
        } catch (Exception e) {
            logger.error("创建文件数量对比图表失败: {}", e.getMessage());
        }
    }
    
    /**
     * 创建平均文件大小对比图表
     * 
     * @param workbook 工作簿
     * @param dataSheet 数据表格
     * @param chartSheet 图表表格
     * @param startRow 数据起始行
     * @param endRow 数据结束行
     */
    private void createFileSizeChart(XSSFWorkbook workbook, XSSFSheet dataSheet, XSSFSheet chartSheet, int startRow, int endRow) {
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
            String categoryRange = "'" + dataSheet.getSheetName() + "'!$A$" + startRow + ":$A$" + endRow;
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(workbook.getSheet(dataSheet.getSheetName()), 
                    CellRangeAddress.valueOf(categoryRange));
            
            // 创建图表数据
            XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            data.setVaryColors(false);
            
            // 合并前平均大小
            String beforeRange = "'" + dataSheet.getSheetName() + "'!$E$" + startRow + ":$E$" + endRow;
            XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), CellRangeAddress.valueOf(beforeRange));
            XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
            beforeSeries.setTitle("合并前平均大小(MB)", null);
            
            // 合并后平均大小
            String afterRange = "'" + dataSheet.getSheetName() + "'!$F$" + startRow + ":$F$" + endRow;
            XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(
                    workbook.getSheet(dataSheet.getSheetName()), CellRangeAddress.valueOf(afterRange));
            XDDFChartData.Series afterSeries = data.addSeries(categories, afterValues);
            afterSeries.setTitle("合并后平均大小(MB)", null);
            
            // 设置为柱状图
            XDDFBarChartData barChartData = (XDDFBarChartData) data;
            barChartData.setBarDirection(BarDirection.COL);
            barChartData.setBarGrouping(BarGrouping.CLUSTERED); // 确保柱子并排显示
            barChartData.setOverlap((byte)0); // 设置柱子之间不重叠
            barChartData.setGapWidth(150); // 设置组间距
            
            // 绘制图表
            chart.plot(data);
        } catch (Exception e) {
            logger.error("创建平均文件大小对比图表失败: {}", e.getMessage());
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
} 