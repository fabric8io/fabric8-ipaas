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

import io.fabric8.msg.jnatsd.embedded.EmbeddedConnection;
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
public class TestLoad {

    @Autowired
    private JNatsd jNatsd;

    @Before
    public void before() throws Exception {
        jNatsd.getConfiguration().setClientPort(0);
        jNatsd.start();
    }

    @After
    public void after() throws Exception {
        jNatsd.stop();
    }

    @Test
    public void testLoad() throws Exception {
        EmbeddedConnection subConnection = new EmbeddedConnection(jNatsd);
        subConnection.start();
        final int count = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        subConnection.addSubscriber("foo", msg -> {
            countDownLatch.countDown();
        });

        EmbeddedConnection pubConnection = new EmbeddedConnection(jNatsd);
        pubConnection.start();

        long start = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            String test = "Test" + i;
            pubConnection.publish("foo", "bah", test.getBytes());
        }

        countDownLatch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals(0, countDownLatch.getCount());

        long finish = System.currentTimeMillis();

        long totalTime = finish - start;

        int messagesPerSecond = (int) ((count * 1000) / totalTime);

        System.err.println("Duration to pub/sub " + count + " messages = " + totalTime + " ms = " + messagesPerSecond + " msg/sec");
        pubConnection.close();
        subConnection.close();
    }
}
