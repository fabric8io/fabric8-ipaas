ZooKeeper for Kubernetes
------------------------

This projects provides a ZooKeeper ensemble integration with Kubernetes. In particular:

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
 
So this is a fixed size ensemble of 3 servers.
    
