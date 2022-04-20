# 背景介绍

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1647587013675-f5105ad3-8002-46d7-889a-db2806058c1f.png)

HBase是一个在HDFS上开发的面向列的分布式**KV数据库**。如果需要实时地随机读写超大规模数据集，就可以使用HBase来进行处理。

相较而言，传统的关系型数据库如果想存储超大规模的数据，就必须面临分库分表的操作，管理起来就相对复杂。而HBase是基于HDFS的，天然就拥有可伸缩的分布式特性。同时也补全了HDFS的一大缺点：HDFS只能执行批处理，无法实现对数据的随机访问，这意味着即使简单的任务，也必须搜索整个数据集。

上图就是HBase在Hadoop家族中的位置，可以看到它是基于HDFS的，同时它上面还有两个Phoenix组件。这个组件也好的补全了HBase缺少的一些功能，比如提供了JDBC客户端，让HBase支持聚合查询等。可以发现，Hadoop家族的各个框架、组件仿佛只能在某一个很小的领域发挥作用，但是它们组合在一起后，就能发挥无穷的威力。

# 基本概念

## HBase数据模型

HBase的数据模型很有说头，不过刚入门可以不了解那么深，后面会再单独写一篇“深入了解HBase”。为了方便理解，可以把HBase的数据模型从逻辑上想象成一个表格，类似MySQL那种，如下图：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1648726296520-0a879fe2-beed-4b25-b40f-ce18919f90ca.png)

虽然逻辑上可以看成一个表格，但是前面说过了，HBase是一个KV数据库，所以上图中rowkey就是HBase的k。而anchor、contents、people是**列簇**。其中anchor列族有cnnsi.com和my.look.ca两个**列**，两个列下面就是实际的**值**了，也就是KV中的V。除此之外，HBase还支持不同时刻的多版本Value，比如html列中，就有t5、t6、t7三个时刻的值。最重要的是，上表中，空出来的位置并不会占用空间，也就是说没有类似NULL这种占位符，空就是空。

## 系统结构

HBase系统主要有如下三个部分组成：

- **RegionServer**

- - RegionServer主要用来响应用户的IO请求，是HBase中最核心的模块，由WAL(HLog)、BlockCache以及多个Region构成。
  - **WAL(HLog)**，HLog在HBase中有两个核心作用——其一，用于实现数据的高可靠性，HBase数据随机写入时，并非直接写入HFile数据文件，而是先写入缓存，再异步刷新落盘。其二，用于实现HBase集群间主从复制，通过回放主集群推送过来的HLog日志实现主从复制。
  - **BlockCache**，HBase系统中的读缓存。客户端从磁盘读取数据之后通常会将数据缓存到系统内存中，后续访问同一行数据可以直接从内存中获取而不需要访问磁盘。
  - **Region**，数据表的一个分片，当数据表大小超过一定阈值就会“水平切分”，分裂为两个Region。Region是集群负载均衡的基本单位。通常一张表的Region会分布在整个集群的多台RegionServer上，一个RegionServer上会管理多个Region，当然，这些Region一般来自不同的数据表。

- **Master**

- - 处理用户的各种管理请求，包括建表、修改表、权限操作、切分表、合并数据分片以及Compaction等。
  - 管理集群中所有RegionServer，包括RegionServer中Region的负载均衡、RegionServer的宕机恢复以及Region的迁移等。
  - 清理过期日志以及文件，Master会每隔一段时间检查HDFS中HLog是否过期、HFile是否已经被删除，并在过期之后将其删除。

- **Zookeeper**

- - 保证任何时候，集群中有且仅有一个 Master。
  - 管理系统核心元数据，比如，当前系统中正常工作的RegionServer集合。
  - 参与RegionServer宕机恢复，ZooKeeper通过心跳可以感知到RegionServer是否宕机，并在宕机后通知Master进行宕机处理。
  - 帮助HBase实现分布式表锁。

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650012272004-79fa1cb4-201d-41ad-9cf0-178c48482d4c.png)

## 存储结构

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650012330916-a417ac2d-8e88-4b49-bcf5-95b8057d6e52.png)

接下来主要看看RegionServer中到底是如何存储数据的。

