package com.dayu.test;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * Hive表操作测试
 */
public class HiveTableTest {

    private static final String JDBC_URL = "jdbc:hive2://172.29.21.223:10000";
    private static final String TABLE_NAME = "testdb.test_merge";

    @Test
    public void testCreateTableAndInsertData() throws Exception {
        // 加载Hive JDBC驱动
        Class.forName("org.apache.hive.jdbc.HiveDriver");
        
        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             Statement stmt = conn.createStatement()) {
            
            // 删除表（如果存在）
            stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            
            // 创建分区表，指定格式为text
            String createTableSQL = "CREATE TABLE " + TABLE_NAME + " (" +
                    "name STRING, " +
                    "age INT" +
                    ") " +
                    "PARTITIONED BY (address STRING) " +
                    "STORED AS TEXTFILE";
            
            stmt.execute(createTableSQL);
            System.out.println("表创建成功：" + TABLE_NAME);
            
            // 向上海分区插入3条数据
            String insertShanghaiSQL = "INSERT INTO " + TABLE_NAME + " PARTITION (address='shanghai') VALUES " +
                    "('张三', 25), " +
                    "('李四', 30), " +
                    "('王五', 28)";
            
            stmt.execute(insertShanghaiSQL);
            System.out.println("上海分区数据插入成功");
            
            // 向北京分区插入2条数据
            String insertBeijingSQL = "INSERT INTO " + TABLE_NAME + " PARTITION (address='beijing') VALUES " +
                    "('赵六', 35), " +
                    "('钱七', 40)";
            
            stmt.execute(insertBeijingSQL);
            System.out.println("北京分区数据插入成功");
            
            // 查询插入的数据进行验证
            System.out.println("验证表数据：");
            stmt.execute("SELECT * FROM " + TABLE_NAME);
            ResultSet resultSet = stmt.getResultSet();
            
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                int age = resultSet.getInt("age");
                String address = resultSet.getString("address");
                System.out.println("name=" + name + ", age=" + age + ", address=" + address);
            }
        }
    }
    
    @Test
    public void datacopy() throws Exception {
        // Hadoop配置
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://172.29.21.223:8020");
        
        // 获取文件系统实例
        FileSystem fs = FileSystem.get(conf);
        
        try {
            // 复制上海分区数据
            copyPartitionData(fs, "shanghai");
            
            // 复制北京分区数据
            copyPartitionData(fs, "beijing");
            
            System.out.println("数据复制完成！");
        } finally {
            fs.close();
        }
    }
    
    /**
     * 为指定分区的数据文件创建副本
     * 
     * @param fs 文件系统
     * @param address 分区值
     * @throws IOException 发生IO异常
     */
    private void copyPartitionData(FileSystem fs, String address) throws IOException {
        // 分区目录路径
        String partitionPath = "/user/hive/warehouse/testdb.db/test_merge/address=" + address;
        Path dirPath = new Path(partitionPath);
        
        // 检查目录是否存在
        if (!fs.exists(dirPath)) {
            System.out.println("目录不存在：" + partitionPath);
            return;
        }
        
        // 获取目录中的所有文件
        FileStatus[] files = fs.listStatus(dirPath);
        System.out.println("目录: " + partitionPath + " 包含 " + files.length + " 个文件");
        
        for (FileStatus fileStatus : files) {
            if (fileStatus.isFile()) {
                Path originalFile = fileStatus.getPath();
                String fileName = originalFile.getName();
                
                // 为每个文件创建3个副本
                for (int i = 1; i <= 3; i++) {
                    Path copyPath = new Path(dirPath, fileName + ".copy" + i);
                    
                    // 读取源文件内容
                    StringBuilder content = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(originalFile)))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                    }
                    
                    // 写入新的副本文件
                    try (PrintWriter writer = new PrintWriter(fs.create(copyPath))) {
                        writer.print(content.toString());
                    }
                    
                    System.out.println("创建副本: " + copyPath.toString());
                }
            }
        }
    }
    
    /**
     * 创建模拟的.staging目录和空文件
     */
    @Test
    public void mockfolder() throws Exception {
        // Hadoop配置
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://172.29.21.223:8020");
        
        // 获取文件系统实例
        FileSystem fs = FileSystem.get(conf);
        
        try {
            // 表目录路径
            String tablePath = "/user/hive/warehouse/testdb.db/test_merge";
            
            // .staging目录路径
            Path stagingPath = new Path(tablePath + "/.staging");
            
            // 创建.staging目录
            if (!fs.exists(stagingPath)) {
                fs.mkdirs(stagingPath);
                System.out.println("已创建目录: " + stagingPath.toString());
            } else {
                System.out.println("目录已存在: " + stagingPath.toString());
            }
            
            // 在.staging目录下创建3个空文件
            for (int i = 1; i <= 3; i++) {
                Path emptyFile = new Path(stagingPath, "empty_file_" + i);
                
                // 创建空文件
                fs.create(emptyFile).close();
                
                System.out.println("已创建空文件: " + emptyFile.toString());
            }
            
            System.out.println(".staging目录和空文件创建完成！");
        } finally {
            fs.close();
        }
    }
} 