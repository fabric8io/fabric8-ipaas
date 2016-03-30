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
import io.fabric8.msg.gateway.brokers.BrokerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 */
public abstract class BrokerControlSupport implements BrokerControl {
    private static final transient Logger LOG = LoggerFactory.getLogger(BrokerControlSupport.class);

    private Map<String, ArtemisClient> artemisClients = new ConcurrentHashMap<>();

    public ArtemisClient getOrCreateArtemisClient(String hostAndPort) throws Exception {
        ArtemisClient artemisClient = artemisClients.get(hostAndPort);
        if (artemisClient == null) {
            artemisClient = new ArtemisClient(hostAndPort);
            artemisClients.put(hostAndPort, artemisClient);
            try {
                System.out.println("About to try connect to Artemis: " + artemisClient.getHostAndPort());
                artemisClient.start();
            } catch (Exception e) {
                LOG.error("Failed to connect to client " + artemisClient.getHostAndPort() + ". " + e, e);
                throw e;
            }
        }
        return artemisClient;
    }

    protected Map<String, ArtemisClient> getArtemisClients() {
        return artemisClients;
    }
}
