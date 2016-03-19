/*
 *
 *  * Copyright 2005-2016 Red Hat, Inc.
 *  * Red Hat licenses this file to you under the Apache License, version
 *  * 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  * implied.  See the License for the specific language governing
 *  * permissions and limitations under the License.
 *
 */

package io.fabric8.msg.gateway.brokers.impl;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.BrokerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class KubernetesBrokerControl implements BrokerControl {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBrokerControl.class);
    private final AtomicBoolean started = new AtomicBoolean();
    private String brokerSelector = "component=artemis,group=artemis,project=artemis,provider=fabric8";
    private String artemisName = "artemis";
    private String artemisPort = "61616";
    private String namespace = KubernetesHelper.defaultNamespace();
    private KubernetesClient kubernetesClient;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ConcurrentLinkedDeque<ArtemisClient> artemisClients = new ConcurrentLinkedDeque<>();
    private Map<Destination, ArtemisClient> destinationArtemisClientMap = new ConcurrentHashMap<>();

    @Override
    public ArtemisClient get(Destination destination) {
        ArtemisClient result = destinationArtemisClientMap.get(destination);
        if (result == null) {
            result = getNextClient();
            if (result != null) {
                ArtemisClient artemisClient = destinationArtemisClientMap.putIfAbsent(destination, result);
                if (artemisClient != null) {
                    result = artemisClient;
                }
            }
        }
        return result;
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            kubernetesClient = new DefaultKubernetesClient();
            executor.scheduleAtFixedRate(() -> lookupBrokers(), 0, 5, TimeUnit.SECONDS);
            LOG.info("KubernetesBrokerControl started");
        }
    }

    public void stop() throws Exception {
        if (started.compareAndSet(true, false)) {
            executor.shutdownNow();
        }
    }

    public String getBrokerSelector() {
        return brokerSelector;
    }

    public void setBrokerSelector(String brokerSelector) {
        this.brokerSelector = brokerSelector;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getArtemisName() {
        return artemisName;
    }

    public void setArtemisName(String artemisName) {
        this.artemisName = artemisName;
    }


    public String getArtemisPort() {
        return artemisPort;
    }

    public void setArtemisPort(String artemisPort) {
        this.artemisPort = artemisPort;
    }


    protected void lookupBrokers() {
        try {
            HashSet<ArtemisClient> set = new HashSet<>();
            Set<String> brokers = KubernetesHelper.lookupServiceInDns(getArtemisName());
            brokers.forEach(broker -> {
                    System.err.println("Broker = " + broker);
                ArtemisClient artemisClient = new ArtemisClient(broker, getArtemisPort());
                set.add(artemisClient);
                });


            for (ArtemisClient artemisClient : set) {
                if (!artemisClients.contains(artemisClient)) {
                    artemisClient.start();
                    artemisClients.add(artemisClient);
                }
            }

            for (ArtemisClient artemisClient : artemisClients) {
                if (!set.contains(artemisClient)) {
                    artemisClient.stop();
                    artemisClients.remove(artemisClient);
                    LOG.info("Removed stale " + artemisClient);
                }
            }
        } catch (Throwable e) {
            LOG.error("FAILED lookupBrokers", e);
        }

    }

    private ArtemisClient getNextClient() {
        ArtemisClient artemisClient = null;
        if (!artemisClients.isEmpty()) {
            artemisClient = artemisClients.poll();
            artemisClients.add(artemisClient);
        }
        return artemisClient;
    }

}
