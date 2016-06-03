#!/bin/bash

echo This script is called by maven to deploy the secret after the maven validate phase.
echo Note that for this script to succeed you must oc login first

# deleting existing secret if there
oc get secret elasticsearch-v1-keystore | grep 'Opaque' &> /dev/null
if [ $? == 0 ]; then
  oc delete secret elasticsearch-v1-keystore
fi
# create the new secret
echo Deploying secret elasticsearch-v1-keystore
oc secrets new elasticsearch-v1-keystore keystore=target/secret/elasticsearch-v1/keystore keystore.password=target/secret/elasticsearch-v1/keystore.password

# oc create route passthrough elasticsearch-v1 --service elasticsearch-v1
