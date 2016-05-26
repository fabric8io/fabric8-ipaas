#!/bin/bash

# creating the cert
mvn clean compile -Pssl
# deleting existing secret
oc delete secret apiman-gateway-keystore
# setting the secret
oc secrets new apiman-gateway-keystore keystore=target/secret/apiman-gateway/keystore keystore-password=target/secret/apiman-gateway/keystore.password
# oc label secret/apiman-gateway-keystore apiman-infra # make them easier to delete later
# mvn -Pf8-local-deploy -Pssl
# oc delete route apiman-gateway
# oc create route passthrough apiman-gateway --service apiman-gateway
