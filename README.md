## Fabric8 iPaaS 

This repository contains the iPaaS related apps that can be run as part of the fabric8 platform on any OpenShift v3 and Kubernetes environment.


### Fabric8 Messaging

To implement scalable messaging on the iPaaS we use 2 microservices:

* [message-broker](message-broker) represents a scalable Replication Controller of message broker Pods using Apache ActiveMQ Artemis which supports JMS 2.0 and various protocols like AMQP, OpenWire, MQTT and STOMP on a single port, 61616 so its easier to reuse Kubernetes Services and OpenShift's external router. 
* [message-gateway](message-gateway) performs discovery, load balancing and sharding of message Destinations across the pool of message brokers to provide linear scalability of messaging.
 
The Message Gateway implements a service, `activemq` on port 61616 so any messaging application can just connect to `tcp://activemq:61616` and use any of the messaging protocols supported. 

#### Using JMS

If you are using JMS then if you use the [mq-client](mq-client) library the **io.fabric8.mq.core.MQConnectionFactory** class will automatically default to using the `activemq` message service for scalable messaging.

#### Using Camel

If you use the [camel-amq](camel-amq) library and `amq:` component it will automically default to using the `activemq` message service for scalable messaging.

#### Use a Message Gateway Sidecar

If your application wishes to avoid a network hop between your container and the Message Gateway you can just add the Message Gateway container into your Pod; then your container can connect on `tcp://localhost:61616` to perform messaging with the gateway taking care of communicating with the correct broker pods based on the destinations you use.