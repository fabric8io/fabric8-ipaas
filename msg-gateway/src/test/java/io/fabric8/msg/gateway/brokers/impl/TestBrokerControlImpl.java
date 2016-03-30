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

import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.BrokerControl;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.springframework.stereotype.Component;

import javax.jms.Destination;
import javax.jms.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TestBrokerControlImpl extends BrokerControlSupport implements BrokerControl {

    final Broker broker1 = new Broker();
    final Broker broker2 = new Broker();
    Broker last = null;

    Map<Destination,ArtemisClient> destinationMap = new ConcurrentHashMap<>();

    @Override
    public ArtemisClient getProducer(Destination destination, Message message) throws Exception {
        ArtemisClient result = destinationMap.get(destination);
        if (result == null){
            if (last == null || last != broker1){
                last = broker1;
            }else{
                last=broker2;
            }
            result = new ArtemisClient("localhost",last.port);
            
            if (destinationMap.putIfAbsent(destination,result) == null){
                result.start();
            }
        }
        return result;
    }

    @Override
    public ArtemisClient getConsumer(Destination destination) throws Exception {
        return getProducer(destination, null);
    }

    public void start() throws Exception{
        broker1.port = 61618;
        broker1.server = getEmbeddedJMS(broker1.port);
        broker1.server.start();
        broker2.port=61619;
        broker2.server = getEmbeddedJMS(broker2.port);
        broker2.server.start();
    }

    public void stop() throws Exception {
        if(broker1.server != null){
            broker1.server.stop();
        }
        if(broker2.server != null){
            broker2.server.stop();
        }
    }

    private EmbeddedJMS getEmbeddedJMS(int port){
        EmbeddedJMS result = new EmbeddedJMS();
        result.setConfiguration(getConfiguration(port));
        JMSConfiguration jmsConfig = new JMSConfigurationImpl();
        result.setJmsConfiguration(jmsConfig);
        return result;
    }

    private Configuration getConfiguration(int port){
        Configuration configuration = new ConfigurationImpl();
        configuration.setPersistenceEnabled(false);
        configuration.setJournalDirectory("target/data/journal");
        configuration.setSecurityEnabled(false);
        Map<String, Object> params = new HashMap<>();
        params.put("port",port);
        TransportConfiguration transportConfiguration =  new TransportConfiguration(NettyAcceptorFactory.class.getName(),params);
        configuration.getAcceptorConfigurations().add(transportConfiguration);
        return configuration;
    }

    private class Broker{
        int port;
        EmbeddedJMS server;
    }


}
