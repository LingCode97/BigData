# HBase数据模型

HBase包含了大量关系型数据库的概念，即：表、行、列，但又总是说HBase是一个Map。那HBase到底是一个怎么样的数据库呢？

实际上，从逻辑视图来看，HBase中的数据是以表形式进行组织的，而且和关系型数据库中的表一样，HBase中的表也由行和列构成，因此HBase非常容易理解。但从物理视图来看，HBase是一个Map，由键值（KeyValue，KV）构成。不过这个Map与普通的Map区别还不小，具体的区别可以接着往下看。我们先说说这个逻辑视图，看看这个表格的格式。

## 逻辑视图

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649244004954-8b5ecdd6-8b1e-440a-b9ac-20f727bc57fa.png)

针对上表的内容，我解释一下：第一个列是rowkey，这个也就是Map的key了。anchor、contents、people则被称为**列簇**，它们下面的cnnsi.com,my.look.ca等才是真正的**列**，**一个列簇下的列可以动态扩展**。另外，**HBase使用时间戳实现了数据的多版本支持**。可以看到html列，其中t5,t6,t7就是不同时刻所对应的值，其中t5、t6等是**时间戳**。

还可以看到，表中有不少空值。在其他数据库中，对于空值的处理一般都会填充null，而对于HBase，**空值不需要任何填充**。这个特性为什么重要？因为HBase的列在理论上是允许无限扩展的，对于成百万列的表来说，通常都会存在大量的空值，如果使用填充null的策略，势必会造成大量空间的浪费。

## 物理视图

物理视图，也就是HBase实际的存储结构，以上面的cnnsi.com列为例，实际存储的数据是这样的：

```shell
{"com.cnn.www"，"anchor","cnnsi.com”，"put"，"t9"}->"CNN"
```

**HBase中Map的key是一个复合键**，由rowkey、列簇、列、类型以及timestamp组成，value即为列的值。

我们知道HBase是基于HDFS的，**HBase中的数据是按照列簇存储的**，即将数据按照列簇分别存储在不同的目录中。为什么HBase要将数据按照列簇分别存储？回答这个问题之前需要先了解两个非常常见的概念：**行式存储、列式存储。**

- **行式存储**：行式存储系统会将一行数据存储在一起，一行数据写完之后再接着写下一行，最典型的如MySQL这类关系型数据库，如下图：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649322440021-f045db02-1110-432c-a60b-495f1e5c42c1.png)

行式存储在获取一行数据时是很高效的，但是如果某个查询只需要读取表中指定列对应的数据，那么行式存储会先取出一行行数据，再在每一行数据中截取待查找目标列。这种处理方式在查找过程中引入了大量无用列信息，从而导致大量内存占用。

- **列式存储**：列式存储理论上会将一列数据存储在一起，不同列的数据分别集中存储，最典型的如Kudu、Parquet on HDFS等系统（文件格式），如下图：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1649322539585-c84613f5-5b89-4844-9d4d-6fcf30296ca2.png)

列式存储对于只查找某些列数据的请求非常高效，只需要连续读出所有待查目标列，然后遍历处理即可；但是反过来，列式存储对于获取一行的请求就不那么高效了，需要多次IO读多个列数据，最终合并得到一行数据。另外，因为同一列的数据通常都具有相同的数据类型，因此列式存储具有天然的高压缩特性。

- **列簇式存储**：HBase采用的是列簇式存储，从概念上来说，列簇式存储介于行式存储和列式存储之间，可以通过不同的设计思路在行式存储和列式存储两者之间相互切换。比如，一张表只设置一个列簇，这个列簇包含所有用户的列。HBase中一个列簇的数据是存储在一起的，因此这种设计模式就等同于行式存储。再比如，一张表设置大量列簇，每个列簇下仅有一列，很显然这种设计模式就等同于列式存储



# RegionServer的核心模块

在“HBase快速入门”一文中有张这个图：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650539015675-d2273b5f-8d35-49f6-8143-2ce7778f4fb5.png)

