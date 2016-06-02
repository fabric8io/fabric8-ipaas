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

package io.fabric8.msg.jnatsd;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {JNatsdConfiguration.class, JNatsd.class}, loader = AnnotationConfigContextLoader.class)
public class TestConnect {

    @Autowired
    private JNatsd jNatsd;

    @Before
    public void before() throws Exception {
        jNatsd.start();
    }

    @After
    public void after() throws Exception {
        jNatsd.stop();
    }

    @Test
    public void testConnect() throws Exception {
        Connection subConnection = new ConnectionFactory().createConnection();
        final int count = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        Subscription subscription = subConnection.subscribe("foo", new MessageHandler() {
            @Override
            public void onMessage(Message message) {
                countDownLatch.countDown();
                //System.out.println("GOT " + message);
            }
        });

        Connection pubConnection = new ConnectionFactory().createConnection();

        for (int i = 0; i < count; i++) {
            String test = "Test" + i;
            pubConnection.publish("foo", "bah", test.getBytes());
            //System.err.println("SENT " + test);
        }

        countDownLatch.await(2, TimeUnit.SECONDS);
        Assert.assertEquals(0, countDownLatch.getCount());
        pubConnection.close();
        subConnection.close();
    }
}
