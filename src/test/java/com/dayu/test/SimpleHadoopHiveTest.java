package com.dayu.test;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.spark.sql.SparkSession;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class SimpleHadoopHiveTest {

    @Test
    public void testHDFS() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://172.22.84.111:8020/");
        try {
            // 获取文件系统实例
            FileSystem fs = FileSystem.get(URI.create(conf.get("fs.defaultFS")), conf);
            // 列出根目录下的文件和目录
            FileStatus[] fileStatus = fs.listStatus(new Path("/"));
            Arrays.stream(fileStatus).forEach(status -> System.out.println(status.getPath().toString()));
            // 关闭文件系统连接
            fs.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testHive() throws Exception {
        Class.forName("org.apache.hive.jdbc.HiveDriver");
        Connection conn = DriverManager.getConnection("jdbc:hive2://172.22.84.111:10000");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("select * from testdb.pokes limit 10");
        while (rs.next()) {
            System.out.println(rs.getString(1) + rs.getString(2));
        }
        rs.close();
        stmt.close();
        conn.close();
    }

    @Test
    public void testSparkRead() throws Exception {
        SparkSession spark = SparkSession.builder().master("local").enableHiveSupport().getOrCreate();
        spark.sql("select * from testdb.pokes limit 10").show();
        spark.close();
    }

    @Test
    public void testHiveMeta() throws Exception {
        HiveConf hiveConf = new HiveConf();
        HiveMetaStoreClient client = new HiveMetaStoreClient(hiveConf);
        try {
            Table table = client.getTable("testdb", "pokes");
            String location = table.getSd().getLocation();
            String inputFormat = table.getSd().getInputFormat();
            System.out.println("location = " + location);
            System.out.println("inputFormat = " + inputFormat);
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (Exception e) {
                System.out.println("关闭Hive元数据客户端时出错: " + e.getMessage());
            }
        }
    }
}
