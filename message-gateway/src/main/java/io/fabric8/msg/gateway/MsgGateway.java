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

package io.fabric8.msg.gateway;

import io.fabric8.msg.gateway.brokers.BrokerControl;
import io.fabric8.msg.gateway.brokers.DestinationMapper;
import io.fabric8.msg.gateway.brokers.impl.KubernetesBrokerControl;
import io.fabric8.msg.gateway.brokers.impl.ZKDestinationMapper;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import javax.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class MsgGateway  {
    private static final Logger LOG = LoggerFactory.getLogger(MsgGateway.class);
    private EmbeddedJMS broker;
    private BrokerControl brokerControl;
    private Proxy proxy;
    private NotificationListener notificationListener;
    private DestinationMapper destinationMapper;


    @PostConstruct
    public void start() throws Exception {

        broker = new EmbeddedJMS();
        broker.start();

        LOG.info("EmbeddedJMS started");
        JMSServerManager jmsServerManager = broker.getJMSServerManager();
        List<String> connectors = new ArrayList<>();
        connectors.add("in-vm");
        jmsServerManager.createConnectionFactory("ConnectionFactory", false, JMSFactoryType.CF, connectors, "ConnectionFactory");
        ConnectionFactory cf = (ConnectionFactory) broker.lookup("ConnectionFactory");

        ZKDestinationMapper zkDestinationMapper = new ZKDestinationMapper();
        zkDestinationMapper.start();
        this.destinationMapper = zkDestinationMapper;

        brokerControl = new KubernetesBrokerControl(destinationMapper);
        zkDestinationMapper.setBrokerControl(brokerControl);
        brokerControl.start();

        proxy = new Proxy(cf,brokerControl);
        proxy.start();
        notificationListener = new NotificationListener(cf,proxy);
        notificationListener.start();
        LOG.info("MsgGateway is initialised and running ...");

    }


    public void stop() throws  Exception{
        if (notificationListener != null){
            notificationListener.stop();
        }
        if (proxy != null){
            proxy.stop();
        }
        if (brokerControl != null){
            brokerControl.stop();
        }
        if (broker != null){
            broker.stop();
        }
    }

    public BrokerControl getBrokerControl() {
        return  brokerControl;
    }

    public void setBrokerControl(BrokerControl brokerControl) {
        this.brokerControl = brokerControl;
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(MsgGateway.class, args);
    }
}
