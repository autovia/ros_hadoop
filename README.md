**RosbagInputFormat** is an open source **splitable** Hadoop InputFormat for the rosbag file format.

# Usage from Spark (pyspark)

## Check that the rosbag file version is V2.0
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar --version -f HMB_1.bag
```

## Extract the index as configuration
The index is a very very small configuration file containing a protobuf array that will be given in the job configuration.

**Note** that the operation **will not** process and it **will not** parse the whole bag file, but will simply seek to the required offset.
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar -f HMB_1.bag
```

## Copy the bag file in HDFS

Using your favorite tool put the bag file in your working HDFS folder.

**Note:** keep the index json file as configuration to your jobs, **do not** put small files in HDFS.

Assuming your HDFS working folder is /user/spark/ (e.g. hdfs dfs -mkdir /user/spark/)
```bash
hdfs dfs -put data/HMB_1.bag
hdfs dfs -ls
```

## Process the ros bag file in Spark using the RosbagInputFormat

**Note:** your HDFS address might differ.
```python
fin = sc.newAPIHadoopFile(
    path =             "hdfs://127.0.0.1:9000/user/spark/HMB_1.bag",
    inputFormatClass = "de.valtech.foss.RosbagMapInputFormat",
    keyClass =         "org.apache.hadoop.io.LongWritable",
    valueClass =       "org.apache.hadoop.io.MapWritable",
    conf =             {"RosbagInputFormat.chunkIdx":"./HMB_1.bag.idx.bin"})
```

## Interpret the Messages
To interpret the messages we need the connections.

We could get the connections as configuration as well. At the moment we decided to collect the connections into Spark driver in a dictionary and use it in the subsequent RDD actions. Note in the next version of the RosbagInputFormater alternative implementations will be given.

### Collect the connections from all Spark partitions of the bag file into the Spark driver

```python
conn_a = fin.filter(lambda r: r[1]['header']['op'] == 7).map(lambda r: r[1]).collect()
conn_d = {str(k['header']['topic']):k for k in conn_a}
# see topic names
conn_d.keys()
```

### Load the python map functions from src/main/python/functions.py
```python
%run -i src/main/python/functions.py
```

### Use of msg_map to apply a function on all messages
Python **rosbag.bag** needs to be installed on all Spark workers.
The msg_map function (from src/main/python/functions.py) takes three arguments:
1. r = the message or RDD record Tuple
2. func = a function (default str) to apply to the ROS message
3. conn = a connection to specify what topic to process

```python
from functools import partial

# Take 3 messages from '/imu/data' topic using default str func
rdd.flatMap(
    partial(msg_map, conn=conn_d['/imu/data'])
).take(3)
```
