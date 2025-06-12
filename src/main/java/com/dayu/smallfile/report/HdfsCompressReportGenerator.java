package com.dayu.smallfile.report;

import com.dayu.smallfile.config.ReportConfig;
import com.dayu.smallfile.model.HdfsCompressResult;
import com.dayu.smallfile.model.HdfsCompressTask;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
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
 * HDFS压缩报告生成器
 */
public class HdfsCompressReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HdfsCompressReportGenerator.class);
    
    private final ReportConfig config;
    private final List<HdfsCompressResult> results;
    
    /**
     * 构造函数
     * 
     * @param config 报告配置
     * @param results 压缩结果列表
     */
    public HdfsCompressReportGenerator(ReportConfig config, List<HdfsCompressResult> results) {
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
        String fileName = "hdfs_compress_report_" + timestamp + ".xlsx";
        File reportFile = new File(dir, fileName);
        
        // 生成Excel报告
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 创建数据表格
            XSSFSheet dataSheet = createDataSheet(workbook);
            
            // 创建汇总表格
            createSummarySheet(workbook);
            
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
        XSSFSheet sheet = workbook.createSheet("压缩任务清单");
        
        // 设置列宽
        sheet.setColumnWidth(0, 15 * 256);  // 根路径
        sheet.setColumnWidth(1, 40 * 256);  // 文件路径
        sheet.setColumnWidth(2, 12 * 256);  // 原始文件大小
        sheet.setColumnWidth(3, 12 * 256);  // 压缩后大小
        sheet.setColumnWidth(4, 10 * 256);  // 压缩率
        sheet.setColumnWidth(5, 10 * 256);  // 处理时间
        sheet.setColumnWidth(6, 10 * 256);  // 状态
        sheet.setColumnWidth(7, 30 * 256);  // 错误信息
        
        // 创建表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"根路径", "文件路径", "原始文件大小(字节)", "压缩后大小(字节)", 
                           "压缩率", "处理时间(毫秒)", "状态", "错误信息"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 填充数据
        int rowNum = 1;
        for (HdfsCompressResult result : results) {
            for (HdfsCompressTask task : result.getTasks()) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(task.getRootPath());
                row.createCell(1).setCellValue(task.getFilePath());
                row.createCell(2).setCellValue(task.getOriginalSize());
                
                if ("SUCCESS".equals(task.getStatus())) {
                    row.createCell(3).setCellValue(task.getCompressedSize());
                    double compressionRatio = 1.0 - ((double) task.getCompressedSize() / task.getOriginalSize());
                    row.createCell(4).setCellValue(String.format("%.2f%%", compressionRatio * 100));
                    row.createCell(5).setCellValue(task.getProcessingTime());
                } else {
                    row.createCell(3).setCellValue(0);
                    row.createCell(4).setCellValue("N/A");
                    row.createCell(5).setCellValue(0);
                }
                
                row.createCell(6).setCellValue(task.getStatus());
                row.createCell(7).setCellValue(task.getErrorMessage() != null ? task.getErrorMessage() : "");
            }
        }
        
        return sheet;
    }
    
    /**
     * 创建汇总表格
     * 
     * @param workbook 工作簿
     */
    private void createSummarySheet(XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("压缩结果统计");
        
        // 设置列宽
        sheet.setColumnWidth(0, 20 * 256);  // 根路径
        sheet.setColumnWidth(1, 15 * 256);  // 总文件数
        sheet.setColumnWidth(2, 15 * 256);  // 成功文件数
        sheet.setColumnWidth(3, 15 * 256);  // 失败文件数
        sheet.setColumnWidth(4, 15 * 256);  // 原始总大小
        sheet.setColumnWidth(5, 15 * 256);  // 压缩后总大小
        sheet.setColumnWidth(6, 10 * 256);  // 压缩率
        sheet.setColumnWidth(7, 15 * 256);  // 总处理时间
        
        // 创建表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"根路径", "总文件数", "成功文件数", "失败文件数", 
                           "原始总大小(字节)", "压缩后总大小(字节)", "压缩率", "总处理时间(毫秒)"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 填充数据
        int rowNum = 1;
        for (HdfsCompressResult result : results) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(result.getRootPath());
            row.createCell(1).setCellValue(result.getTotalFiles().get());
            row.createCell(2).setCellValue(result.getSuccessFiles().get());
            row.createCell(3).setCellValue(result.getFailedFiles().get());
            row.createCell(4).setCellValue(result.getOriginalTotalSize().get());
            row.createCell(5).setCellValue(result.getCompressedTotalSize().get());
            row.createCell(6).setCellValue(String.format("%.2f%%", result.getCompressionRatio() * 100));
            row.createCell(7).setCellValue(result.getTotalProcessingTime().get());
        }
        
        // 创建图表
        createCompressionRatioChart(workbook, sheet, results.size() + 3);
    }
    
    /**
     * 创建压缩率图表
     * 
     * @param workbook 工作簿
     * @param sheet 工作表
     * @param startRow 图表起始行
     */
    private void createCompressionRatioChart(XSSFWorkbook workbook, XSSFSheet sheet, int startRow) {
        // 创建图表数据
        Row headerRow = sheet.createRow(startRow);
        headerRow.createCell(0).setCellValue("根路径");
        headerRow.createCell(1).setCellValue("压缩前总大小");
        headerRow.createCell(2).setCellValue("压缩后总大小");
        
        for (int i = 0; i < results.size(); i++) {
            HdfsCompressResult result = results.get(i);
            Row row = sheet.createRow(startRow + i + 1);
            
            row.createCell(0).setCellValue(result.getRootPath());
            row.createCell(1).setCellValue(result.getOriginalTotalSize().get());
            row.createCell(2).setCellValue(result.getCompressedTotalSize().get());
        }
        
        // 创建绘图区域
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, startRow, 15, startRow + 20);
        
        // 创建图表
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("各路径压缩前后大小对比");
        chart.setTitleOverlay(false);
        
        // 创建图例
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP);
        
        // 创建X轴
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("根路径");
        
        // 创建Y轴
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("大小（字节）");
        
        // 设置数据范围
        XDDFDataSource<String> rootPaths = XDDFDataSourcesFactory.fromStringCellRange(sheet, 
                new CellRangeAddress(startRow + 1, startRow + results.size(), 0, 0));
        
        XDDFNumericalDataSource<Double> originalSizes = XDDFDataSourcesFactory.fromNumericCellRange(sheet, 
                new CellRangeAddress(startRow + 1, startRow + results.size(), 1, 1));
        
        XDDFNumericalDataSource<Double> compressedSizes = XDDFDataSourcesFactory.fromNumericCellRange(sheet, 
                new CellRangeAddress(startRow + 1, startRow + results.size(), 2, 2));
        
        // 创建柱状图
        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        
        // 添加数据系列
        XDDFChartData.Series originalSeries = data.addSeries(rootPaths, originalSizes);
        originalSeries.setTitle("压缩前总大小", null);
        
        XDDFChartData.Series compressedSeries = data.addSeries(rootPaths, compressedSizes);
        compressedSeries.setTitle("压缩后总大小", null);
        
        // 绘制图表
        chart.plot(data);
    }
} 