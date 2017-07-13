FROM ros

RUN apt-get update && apt-get install -y --no-install-recommends \
    locales bzip2 python-pip python3-pip python-setuptools python3-setuptools unzip xz-utils \
    openjdk-8-jdk-headless \
    && rm -rf /var/lib/apt/lists/* 

RUN pip2 install --upgrade pip && \
    pip3 install --upgrade pip && \
    pip3 install --no-cache-dir --upgrade jupyter pandas tensorflow keras && \
    python2 -m pip install ipykernel && \
    python2 -m ipykernel install && \
    python3 -m pip install ipykernel && \
    python3 -m ipykernel install

# Default to UTF-8
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH $PATH:/opt/apache/hadoop/bin:/opt/apache/spark/bin

ADD http://www-eu.apache.org/dist/hadoop/common/hadoop-2.8.0/hadoop-2.8.0.tar.gz /opt/apache/
ADD http://www-eu.apache.org/dist/spark/spark-2.2.0/spark-2.2.0-bin-hadoop2.7.tgz /opt/apache/

RUN ln -s /opt/apache/hadoop-2.8.0 /opt/apache/hadoop && \
    ln -s /opt/apache/spark-2.2.0-bin-hadoop2.7 /opt/apache/spark

RUN printf "<configuration>\n<property>\n<name>fs.defaultFS</name>\n<value>hdfs://localhost:9000</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/core-site.xml && \
    printf "<configuration>\n<property>\n<name>dfs.replication</name>\n<value>1</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/hdfs-site.xml && \
    /opt/apache/hadoop/bin/hdfs namenode -format && \
    printf "#! /bin/bash\nset -e\nsource \"/opt/ros/$ROS_DISTRO/setup.bash\"\n/opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs start namenode\n/opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs start datanode\nexec \"\$@\"\n" > /ros_hadoop.sh && \
    chmod a+x /ros_hadoop.sh

RUN /ros_hadoop.sh && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user/root && \
    /opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs stop datanode && \
    /opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs stop namenode

WORKDIR /root
ENTRYPOINT ["/ros_hadoop.sh"]
CMD ["bash"]

# PYSPARK_DRIVER_PYTHON=jupyter PYSPARK_DRIVER_PYTHON_OPTS="notebook --allow-root --ip=0.0.0.0" /opt/apache/spark/bin/pyspark --num-executors 2 --driver-memory 5g --executor-memory 8g --jars=../lib/rosbaginputformat_2.11-0.9.0-SNAPSHOT.jar,../lib/protobuf-java-3.3.0.jar

