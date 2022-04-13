# 前言

Hadoop不是一项具体的技术，而是一个平台，或者叫生态圈。其结构如图所示：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1647947722791-6e235e6a-24db-4d93-9794-27fb03ecd365.png)

关于Hadoop生态圈的结构图，网上有不少，一搜便有，但是整体的结构基本一致。上图展示的更为广泛，是从整个系统角度出发的。比如存储层，类似MySQL的传统关系型数据库在系统中肯定必不可少，但其不属于Hadoop平台。Hadoop平台的存储基本都是基于HDFS的，然后在这之上进行拓展、加强。比如MapReduce就是基于HDFS的一个离线计算框架。由于单独的HDFS不能做到像MySQL那样在应用里即时查询，就有了HBase，也是基于HDFS的分布式数据库。又由于MapReduce实在太慢了，又有了Spark、Flink等计算框架。。。

你看，Hadoop生态圈就在这种良好的环境下不断成长，形成了现在庞大的Hadoop大数据体系。但是也给我们这些初学者造成不少困扰，框架太多，不知道学啥，该从哪下手呢？我自己的想法就是：**第一优先级，工作需要啥就学啥。如果工作跟大数据无关，想自己学，那么前期就遵循一个作用的框架只学一个**。但是，像HDFS、MapReduce、YARN这三个组件属于基本组件，是要必学的。其余的，比如计算框架，选择Spark或者Flink。消息队列选择自己熟悉的。采集框架，Logstash或Flume都可以。

学完之后，可以自己设计一个系统，把这些框架全部串起来，比如下面这样：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1646127986381-98e96bba-3628-40c6-b4c5-f41074b00f44.png)

上图这个系统，实际作用不大，但是我们能正确的搭建环境、写出正确的代码、各个框架能正确的串起来，那么也算入门了。之后，就可以在某一点深入研究。在实际工作中，需要开发者深入研究的点，一般是计算层，也就是Spark或Flink等。

下面的资料收集、整理于各种书籍、开源文档等，这里分享给大家。

# 基本概念

在Hadoop平台中，最核心的三个组件是：HDFS（分布式文件系统）、YARN（集群资源管理器）和MapReduce（分布式计算框架）。本文作为大数据系列的第一章，简单了解一下这三大组件的基本原理，然后进行单机安装启动，最后上手几个基础的应用程序。

## HDFS

### HDFS概念

**HDFS** （**Hadoop Distributed File System**）是 Hadoop 下的分布式文件系统，其被设计为可以运行在廉价商用机上，所以具有高容错、高吞吐量等特性。

一个完整的HDFS文件系统通常运行在由网络连接在一起的一组计算机（或叫节点）组成的集群之上，在这些节点上运行着不同类型的守护进程，比如NameNode、DataNode、SecondaryNameNode，多个节点上不同类型的守护进程相互配合、互相协作，共同为用户提供高效的分布式存储服务。HDFS的系统架构如图所示。

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1646995444922-7ee2adcf-0c45-44fe-80f0-5a9ea3ae4644.png)

整个HDFS系统架构是一个主从架构。一个典型的HDFS集群中，通常会有一个NameNode，一个SecondaryNameNode和至少一个DataNode，而且HDFS客户端的数量也没有限制。

- **NameNode** : 负责执行有关文件系统命名空间 的操作，例如打开，关闭、重命名文件和目录等。它同时还负责集群**元数据**的存储，记录着文件中各个数据块的位置信息。
- **DataNode**：数据节点，负责提供来自文件系统客户端的读写请求，执行**数据块**的创建，删除等操作。

- - **数据块**：磁盘和文件系统都有数据块的概念，比如磁盘的数据库一般是512字节。其是数据读写的最小单位。，Hadoop 2.x版本以后默认数据块的大小为**128MB**，而且可以根据实际的需求，通过配置hdfs-site.xml文件中的**dfs.block.size**属性来改变块的大小）。这里需要特别指出的是，和其他文件系统不同，HDFS中小于一个块大小的文件并不会占据整个块的空间。

