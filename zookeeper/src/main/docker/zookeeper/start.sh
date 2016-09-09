#!/bin/bash

echo "Starting up in standalone mode"
exec /opt/zookeeper/bin/zkServer.sh start-foreground
