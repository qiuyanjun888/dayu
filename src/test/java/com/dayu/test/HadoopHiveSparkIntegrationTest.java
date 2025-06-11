package com.dayu.test;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.*;
import org.apache.hadoop.mapred.MiniMRCluster;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HadoopHiveSparkIntegrationTest {

    private static MiniDFSCluster miniDFSCluster;
    private static MiniMRCluster miniMRCluster;
    private static HiveMetaStoreClient hiveMetaStoreClient;
    private static FileSystem fs;
    private static SparkSession sparkSession;
    private static JavaSparkContext sparkContext;
    private static final String DB_NAME = "test_db";
    private static final String TABLE_NAME = "student";
    private static final String HDFS_ROOT = "hdfs://localhost:9000";
    // private static final String TEST_BASE_DIR = "D:/develop/test";

    @BeforeClass
    public static void setup() throws Exception {
        // 1. 设置Hadoop MiniCluster
        Configuration conf = new Configuration();
        conf.set("hadoop.tmp.dir", Files.createTempDirectory("hadoop").toString());
        conf.set("dfs.namenode.name.dir", Files.createTempDirectory("nn").toString());
        conf.set("dfs.datanode.data.dir", Files.createTempDirectory("dn").toString());

        miniDFSCluster = new MiniDFSCluster.Builder(conf)
                .nameNodePort(9000)
                .numDataNodes(1)
                .build();
        fs = miniDFSCluster.getFileSystem();

        // 2. 设置MiniMRCluster
//        miniMRCluster = new MiniMRCluster(1, conf.get("fs.defaultFS"), 1);

        // 3. 设置Hive Metastore (使用Derby)
        System.setProperty("javax.jdo.option.ConnectionURL",
                "jdbc:derby:memory:myDB;create=true");
        System.setProperty("hive.metastore.warehouse.dir", HDFS_ROOT + "/user/hive/warehouse");

        HiveConf hiveConf = new HiveConf();
        hiveConf.set("hive.metastore.uris", "");
        hiveConf.set("fs.defaultFS", HDFS_ROOT);
        hiveConf.set("mapreduce.framework.name", "local");

        hiveMetaStoreClient = new HiveMetaStoreClient(hiveConf);

        // 创建测试数据库
        Database db = new Database();
        db.setName(DB_NAME);
        hiveMetaStoreClient.createDatabase(db);

        // 4. 初始化Spark
        SparkConf sparkConf = new SparkConf()
                .setAppName("HadoopHiveSparkTest")
                .setMaster("local[*]")
                .set("spark.sql.warehouse.dir", HDFS_ROOT + "/user/hive/warehouse");

        sparkSession = SparkSession.builder()
                .config(sparkConf)
                .enableHiveSupport()
                .getOrCreate();

        sparkContext = new JavaSparkContext(sparkSession.sparkContext());

        // 5. 创建Hive表并插入数据
        createHiveTable();
        insertData();
    }

    private static void createHiveTable() throws Exception {
        // 创建表结构
        List<FieldSchema> columns = new ArrayList<>();
        columns.add(new FieldSchema("id", "int", ""));
        columns.add(new FieldSchema("name", "string", ""));
        columns.add(new FieldSchema("age", "int", ""));

        List<FieldSchema> partitionColumns = new ArrayList<>();
        partitionColumns.add(new FieldSchema("city", "string", ""));

        // 设置存储描述
        StorageDescriptor sd = new StorageDescriptor();
        sd.setCols(columns);
        sd.setInputFormat("org.apache.hadoop.mapred.TextInputFormat");
        sd.setOutputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat");
        sd.setSerdeInfo(new SerDeInfo());
        sd.getSerdeInfo().setSerializationLib("org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");
        sd.setLocation(HDFS_ROOT + "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME);

        // 创建表
        Table table = new Table();
        table.setDbName(DB_NAME);
        table.setTableName(TABLE_NAME);
        table.setPartitionKeys(partitionColumns);
        table.setSd(sd);
        table.setTableType("MANAGED_TABLE");

        hiveMetaStoreClient.createTable(table);
    }

    private static void insertData() throws Exception {
        // 创建分区目录并写入数据
        createPartitionData("shanghai", 5, 3); // 5条数据，分成3个文件
        createPartitionData("beijing", 5, 2);  // 5条数据，分成2个文件

        // 添加分区到Hive Metastore
        addPartitionToHive("shanghai");
        addPartitionToHive("beijing");
    }

    private static void createPartitionData(String city, int recordCount, int fileCount) throws IOException {
        Path partitionPath = new Path(HDFS_ROOT + "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME + "/city=" + city);
        fs.mkdirs(partitionPath);

        // 将数据分成多个文件写入
        for (int i = 0; i < fileCount; i++) {
            Path filePath = new Path(partitionPath, "data_" + i + ".txt");
            try (PrintWriter writer = new PrintWriter(fs.create(filePath))) {
                int start = i * (recordCount / fileCount);
                int end = (i == fileCount - 1) ? recordCount : (i + 1) * (recordCount / fileCount);

                for (int j = start; j < end; j++) {
                    writer.println((j + 1) + ",student_" + (j + 1) + "," + (20 + j % 5));
                }
            }
        }
    }

    private static void addPartitionToHive(String city) throws Exception {
        StorageDescriptor sd = new StorageDescriptor();
        sd.setLocation(HDFS_ROOT + "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME + "/city=" + city);

        Partition partition = new Partition();
        partition.setDbName(DB_NAME);
        partition.setTableName(TABLE_NAME);
        partition.setSd(sd);
        partition.addToValues(city);

        hiveMetaStoreClient.add_partition(partition);
    }

    @Test
    public void testHadoopHiveSparkIntegration() throws Exception {
        // 1. 测试HDFS API获取ContentSummary和blockSize
        Path tablePath = new Path(HDFS_ROOT + "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME);
        ContentSummary contentSummary = fs.getContentSummary(tablePath);
        System.out.println("HDFS ContentSummary: " + contentSummary);
        System.out.println("DFS BlockSize: " + fs.getDefaultBlockSize(tablePath));

        // 2. 测试Hive Metastore获取表元数据
        Table table = hiveMetaStoreClient.getTable(DB_NAME, TABLE_NAME);
        System.out.println("Hive Table Metadata: " + table);

        List<Partition> partitions = hiveMetaStoreClient.listPartitions(DB_NAME, TABLE_NAME, (short) -1);
        System.out.println("Hive Partitions:");
        partitions.forEach(p -> System.out.println(p.getValues() + " -> " + p.getSd().getLocation()));

        // 3. 使用Spark读取分区数据并合并文件
        Dataset<Row> df = sparkSession.sql("SELECT * FROM " + DB_NAME + "." + TABLE_NAME);
        df.show();

        // 合并上海分区的文件
        mergePartitionFiles("shanghai");
        // 合并北京分区的文件
        mergePartitionFiles("beijing");

        // 验证合并后的文件数量
        verifyFileCount("shanghai", 1);
        verifyFileCount("beijing", 1);
    }

    private void mergePartitionFiles(String city) throws IOException {
        String partitionPath = "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME + "/city=" + city;

        // 读取分区数据
        Dataset<Row> partitionDF = sparkSession.read()
                .option("header", "false")
                .option("inferSchema", "true")
                .csv(HDFS_ROOT + partitionPath + "/*.txt");

        // 临时输出路径
        String tempPath = "/tmp/" + UUID.randomUUID().toString();

        // 写入单个文件
        partitionDF.repartition(1)
                .write()
                .mode("overwrite")
                .csv(HDFS_ROOT + tempPath);

        // 删除原分区文件
        fs.delete(new Path(HDFS_ROOT + partitionPath), true);
        fs.mkdirs(new Path(HDFS_ROOT + partitionPath));

        // 移动合并后的文件到分区目录
        for (org.apache.hadoop.fs.FileStatus status : fs.listStatus(new Path(HDFS_ROOT + tempPath))) {
            if (!status.getPath().getName().startsWith("_")) {
                fs.rename(status.getPath(),
                        new Path(HDFS_ROOT + partitionPath + "/merged_data.txt"));
            }
        }

        // 删除临时目录
        fs.delete(new Path(HDFS_ROOT + tempPath), true);
    }

    private void verifyFileCount(String city, int expectedCount) throws IOException {
        String partitionPath = "/user/hive/warehouse/" + DB_NAME + ".db/" + TABLE_NAME + "/city=" + city;
        int actualCount = fs.listStatus(new Path(HDFS_ROOT + partitionPath)).length;
        System.out.println("Partition " + city + " file count: " + actualCount);
        assert actualCount == expectedCount : "Expected " + expectedCount + " files but found " + actualCount;
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (hiveMetaStoreClient != null) hiveMetaStoreClient.close();
        if (miniMRCluster != null) miniMRCluster.shutdown();
        if (miniDFSCluster != null) miniDFSCluster.shutdown();
        if (sparkSession != null) sparkSession.stop();
    }
}