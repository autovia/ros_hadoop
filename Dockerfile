FROM ros

RUN apt-get update && apt-get install -y --no-install-recommends \
    locales bzip2 python-pip python3-pip python-setuptools python3-setuptools unzip xz-utils \
    openjdk-8-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

RUN pip2 install --upgrade pip && \
    pip3 install --upgrade pip && \
    pip3 install --no-cache-dir --upgrade jupyter && \
    pip2 install --no-cache-dir --upgrade matplotlib pandas tensorflow keras Pillow && \
    python2 -m pip install ipykernel && \
    python2 -m ipykernel install && \
    python3 -m pip install ipykernel && \
    python3 -m ipykernel install

# Default to UTF-8
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH $PATH:/opt/apache/hadoop/bin:/opt/apache/spark/bin
ENV PYSPARK_DRIVER_PYTHON jupyter
ENV PYSPARK_DRIVER_PYTHON_OPTS "notebook --allow-root --ip=0.0.0.0"
ENV ROSIF_JAR /opt/ros_hadoop/ros_hadoop-0.9.2/lib/rosbaginputformat_2.11-0.9.2.jar

ADD http://www-eu.apache.org/dist/hadoop/common/hadoop-2.8.0/hadoop-2.8.0.tar.gz /opt/apache/
ADD http://www-eu.apache.org/dist/spark/spark-2.2.0/spark-2.2.0-bin-hadoop2.7.tgz /opt/apache/
ADD https://xfiles.valtech.io/f/c494d168522045e3bcc0/?dl=1 /srv/data/HMB_4.bag
ADD https://github.com/valtech/ros_hadoop/archive/v0.9.2.tar.gz /opt/ros_hadoop/

RUN ln -s /opt/apache/hadoop-2.8.0 /opt/apache/hadoop && \
    ln -s /opt/apache/spark-2.2.0-bin-hadoop2.7 /opt/apache/spark

RUN printf "<configuration>\n<property>\n<name>fs.defaultFS</name>\n<value>hdfs://localhost:9000</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/core-site.xml && \
    printf "<configuration>\n<property>\n<name>dfs.replication</name>\n<value>1</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/hdfs-site.xml && \
    bash -c "/opt/apache/hadoop/bin/hdfs namenode -format 2>/dev/null" && \
    printf "#! /bin/bash\nset -e\nsource \"/opt/ros/$ROS_DISTRO/setup.bash\"\n/opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs start namenode\n/opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs start datanode\nexec \"\$@\"\n" > /ros_hadoop.sh && \
    chmod a+x /ros_hadoop.sh

RUN bash -c "/ros_hadoop.sh 2>/dev/null" && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user/root && \
    /opt/apache/hadoop/bin/hdfs dfs -put /srv/data/HMB_4.bag && \
    java -jar "$ROSIF_JAR" -f /srv/data/HMB_4.bag && \
    /opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs stop datanode && \
    /opt/apache/hadoop/sbin/hadoop-daemon.sh --script hdfs stop namenode

WORKDIR /opt/ros_hadoop/ros_hadoop-0.9.2/doc/
ENTRYPOINT ["/ros_hadoop.sh"]
CMD ["/opt/apache/spark/bin/pyspark","--num-executors","2","--driver-memory","5g","--executor-memory","8g","--jars=../lib/rosbaginputformat_2.11-0.9.2.jar,../lib/protobuf-java-3.3.0.jar"]

# PYSPARK_DRIVER_PYTHON=jupyter PYSPARK_DRIVER_PYTHON_OPTS="notebook --allow-root --ip=0.0.0.0" /opt/apache/spark/bin/pyspark --num-executors 2 --driver-memory 5g --executor-memory 8g --jars=../lib/rosbaginputformat_2.11-0.9.2.jar,../lib/protobuf-java-3.3.0.jar
