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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.msg.gateway.Constants.GATEWAY_PROCESS_NAME;
import static io.fabric8.msg.gateway.Constants.NOTIFICATION_TOPIC_NAME;
import static io.fabric8.msg.gateway.Constants.WILD_CARD_NAME;

public class Proxy implements MessageListener {
    private static final transient Logger LOG = LoggerFactory.getLogger(Proxy.class);
    private final AtomicBoolean started = new AtomicBoolean();
    private final ConnectionFactory connectionFactory;
    private final BrokerControl brokerControl;
    private JMSContext jmsContext;
    private JMSProducer jmsProducer;

    public Proxy(ConnectionFactory connectionFactory, BrokerControl brokerControl){
        this.connectionFactory = connectionFactory;
        this.brokerControl=brokerControl;
    }


    public void addConsumer(Destination destination) throws Exception{
        brokerControl.get(destination).addConsumer(destination,this);
    }

    public void removeConsumer(Destination destination) throws Exception{
        brokerControl.get(destination).removeConsumer(destination);
    }

    public void start() throws  Exception{
        if (started.compareAndSet(false,true)){
            jmsContext = connectionFactory.createContext();
            jmsContext.start();

            MessageListener gatewayListener = new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    proccessMessageFromGateway(message);
                }
            };
            //listen to the MessageGateway for messages
            Destination topics = jmsContext.createTopic(WILD_CARD_NAME);
            JMSConsumer topicConsumer = jmsContext.createConsumer(topics);
            topicConsumer.setMessageListener(gatewayListener);

            Destination queues = jmsContext.createQueue(WILD_CARD_NAME);
            JMSConsumer queueConsumer = jmsContext.createConsumer(queues);
            queueConsumer.setMessageListener(gatewayListener);

            jmsProducer = jmsContext.createProducer();


        }
    }

    public void stop() throws  Exception{
        if (started.compareAndSet(true,false)){
            if (jmsContext != null){
                jmsContext.stop();
            }
        }
    }

    /**
     * Listen for Messages from the remote brokers - and send them to the Gateway
     * @param message
     */
    @Override
    public void onMessage(Message message) {
        if (started.get()){
            try {
                jmsProducer.send(message.getJMSDestination(), message);
            }catch(Throwable e){
                LOG.error("Failed to send message[" + message + "] to the gateway ",e);
            }
        }
    }


    private void proccessMessageFromGateway(Message message){
        try {
            Destination destination = message.getJMSDestination();
            if (isValidDestination(destination)){
                //prevent looping messages from gateway to brokers

                if (!message.propertyExists(GATEWAY_PROCESS_NAME)) {
                    /**
                     * ToDo - look at using Core Message to avoid this crapola
                     */
                    HashMap<String,Object> map = getMessageProperties(message);
                    map.put(GATEWAY_PROCESS_NAME, true);
                    message.clearProperties();
                    setMessageProperties(message,map);
                    brokerControl.get(destination).send(destination, message);
                }
            }
        }catch(Throwable e){
            LOG.error("Failed to process message[" + message + "] from the gateway ",e);
        }
    }

    private static HashMap<String, Object> getMessageProperties (Message msg) throws JMSException
    {
        HashMap <String, Object> properties = new HashMap <String, Object> ();
        Enumeration srcProperties = msg.getPropertyNames();
        while (srcProperties.hasMoreElements()) {
            String propertyName = (String) srcProperties.nextElement ();
            properties.put(propertyName, msg.getObjectProperty (propertyName));
        }
        return properties;
    }

    private void setMessageProperties (Message msg, HashMap <String, Object> properties) throws JMSException {
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey ();
            Object value = entry.getValue ();
            msg.setObjectProperty(propertyName, value);
        }
    }

    private boolean isValidDestination(Destination destination){
        String destinationName = destination.toString();

        if (destinationName.contains(NOTIFICATION_TOPIC_NAME) ||
                destinationName.contains("ActiveMQ.Advisory")){
            return false;
        }
        return true;
    }
}
