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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import java.util.HashSet;
import java.util.Map;
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
    private String portName = "61616";
    private String namespace = KubernetesHelper.defaultNamespace();
    private int ARTEMIS_PORT = 61616;
    private KubernetesClient kubernetesClient;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ConcurrentLinkedDeque<ArtemisClient> artemisClients = new ConcurrentLinkedDeque<>();
    private Map<Destination, ArtemisClient> destinationArtemisClientMap = new ConcurrentHashMap<>();
    private EndpointWatcher endpointWatcher;
    private Watch endpointWatch;

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

            endpointWatcher = new EndpointWatcher(this, kubernetesClient.getNamespace());

            ClientResource<Endpoints, DoneableEndpoints> endpointsClient;
            endpointsClient = kubernetesClient.endpoints().inNamespace(getNamespace()).withName(getArtemisName());
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
        HashSet<ArtemisClient> set = new HashSet<>();
        if (endpoints != null) {
            for (EndpointSubset subset : endpoints.getSubsets()) {
                if (subset.getPorts().size() == 1) {
                    EndpointPort port = subset.getPorts().get(0);
                    for (EndpointAddress address : subset.getAddresses()) {
                        ArtemisClient artemisClient = new ArtemisClient(address.getIp(), port.getPort());
                        set.add(artemisClient);
                    }
                } else {
                    for (EndpointPort port : subset.getPorts()) {
                        if (Utils.isNullOrEmpty(portName) || portName.endsWith(port.getName())) {
                            for (EndpointAddress address : subset.getAddresses()) {
                                ArtemisClient artemisClient = new ArtemisClient(address.getIp(), port.getPort());
                                set.add(artemisClient);
                            }
                        }
                    }
                }
            }
        }

        System.err.println("LOOKUP SET SIZE = " + set.size());

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
