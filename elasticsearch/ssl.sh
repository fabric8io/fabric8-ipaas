#!/bin/bash

# deleting existing secret if there
oc get secret elasticsearch-v1-keystore | grep 'Opaque' &> /dev/null
if [ $? == 0 ]; then
  oc delete secret elasticsearch-v1-keystore
fi
# create the new secret
oc secrets new elasticsearch-v1-keystore keystore=target/secret/elasticsearch-v1/keystore keystore-password=target/secret/elasticsearch-v1/keystore-password

# oc create route passthrough elasticsearch-v1 --service elasticsearch-v1
