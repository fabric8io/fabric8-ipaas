#!/bin/bash

#This script is called by maven to deploy the secret after the maven validate phase.
#Note that for this script to succeed you must oc login first

oc whoami > /dev/null
if [ $? == 1 ]; then
  echo "[ERROR] Not logged in, no 'elasticsearch-v1-keystore' secret is created"
  exit 0;
fi

# deleting existing secret if there
oc get secret elasticsearch-v1-keystore &> /dev/null
if [ $? == 0 ]; then
  echo "[INFO] Delete existing secret/elasticsearch-v1-keystore"
  oc delete secret elasticsearch-v1-keystore
fi
# create the new secret
echo Deploying secret elasticsearch-v1-keystore
oc secrets new elasticsearch-v1-keystore keystore=target/secret/elasticsearch-v1/keystore keystore.password=target/secret/elasticsearch-v1/keystore.password

# oc create route passthrough elasticsearch-v1 --service elasticsearch-v1
