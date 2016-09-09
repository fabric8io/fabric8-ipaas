FROM docker.io/java:8u40

MAINTAINER iocanel@gmail.com

USER root

ENV ZOOKEEPER_VERSION 3.4.8
EXPOSE 2181 2888 3888

RUN apt-get update && apt-get install -y wget \
    && wget -q -O - http://apache.mirrors.pair.com/zookeeper/zookeeper-${ZOOKEEPER_VERSION}/zookeeper-${ZOOKEEPER_VERSION}.tar.gz | tar -xzf - -C /opt \
    && mv /opt/zookeeper-${ZOOKEEPER_VERSION} /opt/zookeeper \
    && cp /opt/zookeeper/conf/zoo_sample.cfg /opt/zookeeper/conf/zoo.cfg \
    && mkdir -p /opt/zookeeper/{data,log}

WORKDIR /opt/zookeeper
VOLUME ["/opt/zookeeper/conf", "/opt/zookeeper/data", "/opt/zookeeper/log"]

COPY start.sh ./bin/
COPY zoo.cfg ./conf/

CMD ["/opt/zookeeper/bin/start.sh"]
