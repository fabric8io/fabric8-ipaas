#!/bin/bash

set -e

chmod a+w -R /usr/share/elasticsearch/bin

echo 'Second argument is ' $2

if [ $2 = 'ssl' ]; then
  /usr/share/elasticsearch/bin/plugin -i com.floragunn/search-guard/0.5.1 -url https://github.com/lukas-vlcek/origin-aggregated-logging/releases/download/v0.1/search-guard-0.5.1.zip
  cat <<EOF >> /usr/share/elasticsearch/config/elasticsearch.yml

network.bind_host: 0.0.0.0
searchguard.authentication.settingsdb.user.admin: supersecret
searchguard.authentication.authorization.settingsdb.roles.admin: ["admin"]
searchguard:
  allow_all_from_loopback: true
  ssl:
    transport:
      http:
        enabled: true
        keystore_type: JKS 
        keystore_filepath: /secret/elasticsearch-v1/keystore
        keystore_password: supersecret
        enforce_clientauth: false
        truststore_type: JKS
        truststore_filepath: /secret/elasticsearch-v1/keystore
        truststore_password: supersecret
EOF
fi

cd /usr/share/elasticsearch/bin

# Add elasticsearch as command if needed
if [ "${1:0:1}" = '-' ]; then
    set -- elasticsearch "$1"
fi

# Drop root privileges if we are running elasticsearch
if [ "$1" = 'elasticsearch' ]; then
    # Change the ownership of /usr/share/elasticsearch/data to elasticsearch
    chown -R elasticsearch:elasticsearch /usr/share/elasticsearch/data
    exec gosu elasticsearch "$1"
fi

# As argument is not related to elasticsearch,
# then assume that user wants to run his own process,
# for example a `bash` shell to explore this image
exec "$1"
