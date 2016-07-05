## Camel master component

This library implements the `master` endpoint so that only one single pod can consume from a given named endpoint at once.

This allows you to run many pods for high availability but only a single pod can consume from the underlying Camel endpoint.

e.g.

```
from("master:cheese:seda:beer").to("seda:something");
```

Then the lock called `cheese` will be acquired by a single pod in Kubernetes and the pod with the lock will really consume from the endpoint `seda:beer`. All other pods will wait for the lock.

You can have many named locks and use them across different pods.


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
    
    