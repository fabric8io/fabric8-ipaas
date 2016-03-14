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

import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class NotificationListener implements MessageListener {
    private AtomicBoolean started = new AtomicBoolean();
    private final ConnectionFactory connectionFactory;
    private final Proxy proxy;
    private Connection connection;
    private Map<String,AtomicInteger> consumerMap = new ConcurrentHashMap<>();


    NotificationListener(ConnectionFactory connectionFactory,Proxy proxy){
        this.connectionFactory=connectionFactory;
        this.proxy=proxy;
    }

    public void start() throws Exception{
       if (started.compareAndSet(false,true)){
           Topic notificationsTopic = ActiveMQDestination.createTopic(Constants.NOTIFICATION_TOPIC_NAME);

           connection = connectionFactory.createConnection();
           connection.start();

           Session session = connection.createSession();

           MessageConsumer consumer = session.createConsumer(notificationsTopic);
           consumer.setMessageListener(this);

       }
    }

    public void stop() throws Exception{
        if (started.compareAndSet(true,false)){
            if (connection != null){
                connection.close();
            }
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            Map<String, Object> map = new HashMap<>();
            Enumeration propertyNames = message.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String propertyName = (String) propertyNames.nextElement();
                map.put(propertyName,message.getObjectProperty(propertyName));
            }

            Object type = map.get(ManagementHelper.HDR_NOTIFICATION_TYPE.toString());
            if (type != null && (type.toString().equalsIgnoreCase(CoreNotificationType.CONSUMER_CLOSED.name()) ||
                    type.toString().equalsIgnoreCase(CoreNotificationType.CONSUMER_CREATED.name()))) {
                Object routingName = map.get(ManagementHelper.HDR_ROUTING_NAME.toString());
                Object consumerCount = map.get(ManagementHelper.HDR_CONSUMER_COUNT.toString());
                if (routingName != null && routingName.toString().startsWith("jms")) {

                    Destination destination = ActiveMQDestination.createDestination(routingName.toString(),ActiveMQDestination.QUEUE_TYPE);
                    AtomicInteger count = consumerMap.get(routingName);
                    if (consumerCount != null) {
                        int countNumber = Integer.parseInt(consumerCount.toString());
                        if (count != null) {
                            if (countNumber > count.get()) {
                                while (countNumber > count.get()){
                                    proxy.addConsumer(destination);
                                    count.incrementAndGet();
                                }
                            }else if (countNumber < count.get()){
                                while (count.get() >  countNumber){
                                    proxy.removeConsumer(destination);
                                    count.decrementAndGet();
                                }
                            }
                        }
                    }else{
                        AtomicInteger result = consumerMap.putIfAbsent(routingName.toString(),new AtomicInteger(1));
                        if (result != null){
                            result.incrementAndGet();
                        }
                        proxy.addConsumer(destination);
                    }
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
