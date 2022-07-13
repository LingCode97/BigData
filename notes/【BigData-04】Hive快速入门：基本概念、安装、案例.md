# 基本概念

Hive 是一个构建在 Hadoop 之上的数据仓库工具。在没有 Hive 之前，处理数据必须开发复杂的 MapReduce 作业。而Hive只需要开发简单的SQL就可以达到和MR同样的查询功能，Hive会把SQL语句转为MapReduce作业。Hive使用的查询语言成为HiveQL（HQL），跟SQL很像，基本的语法几乎和SQL一模一样，只是大部分函数不一样，函数后面会说到。

## 架构

## ![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1651738417608-c55a1f9b-b8b4-4ea7-8c8b-49610e34097a.png)

Hive的架构如上图所示。下面分别说明一下：

- **CLI接口**：命令行接口
- **JDBC/ODBC客户端**：封装了Thrift服务的Java应用程序，可以通过指定的主机和端口连接到在另一个进程中运行的Hive服务器。
- **Thrift服务器**：Thrift服务器基于Socket通信，支持跨语言。HiveThrift服务简化了在多编程语言中运行Hive的命令。绑定支持C++、Java、PHP、Python和Ruby语言。
- **Web接口**：Web接口就是通过Web浏览器访问、操作、管理Hive。
- Hive驱动引擎（**把HQL转为MR的过程**）：

- - 解释器：将HQL语句转换为语法树。
  - 编译器：将语法树编译为逻辑执行计划。
  - 优化器：对逻辑执行计划进行优化。
  - 执行器：调用底层的运行框架执行逻辑执行计划。

- **元数据库**：元数据用于存放Hive的基础信息，它存储在关系型数据库中，如MySQL、Derby（默认）。元数据包括：数据库信息、表名、表的列和分区及其属性，表的属性，表的数据所在目录等。

## 内置函数

SQL里面内置了大量的函数，比如字符串处理函数，日期函数等等。而Hive也有类似的函数，函数名称和SQL有些区别，所以部分情况下，SQL函数并不能直接用在Hive里面。不过函数也不强调去背，实际工作需要用到啥再去查询即可。这里列出一个网址，详细说明了各种类型的函数及其功能和用法。

Hive内置函数：https://www.hadoopdoc.com/hive/hive-built-in-function

就我的经验，用到最多的就是日期函数，比如时间戳转日期，yyyyMMdd转yyyy-MM-dd等。还有字符串函数，截取某个字符串的某一区间。以及json解析函数，有些字符串实际是一个json字符串，想取其中的某个属性，就可以用上这个函数。具体的函数用法可以去上面的网址查询。

## 数据存储

和HBase不同，**Hive本身没有专门的数据存储格式**（虽然HBase也是基于HDFS的，但是它定义了自己的存储格式，比如HFile），也不能为数据建立索引，只需在创建表时指定列分隔符和行分隔符就可以解析数据了。

Hive中主要包含4类数据模型：表（Table）、分区（Partition）、桶（Bucket）和外部表（External Table）：

- **表**：Hive中的表和数据库中的表在概念上是类似的，每个表在Hive中都有一个对应的存储目录。例如，一个表user在HDFS中的路径为/warehouse/user，其中/warehouse是hive-site.xml配置文件中由${hive.metastore.warehouse.dir}指定的数据仓库的目录。
- **分区**：在Hive中，表中的一个Partition对应于表下的一个目录，所有Partition的数据都存储在对应的目录中。比如用某天的日期当分区字段，这样某天内的所有数据都在同一个目录下。当然分区字段可以用多个，HDFS的目录则有多级，比如dt和city是分区字段，那么对应的目录是：/warehouse/xiaojun/dt=20170801/city=CA
- **桶**：如果你浏览过HDFS的目录，可能会看到最底层的目录类似于：part-00000、part-00001等等。后面的数字就是对应桶的序号。Hive会对指定列计算Hash，然后根据Hash值分散至Bucket中。
- **外部表**：有外部表就有内部表，内部表就是创建一个Hive表，然后往里面insert数据，这个数据就会存在内部表里面，如果要删除这个表，其元数据和实际的数据都会被删除。而外部表只是某个HDFS路径下已存在的数据，然后将其加载至Hive，通过HQL进行数据查询等操作。