### 主从NameNode交互过程

上图中，还有个SecondaryNameNode节点，它是NameNode的辅助节点。辅助了啥？帮助NameNode合并fsimage和edits。其中fsimage存着HDFS的元数据，而edits是一个增量的写日志，需要定期把edits合并进fsimage。这种思想在很多数据库、框架中都有体现，比如Redis的增量日志和全量数据。

SecondaryNameNode和NameNode的交互过程如下图：![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1648034094758-f4aed80e-fb3f-42e1-8309-b5120d6afaa0.png)

### HDFS写流程

用两张漫画展示HDFS的读写流程最为清晰易懂

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1648035354258-9c0f7135-6780-42f5-9476-73b6b1165e54.png)

### HDFS读流程

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1648035473715-f73107ff-646a-41ef-925e-9523a2b82593.png)

## YARN

### YARN基本概念

YARN是一种通用的资源管理系统，为上层应用提供统一的资源管理和调度。可以让上层的多种计算模型（比如MapReduce、Hive、Storm、Spark等）共享整个集群资源，提高集群的资源利用率，而且还可以实现多种计算模型之间的数据共享。

### YARN架构

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1647231677852-0ec7f3a8-fb9e-4801-b4e7-8808aa62ce2a.png)

从YARN的架构图来看，YARN主要是由资源管理器（ResourceManager）、应用程序管理器（ApplicationMaster）、节点管理器（NodeManager）和相应的容器（Container）构成的。每个组件的作用如下。

#### 1. ResourceManager

`ResourceManager` 通常在独立的机器上以后台进程的形式运行，它是整个集群资源的主要协调者和管理者。`ResourceManager` 负责给用户提交的所有应用程序分配资源，它根据应用程序优先级、队列容量、ACLs、数据位置等信息，做出决策，然后以共享的、安全的、多租户的方式制定分配策略，调度集群资源。

#### 2. NodeManager

`NodeManager` 是 YARN 集群中的每个具体节点的管理者。主要负责该节点内所有容器的生命周期的管理，监视资源和跟踪节点健康。具体如下：

- 启动时向 `ResourceManager` 注册并定时发送心跳消息，等待 `ResourceManager` 的指令；
- 维护 `Container` 的生命周期，监控 `Container` 的资源使用情况；
- 管理任务运行时的相关依赖，根据 `ApplicationMaster` 的需要，在启动 `Container` 之前将需要的程序及其依赖拷贝到本地。

#### 3. ApplicationMaster

在用户提交一个应用程序时，YARN 会启动一个轻量级的进程 `ApplicationMaster`。`ApplicationMaster` 负责协调来自 `ResourceManager` 的资源，并通过 `NodeManager` 监视容器内资源的使用情况，同时还负责任务的监控与容错。具体如下：

- 根据应用的运行状态来决定动态计算资源需求；
- 向 `ResourceManager` 申请资源，监控申请的资源的使用情况；
- 跟踪任务状态和进度，报告资源的使用情况和应用的进度信息；
- 负责任务的容错。

#### 4. Container

`Container` 是 YARN 中的资源抽象，它封装了某个节点上的多维度资源，如内存、CPU、磁盘、网络等。当 AM 向 RM 申请资源时，RM 为 AM 返回的资源是用 `Container` 表示的。YARN 会为每个任务分配一个 `Container`，该任务只能使用该 `Container` 中描述的资源。`ApplicationMaster` 可在 `Container` 内运行任何类型的任务。例如，`MapReduce ApplicationMaster` 请求一个容器来启动 map 或 reduce 任务，而 `Giraph ApplicationMaster` 请求一个容器来运行 Giraph 任务。

### YARN工作流程图

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1648122093919-07f8f7a8-ad75-4d66-927d-f6d89594e5a2.png)

## MapReduce

MapReduce是一个可用于大规模数据处理的分布式计算框架，它借助函数式编程及**分而治之**的设计思想，使编程人员在即使不会分布式编程的情况下，也能够轻松地编写分布式应用程序并运行在分布式系统之上。什么是分而治之呢？可以看下图：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649841102594-6ab3ceab-3eb1-4535-922a-d7782663e2ec.png)