可以看到，一个RegionServer由HLog、BlockCache、HFile、MemStore组成。前文中，只介绍了它们的基本作用，本文降深入了解一下这些组件。

## HLog

HBase中系统故障恢复以及主从复制都基于HLog实现。默认情况下，所有写入操作（写入、更新以及删除）的数据都先以追加形式写入HLog，再写入MemStore。

### HLog文件的基本结构

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650539590896-5f22ba66-2af5-42f2-8c31-adc8e79277ab.png)

直接看HLog文件的结构图有点懵，不急，我来解释一下。每个HLog是由多个Region共享的（Region就是HBase的数据分片）。比如上图中，就有三个Region A,B,C共享。图中每个小格由HLogKey和WALEdit两部分组成，表示**一次行级更新的最小追加单元**。其中HLogKey由table name、region name以及sequenceid等字段构成。WALEdit用来表示一个事务中的更新集合。

### HLog生命周期

HLog文件生成之后并不会永久存储在系统中，它的使命完成后，文件就会失效最终被删除。其生命周期如下：

- **HLog构建**：HBase的任何写入（更新、删除）操作都会先将记录追加写入到HLog文件中。
- **HLog滚动**：HBase后台启动一个线程，每隔一段时间（由参数'hbase.regionserver. logroll.period'决定，默认1小时）进行日志滚动。日志滚动会新建一个新的日志文件，接收新的日志数据。**日志滚动机制主要是为了方便过期日志数据能够以文件的形式直接删除**。
- **HLog失效**：写入数据一旦从MemStore中落盘，对应的日志数据就会失效。为了方便处理，HBase中日志失效删除总是以文件为单位执行。查看某个HLog文件是否失效只需确认该HLog文件中所有日志记录对应的数据是否已经完成落盘，如果日志中所有日志记录已经落盘，则可以认为该日志文件失效。**一旦日志文件失效，就会从WALs文件夹移动到oldWALs文件夹。注意此时HLog并没有被系统删除**。
- **HLog删除**：Master后台会启动一个线程，每隔一段时间（参数'hbase.master.cleaner. interval'，默认1分钟）检查一次文件夹oldWALs下的所有失效日志文件，确认是否可以删除，确认可以删除之后执行删除操作。

## MemStore

HBase系统中一张表会被水平切分成多个Region，每个Region负责自己区域的数据读写请求。水平切分意味着**每个Region会包含所有的列簇数据**。HBase**将不同列簇的数据存储在不同的Store中，每个Store由一个MemStore和一系列HFile组成**，结构组成如图所示：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650796509191-74036f89-57a3-42b8-956c-5e0d6ed7cf91.png)

HBase所有的数据写入操作**首先会顺序写入日志HLog，再写入MemStore**，当MemStore中数据大小超过阈值之后再将这些数据批量写入磁盘，生成一个新的HFile文件。

### MemStore的内存管理方式

从上图可以看到，一个RegionServer内有多个Region。这些Region都是共享内存的，毕竟都在同一台机器的同一个RegionServer进程内。这时就会有一个问题：内存碎片。结果就是，会导致频繁的Full GC。我们先说一下为什么会产生大量内存碎片。每个Region内，根据列簇，还会划分多个Store，也就是MemStore。当不同的Region的数据写入到MemStore时，在HBase的逻辑视角上，这些数据可能属于不同的MemStore。但是在JVM看来，这就是一坨写入到堆内存的数据。当某个Region要进行落盘时，会删除堆内存对应的空间，就会出现如下情况：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650799118793-1cf2276d-42d5-4ce1-821f-8376d0756d0e.png)

上图中，白色部分就是Region1落盘刷新后空出来的空间，内存碎片就这样产生了。为了优化这种内存碎片可能导致的Full GC，HBase借鉴了线程本地分配缓存（Thread-Local Allocation Buffer，TLAB）的内存管理方式，通过顺序化分配内存、内存数据分块等特性使得内存碎片更加粗粒度，有效改善Full GC情况。具体实现步骤如下：

