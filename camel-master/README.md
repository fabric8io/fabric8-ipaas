## Camel master component

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.camel/camel-master/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8.ipaas.camel/camel-master/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8.ipaas.camel/camel-master.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8.ipaas.camel/camel-master)


This library implements the `master` endpoint so that only one single pod can consume from a given named endpoint at once.

This allows you to run many pods for high availability but only a single pod can consume from the underlying Camel endpoint.

e.g.

```java
from("master:cheese:seda:beer").to("seda:something");
```

Then the lock called `cheese` will be acquired by a single pod in Kubernetes and the pod with the lock will really consume from the endpoint `seda:beer`. All other pods will wait for the lock.

You can have many named locks and use them across different pods.

### Using camel-master on OpenShift

Things should just work OOTB on Kubernetes. However When using OpenShift everything has fine grained access control with access disabled by default.

So you will need to 

1. create a ServiceAccount to your app that's using camel-master
2. associate the ServiceAccount with your Pods
3. [enable access for your Service Account](https://docs.openshift.com/enterprise/3.1/dev_guide/service_accounts.html) so that it can read and write ConfigMap resources

For (3) to enable edit role on your ServiceAccount type a command line this:
 

```
oc policy add-role-to-user edit system:serviceaccount:cheese:robot    
```

where `robot` is the name of your ServiceAccount and `cheese` is the project you are using in OpenShift.


#### Creating the ServiceAccount and associating it with your Pods

When using the [fabric8-maven-plugin](https://github.com/fabric8io/fabric8-maven-plugin) 3.x or later you can create a Service Account for your app by creating a file called `src/main/fabric8/sa.yml` like this:

``` yaml
metadata:
  name: robot
```

where `robot` is the name of the ServiceAccount you want to create. This would probably be `${project.artifactId}` by default to reuse the same name as the project artifact.

Then to associate the pods with this ServiceAccount add a file called `src/main/fabric8/deployment.yml` that looks like this:

```yaml
spec:
  template:
    spec:
      serviceAccount: robot
```


If you are using the older 2.x version of fabric8-maven-plugin then you can add these maven properties to your pom.xml:
 
```xml
    <fabric8.serviceAccount>robot</fabric8.serviceAccount>
    <fabric8.serviceAccountCreate>true</fabric8.serviceAccountCreate>
``` 

#### Testing the ServiceAccount is properly created

Once your app is deployed in OpenShift you should be able to view your rc and see the serviceAccount

```
$ oc export rc myapp | grep serviceAccount
      serviceAccount: robot
      serviceAccountName: robot
```

which should show that `myapp` has the associated ServiceAccount of `robot`. If not its likely the generated YAML/JSON doesn't have the `serviceAccount` configuration.


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
    
    