**RosbagInputFormat** is an open source **splitable** Hadoop InputFormat for the rosbag file format.

## Usage from Spark (pyspark)

### Check that the rosbag file version is V2.0
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar --version -f HMB_1.bag
```

### Compute the index
The index is a very very small configuration file containing a protobuf array that will be given in the job configuration. **Note** the operation **will not** process and it **will not** parse the whole bag file, but will simply seek to the required offset.
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar -f HMB_1.bag
```

### Copy the bag file in HDFS

Using your favorite tool put the bag file in your working HDFS folder. 
**Note:** keep the index json file as configuration to your jobs, **do not** put small files in HDFS.
Assuming your HDFS working folder is /user/spark/ (e.g. hdfs dfs -mkdir /user/spark/)
```bash
hdfs dfs -put data/HMB_1.bag data/
hdfs dfs -ls data/
```

### Process the ros bag file in Spark using the RosbagInputFormat
**Note:** your HDFS address might differ.
```python
fin = sc.newAPIHadoopFile(
    path =             "hdfs://127.0.0.1:9000/user/spark/HMB_1.bag", 
    inputFormatClass = "de.valtech.foss.RosbagMapInputFormat", 
    keyClass =         "org.apache.hadoop.io.LongWritable", 
    valueClass =       "org.apache.hadoop.io.MapWritable",
    conf =             {"RosbagInputFormat.chunkIdx":"./HMB_1.bag.idx.bin"})
```

### Interpret the Messages
To interpret the messages we need the connections. 
We could get the connections as configuration as well. At the moment we decided to collect the connections into Spark driver in a dictionary and use it in the subsequent RDD actions. Note in the next version of the RosbagInputFormater alternative implementations will be given
Collect the connections from all Spark partitions of the bag file into the Spark driver

```python
conn_d = {str(k['topic']):k for k in fin.filter(lambda r: r[1]['header']['op'] == 7).map(lambda r: r[1]['header']).collect()}

conn_d.keys()
```
