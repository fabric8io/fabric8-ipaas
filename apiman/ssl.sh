#!/bin/bash

# creating the cert
mvn clean compile -Pssl
# deleting existing secret
oc delete secret apiman-keystore
# setting the secret
oc secrets new apiman-keystore keystore=target/secret/apiman/keystore keystore-password=target/secret/apiman/keystore-password
# oc label secret/apiman-keystore apiman-infra # make them easier to delete later
# mvn -Pf8-local-deploy -Pssl
# oc delete route apiman
# oc create route passthrough apiman --service apiman
