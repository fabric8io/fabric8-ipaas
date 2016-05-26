#!/bin/bash

# creating the cert
mvn clean compile -Pssl
# deleting existing secret
oc delete secret apiman-gateway-keystore
# setting the secret
oc secrets new apiman-gateway-keystore keystore=target/secret/apiman-gateway/keystore keystore-password=target/secret/apiman-gateway/keystore.password
# oc label secret/apiman-gateway-keystore apiman-infra # make them easier to delete later

# Copy the Gateway users file
# vagrant plugin install vagrant-scp
# vagrant ssh
# mkdir apiman-gateway
# vagrant scp fabric8-ipaas/apiman-gateway/data/gateway.user :/home/vagrant/apiman-gateway/gateway.user

# mvn -Pf8-local-deploy -Pssl

# oc delete route apiman-gateway
# oc create route passthrough apiman-gateway --service apiman-gateway