分而治之就是把一个大任务拆分成多个子任务，最终再汇总合并结果。比如Java的多线程Stream和归并排序也使用了类似的思想。为了更好理解这个思想，这里给出一个简单的案例：计算1+2+3...+9之和。把这个计算过程画出来就是这样：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649841291773-a82e96d7-668e-4e54-bc49-efbaaa001ec4.png)

理解这个思想比较容易，但总要上手写代码的，MapReduce针对这些问题，该怎么写代码呢？最后一章简单应用会有示例，好奇的朋友可以先跳转过去。

# 快速开始(单机伪集群)

## 下载

下载地址为:https://archive.apache.org/dist/hadoop/common/hadoop-2.9.2/hadoop-2.9.2.tar.gz 

## 配置

下载完成并解压之后，我们就可以开始修改配置文件了。首先进入到hadoop根目录下的etc/hadoop中。修改如下文件：

### 1.hadoop-env.sh

```shell
export JAVA_HOME = JDK安装路径
```

### 2.core-site.xml

```xml
<configuration>
  <property>
    <!--指定 namenode 的 hdfs 协议文件系统的通信地址-->
    <!-- 如果是在云服务器上安装，localhost换成内网IP-->
    <name>fs.defaultFS</name>
    <value>hdfs://localhost:8020</value>
  </property>
  <property>
    <!--指定 hadoop 存储临时文件的目录-->
    <name>hadoop.tmp.dir</name>
    <value>指定目录</value>
  </property>
</configuration>
```

### 3.hdfs-site.xml

```xml
<configuration>
    <property>
        <!--由于我们这里搭建是单机版本，所以指定 dfs 的副本系数为 1-->
        <name>dfs.replication</name>
        <value>1</value>
    </property>
</configuration>
```

### 4.slaves

配置所有从属节点的主机名或IP地址，本演示的单机模式，所以默认localhost即可。

### 5.mapred-site.xml

默认只有一个mapred-site.xml.template文件，我们需要复制一份：`cp mapred-site.xml.template mapred-site.xml `

```xml
<configuration>
    <property>
      <!-- 这里指定mapreduce基于yarn运行程序 -->
        <name>mapreduce.framework.name</name>
        <value>yarn</value>
    </property>
</configuration>
```

### 6.yarn-site.xml

```xml
<configuration>
    <property>
        <!--配置 NodeManager 上运行的附属服务。需要配置成 mapreduce_shuffle 后才可以在 Yarn 上运行 MapReduce 程序。-->
        <name>yarn.nodemanager.aux-services</name>
        <value>mapreduce_shuffle</value>
    </property>
</configuration>
```

## 启动

首先进入到hadoop根目录的sbin目录下，这个目录都是Hadoop的执行脚本。分别启动hdfs和yarn：`./start-dfs.sh`，`./start-yarn.sh`。启动过程中可能需要输入管理员密码，输入即可。如果启动失败，注意观察日志，可能是目录没权限；JDK配置不正确；网络问题；端口占用；防火墙问题等。

然后使用jps命令查看hadoop进程都启动成功没有，进程如下：

- DataNode
- NameNode
- NodeManager
- ResourceManager
- SecondaryNameNode

最后，也可以进入HDFS和YARN的Web验证是否启动正确。如果你是在云服务器配置的，这时候要用外网IP访问，并开放端口访问权限。页面如下：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649845459699-9a9376f4-7e7e-4702-8b4b-0a48003fb198.png)![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649845499040-11bb4a57-08fa-408d-9fb9-1df36c89fedf.png)



# 简单应用

TODO

## HDFS Java API

- Java程序上传大文件到hdfs
- Java程序写入内容

## 单词统计

- 使用MR统计前一步上传的大文件



参考资料：《实战大数据》、[翻译经典 HDFS 原理讲解漫画](https://blog.csdn.net/hudiefenmu/article/details/37655491)、[BigData-Notes](https://github.com/heibaiying/BigData-Notes)