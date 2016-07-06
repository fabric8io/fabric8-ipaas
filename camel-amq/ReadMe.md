## Camel AMQ Component

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.mq/camel-amq/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.mq/camel-amq/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8.ipaas.mq/camel-amq.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8.ipaas.mq/camel-amq)

The Camel **amq:** component uses the [Kubernetes Service](http://fabric8.io/guide/services.html) discovery mechanism to discover and connect to the ActiveMQ brokers. So you can just use the endpoint directly; no configuration is required.

e.g. just use the camel endpoint **"amq:Cheese"** to access the Cheese queue in ActiveMQ; no configuration required!

The Camel **amq:** is used in the example **Fabric8 MQ Producer** and **Fabric8 MQ Consumer** apps in the **Library** in the [console](http://fabric8.io/guide/console.html)

For more information check out the [Fabric8 Messaging documentation](http://fabric8.io/guide/fabric8MQ.html)

