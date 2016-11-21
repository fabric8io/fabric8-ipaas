# Cassandra Server QuickStart

This quickstart exposes a single node Cassandra cluster. The configuration is inspired to the [Kubernetes Cassandra example](https://github.com/kubernetes/kubernetes/tree/master/examples/storage/cassandra).
This deployment is persisted with a Persistent Volume, this is why it's just single node for the moment.
We will use StatefulSet (PetSet) Kubernetes' feature to get the same result in a multi-node Cluster, when the feature will become stable.

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

When the deployment is done don't forget to run

    gofabric8 volume 

this way the Persistent Volume related to the Persistent Volume Claim of the deployment will be created.

### Populate the Cassandra keyspace

It is assumed a running Kubernetes platform is already running. If not you can find details how to [get started](http://fabric8.io/guide/getStarted/index.html).

To list all the pods:

    oc get pods

If you want to use the Cassandra client console you can run the following command (where pod_id is the id of one of the pod in the cluster):

    oc exec <pod_id> -it cqlsh

You can watch the status of your Cluster with the following command:

    oc exec <pod_id> -it nodetool status

You should see an output like this:

```shell
Datacenter: datacenter1
=======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address      Load       Tokens       Owns (effective)  Host ID                               Rack
UN  172.17.0.11  110.87 KiB  32           100.0%            c1e68ff1-8626-468d-905c-6715c39531cf  rack1

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
