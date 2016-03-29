ZooKeeper for Kubernetes
------------------------

This projects provides ZooKeeper integration with Kubernetes. In particular:

-   A Docker image for ZooKeeeper
-   Kubernetes resources for a ZooKeeper ensemble.
  

## Building

### Using the docker binaries
    
    cd src/main/docker/zookeeper
    docker build -t fabric8/zookeeper .


### Using the docker maven plugin

    mvn clean package docker:build

## Deploying
    
    mvn clean package fabric8:apply
    
    
### Setting the ensemble size

ZooKeeper requires that servers know each other upfront and does not support dynamic reconfiguration (yet). This means that the concepts of the replication controller are not fully applicable to ZooKeeper.  In other words you can't scale up or down a ZooKeeper ensemble just by increasing or decreasing the replicas of a controller.
 
So in order the size of the ensemble needs to be configured up front, for example:
  
    mvn clean package fabric8.apply -Densemble.size=3
    mvn clean package fabric8.apply -Densemble.size=5
    mvn clean package fabric8.apply -Densemble.size=7
    
Using an **ensemble.size=1** which is the default value will create a single/standalone zookeeper server.    
    
    