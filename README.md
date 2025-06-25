# 大禹 - 大数据运维治理工具箱

大禹是一个全面的大数据运维治理工具箱，旨在解决大数据平台中的常见运维问题，提高数据管理效率和系统性能。该工具箱采用模块化设计，可以根据需求灵活扩展，目前已实现的核心功能包括HDFS小文件合并工具。

## 架构设计

### 核心服务层:

- **小文件分析服务**：统计分析HDFS小文件分布，生成优化建议报告，与小文件合并工具集成
- **HDFS压缩服务**：支持多种压缩算法(SNappy, Gzip, LZO等)，按目录/文件类型自动压缩，压缩策略管理
- **文件清理服务**：基于TTL的自动清理，保留策略管理(按时间/版本)，敏感文件安全删除
- **元数据管理服务**：统一管理Hive表、HDFS目录元数据，变更历史追踪，血缘关系分析
- **任务调度服务**：定时任务管理，依赖调度(DAG)，任务优先级控制
- **监控告警服务**：资源使用监控，异常检测，多渠道告警(邮件/短信/Webhook)

### 公共基础设施层:

- **存储层**：HDFS作为底层存储，对象存储(S3/OSS)可选支持
- **计算层**：Spark作为主要计算引擎，Flink用于流式处理场景
- **元数据层**：Hive MetaStore，自定义元数据扩展

### 技术架构:

- **前端**：Web UI (React/Vue + Ant Design)，可视化分析 (ECharts/D3.js)
- **后端**：微服务框架 (Spring Boot/Spring Cloud)，任务调度 (Apache Airflow/DolphinScheduler)，分布式协调 (ZooKeeper)
- **数据流**：批处理 (Spark)，流处理 (Flink)，消息队列 (Kafka/Pulsar)

### 扩展性设计:

- **RESTful API**：提供标准接口供外部系统集成
- **配置中心**：动态调整运行参数
- **多租户支持**：隔离不同业务线的资源和使用

## HDFS小文件合并工具

基于Spark的HDFS小文件合并工具，用于解决Hadoop生态系统中的小文件问题。

### 功能特点

- 支持扫描Hive表，自动识别需要合并的小文件
- 多线程并行处理，提高合并效率
- 支持试运行模式，不实际执行合并
- 生成详细的合并报告（Excel或CSV格式）

### 系统要求

- Java 17+
- Apache Spark 3.x
- Apache Hadoop 3.x
- Apache Hive 3.x

### 快速开始

#### 编译

```bash
./gradlew fatJar
```

编译成功后，会在`build/libs`目录下生成`dayu-small-file-1.0.0.jar`文件。

#### 配置

在运行前，需要准备配置文件。可以复制`src/main/resources/config.yaml`文件，并根据实际情况修改。

配置文件主要包括以下几个部分：

- Spark作业配置
- 合并参数配置
- 扫描配置（Hive表或HDFS路径）
- 线程池配置
- 日志与报告配置
- 高级配置

#### 运行

```bash
spark-submit \
  --class com.dayu.smallfile.SmallFileMergeApp \
  --master yarn \
  --deploy-mode cluster \
  --conf spark.driver.memory=2g \
  --conf spark.executor.memory=4g \
  build/libs/dayu-small-file-1.0.0.jar \
  -c /path/to/config.yaml
```

或者使用Java命令启动（会自动使用SparkLauncher提交Spark作业）：

```bash
java -jar build/libs/dayu-small-file-1.0.0.jar -c /path/to/config.yaml
```

### 命令行参数

- `-c, --config <FILE>`: 指定配置文件路径
- `--local`: 本地模式运行，不使用SparkLauncher
- `-h, --help`: 显示帮助信息

### 报告示例

合并完成后，会在配置的报告输出目录生成报告文件，包含以下信息：

- 数据库和表名
- 路径
- 文件格式
- 合并前文件数
- 合并后文件数
- 合并前平均大小
- 合并后平均大小
- 状态（成功/失败/跳过）
- 耗时
- 错误信息（如果有）

### 注意事项

- 在生产环境使用前，建议先使用试运行模式（`advanced.dry-run: true`）测试
- 合并操作会消耗大量资源，请合理配置Spark参数
- 对于重要数据，建议启用备份功能（`advanced.cleanup: false`并配置`advanced.backup-dir`）

## 许可证

Apache License 2.0 