# Cassandra Server QuickStart

This quickstart exposes a 3 nodes Cassandra cluster. The configuration is inspired to the [Kubernetes Cassandra example](https://github.com/kubernetes/kubernetes/tree/master/examples/storage/cassandra)

### Building

The example can be built with

    mvn clean install

### Running the example in Kubernetes

It is assumed a running Kubernetes platform is already running. If not you can find details how to [get started](http://fabric8.io/guide/getStarted/index.html).

The example can be built and deployed using a single goal:

    mvn fabric8:run

When the example runs in fabric8, you can use the OpenShift client tool to inspect the status

To list all the running pods:

    oc get pods

Then find the name of the pod that runs this quickstart, and output the logs from the running pods with:

    oc logs <name of pod>

You can also use the fabric8 [web console](http://fabric8.io/guide/console.html) to manage the
running pods, and view logs and much more.

### Populate the Cassandra keyspace

It is assumed a running Kubernetes platform is already running. If not you can find details how to [get started](http://fabric8.io/guide/getStarted/index.html).

To list all the pods:

    oc get pods

Use the name of one of the Cassandra pods in your list and run the following command:

    oc exec <pod_id> -it cqlsh

As input use the cql code in `src/main/resources/cql/users.cql`. This way you've created the keyspace for using the cassandra-client code.
You can watch the status of your Cluster with the following command:

    oc exec <pod_id> -it nodetool status

You should see an output like this:

```shell
Datacenter: datacenter1
=======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address      Load       Tokens       Owns (effective)  Host ID                               Rack
UN  172.17.0.9   69.99 KB   32           100.0%            e308d4c5-4d53-4770-8bc4-41edb3159031  rack1
UN  172.17.0.8   95.93 KB   32           100.0%            b690159d-3079-44a8-9e58-8ea2dc8877bb  rack1
UN  172.17.0.10  90.11 KB   32           100.0%            69467200-c202-42a0-b790-21c677863158  rack1
```

You can also scale up or scale down your cluster:

    oc scale --replicas=<replicas_num> rc/cassandra

### Access services using a web browser

When the application is running, you can use a web browser to access the HTTP service. Assuming that you
have a [Vagrant setup](http://fabric8.io/guide/getStarted/vagrant.html) you can access the application with
`http://war-wildfly-default.vagrant.f8/`.

Notice: As it depends on your OpenShift setup, the hostname (route) might vary. Verify with `oc get routes` which
hostname is valid for you.

### More details

You can find more details about running this [quickstart](http://fabric8.io/guide/quickstarts/running.html) on the website. This also includes instructions how to change the Docker image user and registry.
