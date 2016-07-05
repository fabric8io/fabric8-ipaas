## Camel master component

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.camel/camel-master/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.camel/camel-master/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8.ipaas.camel/camel-master.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8.ipaas.camel/camel-master)


This library implements the `master` endpoint so that only one single pod can consume from a given named endpoint at once.

This allows you to run many pods for high availability but only a single pod can consume from the underlying Camel endpoint.

e.g.

```
from("master:cheese:seda:beer").to("seda:something");
```

Then the lock called `cheese` will be acquired by a single pod in Kubernetes and the pod with the lock will really consume from the endpoint `seda:beer`. All other pods will wait for the lock.

You can have many named locks and use them across different pods.


### How it works

Under the covers the component uses a Kubernetes [ConfigMap](http://kubernetes.io/docs/user-guide/configmap/) called `camel-master-config` by default which is used to store which pods own which locks. (You can configure the ConfigMap name on the MasterComponent if you wish to use another name). 

Each master endpoint instance will try grab the lock for the named endpoint in the ConfigMap on startup. If the look cannot be grabbed then the ConfigMap and the owner pod are watched. If either changed then each pod will try to reacquire the lock again.

You can see a summary of which pods own which locks via the command line:

     kubectl get configmap camel-master-config -oyaml
             
The `data` section should show the endpoint names -> pod names for the owners.

e.g.

    james$ kubectl get configmap camel-master-config -oyaml
    apiVersion: v1
    data:
      foo: somepod-1234
      bar: cheese-5678
    kind: ConfigMap
    metadata:
    ...

In the above output we can see that the lock (endpoint name) `foo` is owned by pod name `somepod-1234`.

### Building the code

If you want to build and try the code locally use:

    mvn install
    
To try the integration tests, you'll need a Kubernetes or OpenShift cluster running so that on the command line you can type
    
    kubectl get pods
    
Or if you use OpenShift then
    
    oc get pods
    
Then you can run the integration tests via:
    
    export HOSTNAME=myPodName
    mvn test -Pitest
    
    