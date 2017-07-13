# **RosbagInputFormat**
RosbagInputFormat is an open source **splitable** Hadoop InputFormat for the Rosbag file format.

# Usage from Spark (pyspark)
Example data can be found for instance at https://github.com/udacity/self-driving-car/tree/master/datasets published under MIT License.

## The documentation
The ./doc folder contains a jupyter notebook with a few basic usage examples.

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

## In the container shell

### Check that the rosbag file version is V2.0
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar --version -f HMB_1.bag
```

### Extract the index as configuration
The index is a very very small configuration file containing a protobuf array that will be given in the job configuration.

Note that the operation will not process and it will not parse the whole bag file, but will simply seek to the required offset.
```bash
java -jar lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar -f HMB_1.bag
```

In the container shell copy your .bag file in HDFS using your favorite tool.
```bash
# Example data can be found for instance at https://github.com/udacity/self-driving-car/tree/master/datasets published under MIT License.
cd /root/ros_hadoop

hdfs dfs -put HMB_1.bag

# Extract the index to be used in the RosInputFormat configuration

```

Now you can start the jupyter notebook in the container shell.
```bash
cd /root/ros_hadoop

PYSPARK_DRIVER_PYTHON=jupyter PYSPARK_DRIVER_PYTHON_OPTS="notebook --allow-root --ip=0.0.0.0" /opt/apache/spark/bin/pyspark --num-executors 2 --driver-memory 5g --executor-memory 8g --jars=../lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar,../lib/protobuf-java-3.3.0.jar
```

Point your browser to the local URL and enjoy the tutorial.

## Please do not forget to send us your feedback.
![doc/images/browse-tutorial.png](doc/images/browse-tutorial.png)
