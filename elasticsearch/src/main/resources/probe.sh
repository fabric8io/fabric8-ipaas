#!/bin/bash

echo $1

response=$(curl -k $1://localhost:9200) 

echo $response

if [[ $response == *"status\" : 200"* ]]; then
    echo 'ok'
    exit 0;
fi

echo 'nok'
exit 1
