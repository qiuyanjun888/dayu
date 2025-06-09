package com.dayu.smallfile.report;

import com.dayu.smallfile.config.Config;
import com.dayu.smallfile.config.ReportConfig;
import com.dayu.smallfile.model.HiveTblMergeResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    
    private final ReportConfig config;
    private final List<HiveTblMergeResult> results;
    
    public ReportGenerator(ReportConfig config, List<HiveTblMergeResult> results) {
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
        String outputDir = config.getOutputDir();
        
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
            createDataSheet(workbook);
            
            // 创建图表
            createChartSheet(workbook);
            
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
     */
    private void createDataSheet(XSSFWorkbook workbook) {
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
        
        // 创建表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
    }
    
    /**
     * 创建图表
     *
     * @param workbook 工作簿
     */
    private void createChartSheet(XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("合并结果图表");
        
        // 准备图表数据
        int rowCount = results.size();
        
        // 创建数据区域
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("路径");
        headerRow.createCell(1).setCellValue("合并前文件数");
        headerRow.createCell(2).setCellValue("合并后文件数");
        headerRow.createCell(3).setCellValue("合并前平均大小(MB)");
        headerRow.createCell(4).setCellValue("合并后平均大小(MB)");
        
        for (int i = 0; i < results.size(); i++) {
            HiveTblMergeResult result = results.get(i);
            Row row = sheet.createRow(i + 1);
            
            String label = result.getDbName() + "." + result.getTableName();
            row.createCell(0).setCellValue(label);
            row.createCell(1).setCellValue(result.getBeforeFileCount());
            row.createCell(2).setCellValue(result.getAfterFileCount());
            row.createCell(3).setCellValue(formatSize(result.getBeforeAvgSize()));
            row.createCell(4).setCellValue(formatSize(result.getAfterAvgSize()));
        }
        
        // 创建文件数量对比图表
        createBarChart(sheet, 1, rowCount, 0, 2, "文件数量对比", "表", "文件数量", 2, 2, 12, 15);
        
        // 创建平均文件大小对比图表
        createBarChart(sheet, 1, rowCount, 0, 4, "平均文件大小对比(MB)", "表", "平均大小(MB)", 2, 18, 12, 15);
    }
    
    /**
     * 创建柱状图
     *
     * @param sheet 工作表
     * @param startRow 数据开始行
     * @param endRow 数据结束行
     * @param categoryCol 分类列
     * @param endValueCol 值结束列
     * @param title 图表标题
     * @param categoryAxisTitle 分类轴标题
     * @param valueAxisTitle 值轴标题
     * @param col1 图表左上角列
     * @param row1 图表左上角行
     * @param col2 图表右下角列
     * @param row2 图表右下角行
     */
    private void createBarChart(XSSFSheet sheet, int startRow, int endRow, int categoryCol, int endValueCol, 
                               String title, String categoryAxisTitle, String valueAxisTitle, 
                               int col1, int row1, int col2, int row2) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);


        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);
        
        XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        categoryAxis.setTitle(categoryAxisTitle);
        
        XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
        valueAxis.setTitle(valueAxisTitle);
        
        // 获取数据范围
        String categoryRange = sheet.getSheetName() + "!$" + getColumnName(categoryCol + 1) + "$" + (startRow + 1) + ":$" + getColumnName(categoryCol + 1) + "$" + (endRow + 1);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, CellRangeAddress.valueOf(categoryRange));
        
        XDDFChartData data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
        XDDFChartData.Series series1 = null;
        XDDFChartData.Series series2 = null;
        
        if (endValueCol >= 2) {
            // 合并前数据系列
            String valuesRange1 = sheet.getSheetName() + "!$" + getColumnName(1 + 1) + "$" + (startRow + 1) + ":$" + getColumnName(1 + 1) + "$" + (endRow + 1);
            XDDFNumericalDataSource<Double> values1 = XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress.valueOf(valuesRange1));
            series1 = data.addSeries(categories, values1);
            series1.setTitle("合并前", null);
        }
        
        if (endValueCol >= 3) {
            // 合并后数据系列
            String valuesRange2 = sheet.getSheetName() + "!$" + getColumnName(2 + 1) + "$" + (startRow + 1) + ":$" + getColumnName(2 + 1) + "$" + (endRow + 1);
            XDDFNumericalDataSource<Double> values2 = XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress.valueOf(valuesRange2));
            series2 = data.addSeries(categories, values2);
            series2.setTitle("合并后", null);
        }
        
        if (endValueCol >= 5) {
            // 合并前平均大小数据系列
            String valuesRange3 = sheet.getSheetName() + "!$" + getColumnName(3 + 1) + "$" + (startRow + 1) + ":$" + getColumnName(3 + 1) + "$" + (endRow + 1);
            XDDFNumericalDataSource<Double> values3 = XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress.valueOf(valuesRange3));
            series1 = data.addSeries(categories, values3);
            series1.setTitle("合并前平均大小", null);
            
            // 合并后平均大小数据系列
            String valuesRange4 = sheet.getSheetName() + "!$" + getColumnName(4 + 1) + "$" + (startRow + 1) + ":$" + getColumnName(4 + 1) + "$" + (endRow + 1);
            XDDFNumericalDataSource<Double> values4 = XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress.valueOf(valuesRange4));
            series2 = data.addSeries(categories, values4);
            series2.setTitle("合并后平均大小", null);
        }
        
        // 设置为柱状图
        XDDFBarChartData barChartData = (XDDFBarChartData) data;
        barChartData.setBarDirection(BarDirection.COL);

        chart.plot(data);
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