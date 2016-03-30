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

package io.fabric8.artemis;

import io.fabric8.msg.artemis.Artemis;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class TestArtemis {
    private Artemis artemis;

    @Before
    public void doStart() throws Exception {
        artemis = new Artemis();
        artemis.start();
    }

    @After
    public void doStop() throws Exception {
        if (artemis != null) {
            artemis.stop();
        }
    }

    @Test
    public void simpleTest() throws Exception {
        int numberOfMessages = 10;
        String destinationName = "test.foo";
        String brokerURL = "tcp://localhost:61616";
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);

        Connection consumerConnection = factory.createConnection();
        consumerConnection.start();
        Session consumerSession = consumerConnection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        Destination consumerDestination = consumerSession.createQueue("#");
        MessageConsumer messageConsumer = consumerSession.createConsumer(consumerDestination);


        Connection producerConnection = factory.createConnection();
        producerConnection.start();
        Session producerSession = producerConnection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        Destination destination = producerSession.createQueue(destinationName);
        MessageProducer producer = producerSession.createProducer(destination);

        for (int i = 0; i < numberOfMessages;i++){
            Message message = producerSession.createTextMessage("test message: " + i);
            producer.send(message);
        }

        Message message;

        for (int i = 0; i < numberOfMessages; i++){
            message = messageConsumer.receive(5000);
            Assert.assertNotNull(message);

        }
        messageConsumer.close();

    }
}
