/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.msg.gateway.brokers.impl;

import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.DestinationMapper;

import javax.jms.Destination;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A toy simple example which only works for 1 in memory based message gateway
 */
public class MemorySingletonDestinationMapper implements DestinationMapper {
    private Map<Destination, ArtemisClient> destinationArtemisClientMap = new ConcurrentHashMap<>();

    @Override
    public ArtemisClient getConsumer(Destination destination, Callable<ArtemisClient> factoryCallback) throws Exception {
        return getProducer(destination, factoryCallback);
    }

    @Override
    public ArtemisClient getProducer(Destination destination, Callable<ArtemisClient> factoryCallback) throws Exception {
        ArtemisClient result = destinationArtemisClientMap.get(destination);
        if (result == null) {
            result = factoryCallback.call();
            if (result != null) {
                ArtemisClient artemisClient = destinationArtemisClientMap.putIfAbsent(destination, result);
                if (artemisClient != null) {
                    result = artemisClient;
                }
            }
        }
        return result;
    }
}
