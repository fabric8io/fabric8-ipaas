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
import io.fabric8.kubernetes.api.model.DoneableEndpoints;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.BrokerControl;
import io.fabric8.msg.gateway.brokers.DestinationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.Message;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class KubernetesBrokerControl extends BrokerControlSupport implements BrokerControl {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBrokerControl.class);
    private final AtomicBoolean started = new AtomicBoolean();
    private final DestinationMapper destinationMapper;
    private String brokerSelector = "component=message-broker,group=messaging";
    private String artemisName = "message-broker";
    private String portName = "61616";
    private String namespace = KubernetesHelper.defaultNamespace();
    private int ARTEMIS_PORT = 61616;
    private KubernetesClient kubernetesClient;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private EndpointWatcher endpointWatcher;
    private Watch endpointWatch;
    private final AtomicInteger clientIndex = new AtomicInteger();
    private Callable<ArtemisClient> createClientCallback = () -> getNextClient();

    public KubernetesBrokerControl(DestinationMapper destinationMapper) {
        this.destinationMapper = destinationMapper;
    }

    @Override
    public ArtemisClient getProducer(Destination destination, Message message) throws Exception {
        // TODO if the destination is sharded lets find the shard bucket based on the message...
        return destinationMapper.getProducer(destination, createClientCallback);
    }

    @Override
    public ArtemisClient getConsumer(Destination destination) throws Exception {
        // TODO if the destination is sharded lets randomly pick a shard?
        return destinationMapper.getConsumer(destination, createClientCallback);
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            kubernetesClient = new DefaultKubernetesClient();

            endpointWatcher = new EndpointWatcher(this, kubernetesClient.getNamespace());
            LOG.info("EndpointWatcher created in namespace " + kubernetesClient.getNamespace());

            ClientResource<Endpoints, DoneableEndpoints> endpointsClient;
            endpointsClient = kubernetesClient.endpoints().inNamespace(getNamespace()).withName(getArtemisName());
            LOG.info("watching endpoints in namespace " + getNamespace() + " for " + getArtemisName());
            endpointWatch = endpointsClient.watch(endpointWatcher);
            Endpoints endpoints = endpointsClient.get();
            if (endpoints != null) {
                endpointWatcher.onInitialEndpoints(endpoints);
            }

            // TODO watch for the map of Destination -> Broker

            LOG.info("KubernetesBrokerControl started");
        }
    }

    public void stop() throws Exception {
        if (endpointWatch != null) {
            endpointWatch.close();
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

    public int getARTEMIS_PORT() {
        return ARTEMIS_PORT;
    }

    public void setARTEMIS_PORT(int ARTEMIS_PORT) {
        this.ARTEMIS_PORT = ARTEMIS_PORT;
    }

    public String getArtemisName() {
        return artemisName;
    }

    public void setArtemisName(String artemisName) {
        this.artemisName = artemisName;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public void createClientForEndpoints(Endpoints endpoints) throws Exception {
        HashSet<String> set = new HashSet<>();
        if (endpoints != null) {
            for (EndpointSubset subset : endpoints.getSubsets()) {
                if (subset.getPorts().size() == 1) {
                    EndpointPort port = subset.getPorts().get(0);
                    for (EndpointAddress address : subset.getAddresses()) {
                        ArtemisClient client = getOrCreateArtemisClient(address.getIp(), port.getPort());
                        set.add(client.getHostAndPort());
                    }
                } else {
                    for (EndpointPort port : subset.getPorts()) {
                        if (Utils.isNullOrEmpty(portName) || portName.endsWith(port.getName())) {
                            for (EndpointAddress address : subset.getAddresses()) {
                                ArtemisClient client = getOrCreateArtemisClient(address.getIp(), port.getPort());
                                set.add(client.getHostAndPort());
                            }
                        }
                    }
                }
            }
        }

        System.err.println("LOOKUP SET SIZE = " + set.size());

        Map<String, ArtemisClient> artemisClients = getArtemisClients();
        Set<Map.Entry<String, ArtemisClient>> entries = artemisClients.entrySet();
        for (Map.Entry<String, ArtemisClient> entry : entries) {
            String key = entry.getKey();
            ArtemisClient client = entry.getValue();
            if (!set.contains(key)) {
                artemisClients.remove(key);
                LOG.info("Removed stale " + client);
                try {
                    client.stop();
                } catch (Exception e) {
                    LOG.warn("Failed to stop client " + client + ". " + e, e);
                }
            }
        }
    }

    public ArtemisClient getOrCreateArtemisClient(String host, Integer port) throws Exception {
        return getOrCreateArtemisClient(host + ":" + port);
    }

    private ArtemisClient getNextClient() {
        int clientCounter = clientIndex.incrementAndGet();
        int count = 0;
        while (true) {
            List<ArtemisClient> list = new ArrayList<>(getArtemisClients().values());
            if (list.isEmpty()) {
                // lets wait until there are clients we can use!
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (count > 10) {
                    count = 0;
                    LOG.warn("Still no broker pods available! Waiting for them to join....");
                }
            } else {
                int size = list.size();
                int idx = clientCounter % size;
                return list.get(idx);
            }
        }
    }
}
