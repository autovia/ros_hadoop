# **RosbagInputFormat**
RosbagInputFormat is an open source **splittable** Hadoop InputFormat for the ROS bag file format.

# Usage

1. Download latest release jar file and put it in classpath
2. Extract the index configuration of your ROS bag file e.g.
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0.jar -f /srv/data/HMB_4.bag
# will create an idx.bin config file /srv/data/HMB_4.bag.idx.bin
```
3. Put the ROS bag file in HDFS e.g.
```bash
hdfs dfs -put
```
4. Use it in your jobs e.g.
```python
sc.newAPIHadoopFile(
    path =             "hdfs://127.0.0.1:9000/user/spark/HMB_4.bag",
    inputFormatClass = "de.valtech.foss.RosbagMapInputFormat",
    keyClass =         "org.apache.hadoop.io.LongWritable",
    valueClass =       "org.apache.hadoop.io.MapWritable",
    conf =             {"RosbagInputFormat.chunkIdx":"/srv/data/HMB_4.bag.idx.bin"})
```

**The extracted index is a very very small configuration** file containing a protobuf array that will be given in the job configuration. **Note that the operation will not process and it will not parse** the whole bag file, but will simply seek to the required offset.

Example data can be found for instance at https://github.com/udacity/self-driving-car/tree/master/datasets published under MIT License.

# Documentation
The [doc/](doc/) folder contains a jupyter notebook with a few basic usage examples.

<p align="center"><img src="doc/images/concept.png" height="350">
</p>

# Demo

## To test locally use the Dockerfile

To build an image using the Dockerfile run the following in the shell.
Please note that it will download Hadoop and Spark from the URL source. The generated image is therefor relatively large ~5G.
```bash
docker build -t ros_hadoop:latest -f Dockerfile .
```

To start a container use the following shell command **in the ros_hadoop folder.**
```bash
# $(pwd) will point to the ros_hadoop git clone folder
docker run -it -v $(pwd):/root/ros_hadoop -p 8888:8888 ros_hadoop
```
The container has a configured HDFS as well as Spark and the RosInputFormat jar.
It leaves the user in a bash shell.

Point your browser to the local [URL](http://localhost:8888/) and enjoy the tutorial. The access token is printed in the docker container console.

### Interpret the Messages

To interpret the messages we need the connections.

We could get the connections as configuration as well. At the moment we decided to collect the connections into Spark driver in a dictionary and use it in the subsequent RDD actions. **Note** that in the next version of the RosbagInputFormater alternative implementations will be given.

Collect the connections from all Spark partitions of the bag file into the Spark driver.
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

Python rosbag.bag needs to be installed on all Spark workers. The msg_map function (from src/main/python/functions.py) takes three arguments:

r = the message or RDD record Tuple
func = a function (default str) to apply to the ROS message
conn = a connection to specify what topic to process

```python
from functools import partial

# Take 3 messages from '/imu/data' topic using default str func
rdd = fin.flatMap(
    partial(msg_map, conn=conn_d['/imu/data'])
)

rdd.take(3)
```

## Please do not forget to send us your [feedback](AUTHORS).
![doc/images/browse-tutorial.png](doc/images/browse-tutorial.png)