- 每个MemStore会实例化得到一个MemStoreLAB对象。
- MemStoreLAB会申请一个2M大小的Chunk数组，同时维护一个Chunk偏移量，该偏移量初始值为0。
- 当一个KeyValue值插入MemStore后，MemStoreLAB会首先通过KeyValue.getBuffer()取得data数组，并将data数组复制到Chunk数组中，之后再将Chunk偏移量往前移动data. length。
- 当前Chunk满了之后，再申请一个新的Chunk。

经过优化之后的内存布局是这样的：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650799517089-9fdffd48-e4df-44af-8412-daa79baa6d95.png)

## HFile

MemStore中数据落盘之后会形成一个文件写入HDFS，这个文件称为HFile。从HBase诞生到现在，HFile经历了3个版本，其中V2在0.92引入，V3在0.98引入。HFile V1版本在实际使用过程中发现占用内存过多，HFile V2版本针对此问题进行了优化，HFile V3版本和V2版本基本相同，只是在cell层面添加了对Tag数组的支持。

下面以V2版本为例进行介绍。HFile的物理结构如下图所示：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650800188240-72c66332-0344-4a85-9c97-735de0db8cdf.png)

HFile文件主要分为4个部分：Scanned block部分、Non-scanned block部分、Load-on-open部分和Trailer。

- **Scanned Block**部分：顾名思义，表示顺序扫描HFile时所有的数据块将会被读取。这个部分包含3种数据块：Data Block，Leaf Index Block以及BloomBlock。其中Data Block中存储用户的KeyValue数据，Leaf Index Block中存储索引树的叶子节点数据，Bloom Block中存储布隆过滤器相关数据。
- **Non-scanned Block**部分：**表示在HFile顺序扫描的时候不会被读取的数据**，主要包括Meta Block和Intermediate Level Data Index Blocks两部分。
- **Load-on-open**部分：这部分数据会在RegionServer打开HFile时直接加载到内存中，包括FileInfo、布隆过滤器MetaBlock、Root Data Index和MetaIndexBlock。
- **Trailer**部分：这部分主要记录了HFile的版本信息、其他各个部分的偏移值和寻址信息。

上图中可以发现，每个方格基本都是各种Block（数据块），Block是HBase中最小的数据读取单元。Block的大小可以在创建表列簇的时候通过参数blocksize=> '65535'指定，**默认为64K**。通常来讲，**大号的Block有利于大规模的顺序扫描，而小号的Block更有利于随机查询**。因此用户在设置blocksize时需要根据业务查询特征进行权衡，默认64K是一个相对折中的大小。

**HFile中所有Block都拥有相同的数据结构，HBase将所有Block统一抽象为HFileBlock。HFileBlock主要包含两部分：BlockHeader和BlockData。**其中BlockHeader主要存储Block相关元数据，BlockData用来存储具体数据。

抽象归抽象，最终还是得有实现。HBase中定义了8种BlockType，每种BlockType对应的Block都存储不同的内容，有的存储用户数据，有的存储索引数据，有的存储元数据（meta）。对于任意一种类型的HFileBlock，都拥有相同结构的BlockHeader，但是BlockData结构却不尽相同。下表列出了这8中类型：

