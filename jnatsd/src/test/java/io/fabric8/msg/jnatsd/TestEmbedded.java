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
import io.fabric8.msg.jnatsd.protocol.Msg;
import io.vertx.core.Handler;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestEmbedded {

    @Test
    public void testEmbedded() throws Exception {
        JNatsd jNatsd = new JNatsd();
        jNatsd.getConfiguration().setClientPort(0);
        EmbeddedConnection subConnection = new EmbeddedConnection(jNatsd);
        subConnection.start();
        final int count = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        String subscription = subConnection.addSubscriber("foo", new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                countDownLatch.countDown();
            }
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
        jNatsd.stop();
    }
}
