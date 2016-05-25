FROM docker.io/java:8u40

MAINTAINER iocanel@gmail.com

USER root

ENV SCALA_VERSION 2.11
ENV KAFKA_VERSION 0.9.0.1
EXPOSE 9092

RUN apt-get update && apt-get install -y wget \
    && wget -q -O - http://apache.mirrors.pair.com/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz | tar -xzf - -C /opt \
    && mv /opt/kafka_${SCALA_VERSION}-${KAFKA_VERSION} /opt/kafka

WORKDIR /opt/kafka
VOLUME ["/opt/kafka/config", "/opt/kafka/logs"]

COPY server.properties ./config/

CMD ["/opt/kafka/bin/kafka-server-start.sh", "/opt/kafka/config/server.properties"]