![img](https://cdn.nlark.com/yuque/0/2022/png/12395255/1650800827862-ef0096ca-4ee0-4169-915b-70afd1169c9d.png)

每种类型就不再详细阐述了，不然篇幅太多了，详细的可以阅读文末推荐的参考资料。

## BlockCache

为了提升读取性能，HBase实现了一种读缓存结构——**BlockCache**。客户端读取某个Block，**首先会检查该Block是否存在于Block Cache，如果存在就直接加载出来，如果不存在则去HFile文件中加载，加载出来之后放到Block Cache中**，后续同一请求或者邻近数据查找请求可以直接从内存中获取，以避免昂贵的IO操作。

需要注意的是，BlockCache是RegionServer级别的，一个RegionServer只有一个BlockCache。到目前为止，HBase先后实现了3种BlockCache方案，LRUBlockCache是最早的实现方案，也是默认的实现方案；HBase 0.92版本实现了第二种方案SlabCache；HBase 0.96之后官方提供了另一种可选方案BucketCache。

这3种方案的不同之处主要在于内存管理模式，**其中LRUBlockCache是将所有数据都放入JVM Heap中**，交给JVM进行管理。而后两种方案采用的机制允许将部分数据存储在堆外。这种演变本质上是因为LRUBlockCache方案中JVM垃圾回收机制经常导致程序长时间暂停，而采用堆外内存对数据进行管理可以有效缓解系统长时间GC。

- **LRUBlockCache**：LRUBlockCache是HBase目前默认的BlockCache机制，实现相对比较简单。它使用一个ConcurrentHashMap管理BlockKey到Block的映射关系。
- **SlabCache**：为了解决LRUBlockCache方案中因JVM垃圾回收导致的服务中断问题，SlabCache方案提出使用Java NIO DirectByteBuffer技术实现堆外内存存储，不再由JVM管理数据内存。默认情况下，系统在初始化的时候会分配两个缓存区，**分别占整个BlockCache大小的80%和20%，每个缓存区分别存储固定大小的Block，其中前者主要存储小于等于64K的Block，后者存储小于等于128K的Block**，如果一个Block太大就会导致两个区都无法缓存。和LRUBlockCache相同，SlabCache也使用Least-Recently-Used算法淘汰过期的Block。和LRUBlockCache不同的是，**SlabCache淘汰Block时只需要将对应的BufferByte标记为空闲，后续cache对其上的内存直接进行覆盖即可**。
- **BucketCache**：BucketCache通过不同配置方式可以工作在三种模式下：heap，offheap和file。heap模式表示这些Bucket是从JVM Heap中申请的；offheap模式使用DirectByteBuffer技术实现堆外内存存储管理；file模式使用类似SSD的存储介质来缓存Data Block。



# HBase读写流程





# 性能调优

## 超时参数该怎么配？

先了解下HBase常见的几个超时参数：

- hbase.rpc.timeout：表示**单次RPC请求的超时时间**，一旦单次RPC超过该时间，上层将收到TimeoutException。默认为60000ms。
- hbase.client.retries.number：表示调用API时**最多容许发生多少次RPC重试操作**。默认为35次。
- hbase.client.pause：表示连续两次RPC重试之间的休眠时间，默认为100ms。

- - 注意，**HBase的重试休眠时间是按照随机退避算法计算的**，若hbase.client.pause=100，则第一次RPC重试前将休眠100ms左右，第二次RPC重试前将休眠200ms左右，第三次RPC重试前将休眠300ms左右，第四次重试前将休眠500ms左右，第五次重试前将休眠1000ms左右，第六次重试则将休眠2000ms左右……也就是重试次数越多，则休眠的时间会越长。因此，若按照默认的hbase.client.retries.number=35，则可能长期卡在休眠和重试两个步骤中。

- hbase.client.operation.timeout：表示单次API的超时时间，默认值为1200000ms。注意，get/put/delete等表操作称为一次API操作，**一次API可能会有多次RPC重试**，这个operation.timeout限制的是API操作的总超时。



**案例分析：假设某业务要求单次HBase的读请求延迟不超过1s，那么该如何设置上述4个超时参数呢？**

1. 首先，hbase.client.operation.timeout应该设成1s。
2. 其次，在SSD集群上，如果集群参数设置合适且集群服务正常，则基本可以保证p99延迟在100ms以内，因此hbase.rpc. timeout设成100ms。这里，hbase.client.pause用默认的100ms。
3. 最后，按照hbase.client.pause的算法逻辑，hbase.client.pause=100ms，1s内差不多能重试4次，所以hbase.client.retries.number可以设置为4。不过可以考虑适当增加一些，增大容错性，比如设置成6。也不宜设置过大，否则一次请求可能会卡在重试里，导致响应时间远超1s。



> 资料：《HBase原理与实践》，本文内容摘于本书，推荐大家亲自阅读