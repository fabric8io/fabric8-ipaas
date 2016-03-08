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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.BrokerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class KubernetesBrokerControl implements BrokerControl{
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBrokerControl.class);
    private final AtomicBoolean started = new AtomicBoolean();
    private String brokerSelector = "component=artemis,group=artemis,project=artemis,provider=fabric8";
    private String namespace = KubernetesHelper.defaultNamespace();
    private int ARTEMIS_PORT = 61616;
    private KubernetesClient kubernetes;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ConcurrentLinkedDeque<ArtemisClient> artemisClients = new ConcurrentLinkedDeque<>();
    private Map<Destination,ArtemisClient> destinationArtemisClientMap = new ConcurrentHashMap<>();

    @Override
    public ArtemisClient get(Destination destination) {
        ArtemisClient result = destinationArtemisClientMap.get(destination);
        if (result == null){
            result = getNextClient();
            if (result != null){
                ArtemisClient  artemisClient = destinationArtemisClientMap.putIfAbsent(destination,result);
                if (artemisClient != null){
                    result = artemisClient;
                }
            }
        }
        return result;
    }

    public void start() throws Exception{
        if (started.compareAndSet(false,true)){
            kubernetes = new DefaultKubernetesClient();
            executor.scheduleAtFixedRate(()-> lookupBrokers(),0,5, TimeUnit.SECONDS);
            LOG.info("KubernetesBrokerControl started");
        }
    }

    public void stop() throws Exception {
        if(started.compareAndSet(true,false)){
            executor.shutdownNow();
        }
    }

    public String getBrokerSelector() {
        return brokerSelector;
    }

    public void setBrokerSelector(String brokerSelector) {
        this.brokerSelector = brokerSelector;
    }

    protected void lookupBrokers() {
        LOG.info("LOOKUP BROKERS CALLED");
        try {
            HashSet<ArtemisClient> set = new HashSet<>();
            Map<String, Pod> podMap = KubernetesHelper.getSelectedPodMap(kubernetes,namespace, getBrokerSelector());
            Collection<Pod> pods = podMap.values();
            LOG.info("Checking " + getBrokerSelector() + ": groupSize = " + pods.size());
            for (Pod pod : pods) {
                if (KubernetesHelper.isPodRunning(pod)) {
                    String host = KubernetesHelper.getHost(pod);
                    ArtemisClient artemisClient = new ArtemisClient(host, ARTEMIS_PORT);
                    LOG.info("Added new " + artemisClient);
                    set.add(artemisClient);
                }
            }

            for (ArtemisClient artemisClient:set){
                if (!artemisClients.contains(artemisClient)){
                    artemisClient.start();
                    artemisClients.add(artemisClient);
                }
            }

            for (ArtemisClient artemisClient:artemisClients){
                if (!set.contains(artemisClient)){
                    artemisClient.stop();
                    artemisClients.remove(artemisClient);
                    LOG.info("Removed stale " + artemisClient);
                }
            }

        } catch (Throwable e) {
            LOG.error("Failed to pollBrokers ", e);
        }
    }

    private ArtemisClient getNextClient(){
        ArtemisClient artemisClient = null;
        if (!artemisClients.isEmpty()){
            artemisClient = artemisClients.poll();
            artemisClients.add(artemisClient);
        }
        return artemisClient;
    }

}