# 快速开始

## 下载

下载地址：https://dlcdn.apache.org/hive/ 。根据自己的Hadoop版本选择，如果Hadoop是3.x的，hive就选3.x的。如果Hadoop是2.x的，hive就选2.x的。我下载的是2.3.9版本。进到上面的网址后，选择编译好的版本下载，也是体积最大的：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1651825198477-c0edf14e-c7af-4ae1-a336-ad5271fad344.png)

下载完成后，解压即可。

## 配置

我们本次使用MySQL存储Hive的元数据，所以得先安装MySQL，MySQL的安装这里就不演示了。安装好MySQL后进到hive根目录。做如下配置：

```shell
cd conf/
cp hive-default.xml.template hive-site.xml
vim hive-site.xml
```

进入到hive-site.xml后，先配置MySQL链接相关的：

```xml
<!-- 修改元数据存储驱动，description为默认的，解释该属性的作用 -->
<property>
    <name>javax.jdo.option.ConnectionDriverName</name>
    <value>com.mysql.cj.jdbc.Driver</value>
    <description>Driver class name for a JDBC metastore</description>
</property>
<!-- mysql链接，hive数据库不用自己手动创建，会自动创建 -->
<property>
    <name>javax.jdo.option.ConnectionURL</name>
    <value>jdbc:mysql://localhost:3306/hive?createDatabaseIfNotExit=true</value>
    <description>
      JDBC connect string for a JDBC metastore.
      To use SSL to encrypt/authenticate the connection, provide database-specific SSL flag in the connection URL.
      For example, jdbc:postgresql://myhost/db?ssl=true for postgres database.
    </description>
</property>
<property>
    <name>datanucleus.schema.autoCreateAll</name>
    <value>true</value>
    <description>Auto creates necessary schema on a startup if one doesn't exist. Set this to false, after creating it once.To enable auto create also set hive.metastore.schema.verification=false. Auto creation is not recommended for production use cases, run schematool command instead.</description>
</property>
<!-- mysql的用户名和密码 -->
<property>
    <name>javax.jdo.option.ConnectionUserName</name>
    <value>你的用户名</value>
    <description>Username to use against metastore database</description>
</property>
<property>
    <name>javax.jdo.option.ConnectionPassword</name>
    <value>你的密码</value>
    <description>password to use against metastore database</description>
</property>
```

Hive的lib目录里面是不带MySQL的连接器jar包的，所以需要你自己下载一个，然后拷贝进去。去Maven仓库找一个和你MySQL版本一致的，然后下载，拷贝进hive的lib目录。https://mvnrepository.com/artifact/mysql/mysql-connector-java

接着修改hive相关的目录配置，还是在hive-site.xml文件里面：

```xml
<!-- 下面的目录改成你的hadoop目录即可 -->
<property>
    <name>hive.querylog.location</name>
    <value>/opt/hadoop/app/hive/iotmp</value>
    <description>Location of Hive run time structured log file</description>
</property>
<property>
    <name>hive.exec.local.scratchdir</name>
    <value>/opt/hadoop/app/hive/iotmp</value>
    <description>Local scratch space for Hive jobs</description>
</property>
<property>
    <name>hive.downloaded.resources.dir</name>
    <value>/opt/hadoop/app/hive/iotmp</value>
    <description>Temporary local directory for added resources in the remote file system.</description>
</property>
```

## 启动

- 手动初始化元数据表格：这里踩过一个小小的坑，之前启动hive服务时，一直提示`hive.version`表格不存在。按理说上面已经配置了自动创建数据库和表格，都会自己创建，也许是版本更新导致配置有些变化，这里也懒得追究到底哪个配置出问题了。如果你和我一样，还是出现了类似的错误，那就手动初始化一下表格，hive也提供了相应的工具，进到bin目录，执行如下命令：`./schematool -dbType mysql -initSchema`。
- 接着启动hive的metadata服务：`./hive --service metastore &`
- 最后启动hive服务：`./hive`



# 简单应用

​	TODO