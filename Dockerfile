FROM ros:kinetic

RUN apt-get update && apt-get install -y --no-install-recommends \
    locales bzip2 tree unzip xz-utils curl wget iproute2 \
    python-pip python3-pip python-setuptools python3-setuptools \
    openjdk-8-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

RUN python2 -m pip install --upgrade pip && \
    python3 -m pip install --upgrade pip && \
    pip3 install --no-cache-dir --upgrade jupyter && \
    pip2 install --no-cache-dir --upgrade pyspark matplotlib pandas tensorflow keras Pillow && \
    python2 -m pip install ipykernel && \
    python2 -m ipykernel install && \
    python3 -m pip install ipykernel && \
    python3 -m ipykernel install

RUN pip2 install seaborn gmaps
RUN jupyter nbextension enable --py gmaps

RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 66F84AE1EB71A8AC108087DCAF677210FF6D3CDA && \
    bash -c 'echo "deb [ arch=amd64 ] http://packages.dataspeedinc.com/ros/ubuntu $(lsb_release -sc) main" > /etc/apt/sources.list.d/ros-dataspeed-public.list' && \
    apt-get update

RUN bash -c 'echo "yaml http://packages.dataspeedinc.com/ros/ros-public-'$ROS_DISTRO'.yaml '$ROS_DISTRO'" > /etc/ros/rosdep/sources.list.d/30-dataspeed-public-'$ROS_DISTRO'.list' && \
    rosdep update 2>/dev/null && apt-get install -y --no-install-recommends \
      ros-$ROS_DISTRO-dbw-mkz ros-$ROS_DISTRO-mobility-base ros-$ROS_DISTRO-baxter-sdk ros-$ROS_DISTRO-velodyne && \
    rm -rf /var/lib/apt/lists/*

# Default to UTF-8
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH $PATH:/opt/apache/hadoop/bin
ENV ROSIF_JAR /opt/ros_hadoop/master/lib/rosbaginputformat.jar

RUN mkdir -p /opt/ros_hadoop/latest
RUN mkdir -p /opt/ros_hadoop/master/dist/
RUN mkdir -p /opt/apache/
ADD . /opt/ros_hadoop/master
RUN bash -c "curl -s https://api.github.com/repos/valtech/ros_hadoop/releases/latest | egrep -io 'https://api.github.com/repos/valtech/ros_hadoop/tarball/[^\"]*' | xargs wget --quiet -O /opt/ros_hadoop/latest.tgz"
RUN bash -c "if [ ! -f /opt/ros_hadoop/master/dist/hadoop-3.0.2.tar.gz ] ; then wget --quiet -O /opt/ros_hadoop/master/dist/hadoop-3.0.2.tar.gz http://www.eu.apache.org/dist/hadoop/common/hadoop-3.0.2/hadoop-3.0.2.tar.gz ; fi"
RUN tar -xzf /opt/ros_hadoop/latest.tgz -C /opt/ros_hadoop/latest --strip-components=1 && rm /opt/ros_hadoop/latest.tgz
RUN tar -xzf /opt/ros_hadoop/master/dist/hadoop-3.0.2.tar.gz -C /opt/apache && rm /opt/ros_hadoop/master/dist/hadoop-3.0.2.tar.gz

RUN ln -s /opt/apache/hadoop-3.0.2 /opt/apache/hadoop 
RUN bash -c "if [ ! -f /opt/ros_hadoop/latest/lib/rosbaginputformat.jar ] ; then ln -s /opt/ros_hadoop/master/lib/rosbaginputformat.jar /opt/ros_hadoop/latest/lib/rosbaginputformat.jar ; fi"

RUN printf "<configuration>\n\n<property>\n<name>fs.defaultFS</name>\n<value>hdfs://localhost:9000</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/core-site.xml && \
    printf "<configuration>\n<property>\n<name>dfs.replication</name>\n<value>1</value>\n</property>\n</configuration>" > /opt/apache/hadoop/etc/hadoop/hdfs-site.xml && \
    bash -c "/opt/apache/hadoop/bin/hdfs namenode -format 2>/dev/null" && \
    printf "#! /bin/bash\n/opt/apache/hadoop/bin/hdfs --daemon stop datanode\n/opt/apache/hadoop/bin/hdfs --daemon stop namenode\n/opt/apache/hadoop/bin/hdfs --daemon start namenode\n/opt/apache/hadoop/bin/hdfs --daemon start datanode\nexec \"\$@\"\n" > /start_hadoop.sh && \
    chmod a+x /start_hadoop.sh

RUN printf "#! /bin/bash\nset -e\nsource \"/opt/ros/$ROS_DISTRO/setup.bash\"\n/start_hadoop.sh\nexec \"\$@\"\n" > /ros_hadoop.sh && \
    chmod a+x /ros_hadoop.sh

RUN bash -c "if [ ! -f /opt/ros_hadoop/master/dist/HMB_4.bag ] ; then wget --quiet -O /opt/ros_hadoop/master/dist/HMB_4.bag https://xfiles.valtech.io/f/c494d168522045e3bcc0/?dl=1 ; fi" && \
    java -jar "$ROSIF_JAR" -f /opt/ros_hadoop/master/dist/HMB_4.bag

RUN bash -c "/start_hadoop.sh" && \
    /opt/apache/hadoop/bin/hdfs dfsadmin -safemode wait && \
    /opt/apache/hadoop/bin/hdfs dfsadmin -report && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user && \
    /opt/apache/hadoop/bin/hdfs dfs -mkdir /user/root && \
    /opt/apache/hadoop/bin/hdfs dfs -put /opt/ros_hadoop/master/dist/HMB_4.bag && \
    /opt/apache/hadoop/bin/hdfs --daemon stop datanode && \
    /opt/apache/hadoop/bin/hdfs --daemon stop namenode

WORKDIR /opt/ros_hadoop/latest/doc/
ENTRYPOINT ["/ros_hadoop.sh"]
CMD ["jupyter", "notebook", "--allow-root", "--ip=0.0.0.0"]
