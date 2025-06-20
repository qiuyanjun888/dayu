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
import java.util.Date;
import java.util.List;

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
            
            // 创建图表
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
     * 创建图表
     *
     * @param workbook 工作簿
     * @param dataSheet 数据表格，用于引用数据
     */
    private void createChartSheet(XSSFWorkbook workbook, XSSFSheet dataSheet) {
        XSSFSheet chartSheet = workbook.createSheet("合并结果图表");
        
        // 创建图表数据表格
        createChartData(workbook, chartSheet);
        
        // 创建文件数量对比图表
        createFileCountChart(workbook, chartSheet, 2, 15);
        
        // 创建平均文件大小对比图表
        createFileSizeChart(workbook, chartSheet, 18, 31);
        
        // 确保所有图表数据源被正确识别
        chartSheet.enableLocking();
    }
    
    /**
     * 创建图表数据表格
     * 
     * @param workbook 工作簿
     * @param chartSheet 图表工作表
     */
    private void createChartData(XSSFWorkbook workbook, XSSFSheet chartSheet) {
        // 设置列宽自适应
        chartSheet.setDefaultColumnWidth(12);
        
        // 创建数据表格
        Row headerRow = chartSheet.createRow(0);
        headerRow.createCell(0).setCellValue("表");
        headerRow.createCell(1).setCellValue("合并前文件数");
        headerRow.createCell(2).setCellValue("合并后文件数");
        headerRow.createCell(3).setCellValue("合并前平均大小(MB)");
        headerRow.createCell(4).setCellValue("合并后平均大小(MB)");
        
        // 填充数据
        for (int i = 0; i < results.size(); i++) {
            HiveTblMergeResult result = results.get(i);
            Row row = chartSheet.createRow(i + 1);
            
            row.createCell(0).setCellValue(result.getTableName()); // 只显示表名
            row.createCell(1).setCellValue(result.getBeforeFileCount());
            row.createCell(2).setCellValue(result.getAfterFileCount());
            row.createCell(3).setCellValue(formatSize(result.getBeforeAvgSize()));
            row.createCell(4).setCellValue(formatSize(result.getAfterAvgSize()));
        }
        
        // 设置列宽自适应
        for (int i = 0; i < 5; i++) {
            chartSheet.autoSizeColumn(i);
        }
    }
    
    /**
     * 创建文件数量对比图表
     * 
     * @param workbook 工作簿
     * @param chartSheet 图表工作表
     * @param startRow 图表起始行
     * @param endRow 图表结束行
     */
    private void createFileCountChart(XSSFWorkbook workbook, XSSFSheet chartSheet, int startRow, int endRow) {
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, startRow, 10, endRow);
        
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("文件数量对比");
        chart.setTitleOverlay(false);
        
        // 设置图例
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        
        // 创建坐标轴
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        categoryAxis.setTitle("表");
        
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setTitle("文件数量");
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        
        // 获取数据范围
        int rowCount = results.size();
        String categoryRange = chartSheet.getSheetName() + "!$A$2:$A$" + (rowCount + 1);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(categoryRange));
        
        // 创建图表数据
        XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
        data.setVaryColors(false);
        
        // 合并前文件数
        String beforeRange = chartSheet.getSheetName() + "!$B$2:$B$" + (rowCount + 1);
        XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(beforeRange));
        XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
        beforeSeries.setTitle("合并前文件数", null);
        
        // 合并后文件数
        String afterRange = chartSheet.getSheetName() + "!$C$2:$C$" + (rowCount + 1);
        XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(afterRange));
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
        
        // 确保图表数据表关联到图表
        chart.setAutoTitleDeleted(false);
    }
    
    /**
     * 创建平均文件大小对比图表
     * 
     * @param workbook 工作簿
     * @param chartSheet 图表工作表
     * @param startRow 图表起始行
     * @param endRow 图表结束行
     */
    private void createFileSizeChart(XSSFWorkbook workbook, XSSFSheet chartSheet, int startRow, int endRow) {
        XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, startRow, 10, endRow);
        
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("平均文件大小对比(MB)");
        chart.setTitleOverlay(false);
        
        // 设置图例
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        
        // 创建坐标轴
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        categoryAxis.setTitle("表");
        
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setTitle("平均大小(MB)");
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        
        // 获取数据范围
        int rowCount = results.size();
        String categoryRange = chartSheet.getSheetName() + "!$A$2:$A$" + (rowCount + 1);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(categoryRange));
        
        // 创建图表数据
        XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
        data.setVaryColors(false);
        
        // 合并前平均大小
        String beforeRange = chartSheet.getSheetName() + "!$D$2:$D$" + (rowCount + 1);
        XDDFNumericalDataSource<Double> beforeValues = XDDFDataSourcesFactory.fromNumericCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(beforeRange));
        XDDFChartData.Series beforeSeries = data.addSeries(categories, beforeValues);
        beforeSeries.setTitle("合并前平均大小(MB)", null);
        
        // 合并后平均大小
        String afterRange = chartSheet.getSheetName() + "!$E$2:$E$" + (rowCount + 1);
        XDDFNumericalDataSource<Double> afterValues = XDDFDataSourcesFactory.fromNumericCellRange(workbook.getSheet(chartSheet.getSheetName()), CellRangeAddress.valueOf(afterRange));
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
        
        // 确保图表数据表关联到图表
        chart.setAutoTitleDeleted(false);
    }
    
    /**
     * 获取列名（A, B, C, ...）
     *
     * @param columnNumber 列号（从1开始）
     * @return 列名
     */
    private String getColumnName(int columnNumber) {
        StringBuilder sb = new StringBuilder();
        while (columnNumber > 0) {
            int remainder = (columnNumber - 1) % 26;
            sb.insert(0, (char) ('A' + remainder));
            columnNumber = (columnNumber - 1) / 26;
        }
        return sb.toString();
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