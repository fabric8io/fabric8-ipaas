#!/bin/bash

#This script is called by maven to deploy the secret after the maven validate phase.
#Note that for this script to succeed you must oc login first

if [ ! -f "../elasticsearch/target/secret/elasticsearch-v1/public.key" ]; then
    echo "[WARNING] Please run 'mvn -Pssl package' to create the elasticsearch public.key"
    echo "[WANRING] No 'apiman-gateway-keystore'secret is created"
else
    # deleting existing secret if there
    oc get secret apiman-gateway-keystore | grep 'Opaque' &> /dev/null
    if [ $? == 0 ]; then
      echo [INFO] Delete existing secret/apiman-gateway-keystore
      oc delete secret apiman-gateway-keystore
    fi
    
    truststorePw=`cat target/secret/apiman-gateway/truststore.password`
    
    if [ -f "target/secret/apiman-gateway/truststore" ]; then
      rm target/secret/apiman-gateway/truststore
    fi
    
    echo [INFO] Import Elasticsearch \"public.key\" into truststore
    keytool -noprompt -importcert -keystore target/secret/apiman-gateway/truststore \
            -alias elasticsearch-v1 \
            -file ../elasticsearch/target/secret/elasticsearch-v1/public.key \
            -storepass $truststorePw 
    
    # create the new secret
    echo [INFO]Create new secret/apiman-gateway-keystore
    oc secrets new apiman-gateway-keystore \
         gateway.user=target/secret/apiman-gateway/gateway.user \
         apiman-gateway.properties=target/secret/apiman-gateway/apiman-gateway.properties \
         keystore=target/secret/apiman-gateway/keystore \
         keystore.password=target/secret/apiman-gateway/keystore.password \
         truststore=target/secret/apiman-gateway/truststore \
         truststore.password=target/secret/apiman-gateway/truststore.password
    #     client.keystore=target/secret/apiman-gateway/client.keystore \
    #     client.keystore.password=target/secret/apiman-gateway/client.keystore.password
    
    # oc create route passthrough apiman-gateway --service apiman-gateway

fi