一个RegionServer由一个（或多个）HLog、一个BlockCache以及多个Region组成。其中，HLog用来保证数据写入的可靠性；BlockCache可以将数据块缓存在内存中以提升数据读取性能；Region是HBase中数据表的一个数据分片，一个RegionServer上通常会负责多个Region的数据读写。一个Region由多个Store组成，**每个Store存放对应列簇的数据**，比如一个表中有两个列簇，这个表的所有Region就都会包含两个Store。每个Store包含一个MemStore和多个HFile，**用户数据写入时会将对应列簇数据写入相应的MemStore，一旦写入数据的内存大小超过设定阈值，系统就会将MemStore中的数据落盘形成HFile文件**。HFile存放在HDFS上，是一种定制化格式的数据存储文件，方便用户进行数据读取。

在“深入了解HBase”中，还详细介绍了HLog、MemStore、HFile和BlockCache的文件结构。

# 下载安装（单机伪分布式）

前置准备：

- 安装JDK8+
- 启动Hadoop
- 可选：启动zookeeper（HBase默认启动自带的zookeeper，以下演示采用的是自带的zookeeper）

一共需要修改三个文件：

- hbase-site.xml：HBase配置文件
- hbase-env.sh：系统配置文件，如JVM配置、JDK指定
- regionservers：节点配置文件

```shell
# 下载软件包
wget https://archive.apache.org/dist/hbase/2.2.7/hbase-2.2.7-bin.tar.gz
# 解压
tar -zxvf https://archive.apache.org/dist/hbase/2.2.7/hbase-2.2.7-bin.tar.gz
# 移至工作目录
mv hbase-2.2.7 /usr/hbase/
# 修改配置
vim /usr/hbase/usr/hbase/conf/hbase-site.xml
```

打开配置文件后输入以下内容：

```xml
<configuration>
  <property>
    <name>hbase.cluster.distributed</name>
    <value>true</value>
  </property>
<!--指定 HBase 数据存储路径为 HDFS 上的 hbase 目录,hbase 目录不需要预先创建，程序会自动创
     建-->
  <property>
    <name>hbase.rootdir</name>
    <value>hdfs://ip:8020/hbase</value>
  </property>
<!--指定 zookeeper 数据的存储位置-->
  <property>
    <name>hbase.zookeeper.property.dataDir</name>
    <value>/home/zookeeper/dataDir</value>
  </property>
<!--如果选择自己启动zk，需要填写zk配置-->
<!-- <property>
        <name>hbase.zookeeper.quorum</name>
        <value>ip</value>
  </property>
<property>
        <name>hbase.zookeeper.property.clientPort</name>
        <value>2181</value>
</property>-->
  <property>
    <name>hbase.unsafe.stream.capability.enforce</name>
    <value>false</value>
  </property>
</configuration>
# 接着修改系统环境配置
vim /usr/hbase/conf/hbase-env.sh

# 添加如下内容
export JAVA_HOME=你的JDK安装目录

# 最后指定一下节点
vim /usr/hbase/conf/regionservers

#如果你在/etc/hosts里面指定ip别名的话，填入别名就行，否则填入IP地址
```

配置工作完成，接着进入到bin目录执行：`./start-hbase.sh` 脚本启动。启动后，用jps命令看如下进程是否成功启动：

- HMaster
- HRegionServer
- HQuorumPeer（Hbase内置的Zookeeper进程）

如果发现某个进程没有，就去logs目录查看对应日志，找到报错日志针对性解决。比如HMaster进程没有就看hbase-xxx-master-ip.log日志文件，格式大概就是这样，master对应HMaster。 

# Java API&Shell命令

Shell命令参考：https://github.com/heibaiying/BigData-Notes/blob/master/notes/Hbase_Shell.md

## Java API

首先导入Maven依赖，建议去https://mvnrepository.com/ 网站搜索hbase-client，选择和自己安装的Hbase版本号一样的依赖。我安装的Hbase版本是2.2.7，所以相应的Java客户端也选择2.2.7

```xml
<!-- https://mvnrepository.com/artifact/org.apache.hbase/hbase-client -->
        <dependency>
            <groupId>org.apache.hbase</groupId>
            <artifactId>hbase-client</artifactId>
            <version>2.2.7</version>
        </dependency>
```

API没什么好讲的，无非就是增删改查、建表、删表等，可以看https://github.com/heibaiying/BigData-Notes/blob/master/notes/Hbase_Java_API.md 。 本仓库中也有示例，见code/hbase目录。如果碰到不理解的类可以去https://hbase.apache.org/2.3/apidocs/index.html 查看官方的API文档，每个类的作用都有简单的介绍。



//TODO

# 过滤器和协处理器应用



# SpringBoot整合HBase（MyBatis+Phoenix）





参考资料：《HBase原理与实践》、https://github.com/heibaiying/BigData-Notes