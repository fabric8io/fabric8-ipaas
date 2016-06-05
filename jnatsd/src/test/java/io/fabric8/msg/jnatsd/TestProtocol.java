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

import io.nats.client.AsyncSubscription;
import io.nats.client.Channel;
import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import io.nats.client.SyncSubscription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {JNatsdConfiguration.class, JNatsd.class}, loader = AnnotationConfigContextLoader.class)
public class TestProtocol {

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
    public void testPubSubWithReply() {
        try (Connection c = new ConnectionFactory().createConnection()) {
            try (SyncSubscription s = c.subscribeSync("foo")) {
                final byte[] omsg = "Hello World".getBytes();
                c.publish("foo", "reply", omsg);
                try {
                    c.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                }
                Message m = s.nextMessage(10000);
                assertArrayEquals("Message received does not match: ", omsg, m.getData());
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testFlush() {
        final byte[] omsg = "Hello World".getBytes();

        try (Connection c = new ConnectionFactory().createConnection()) {
            c.subscribeSync("foo");
            c.publish("foo", "reply", omsg);
            try {
                c.flush();
            } catch (Exception e) {
                fail("Received error from flush: " + e.getMessage());
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testQueueSubscriber() {
        final byte[] omsg = "Hello World".getBytes();
        try (Connection c = new ConnectionFactory().createConnection()) {
            SyncSubscription s1 = c.subscribeSync("foo", "bar"), s2 = c.subscribeSync("foo", "bar");
            c.publish("foo", omsg);
            try {
                c.flush();
            } catch (Exception e) {
                /* IGNORE */
            }

            int r1 = s1.getQueuedMessageCount();
            int r2 = s2.getQueuedMessageCount();
            assertEquals("Received too many messages for multiple queue subscribers", 1, r1 + r2);

            // Drain the messages.
            try {
                s1.nextMessage(1000);
            } catch (TimeoutException e) {
            }
            assertEquals(0, s1.getQueuedMessageCount());
            try {
                s2.nextMessage(1000);
            } catch (TimeoutException e) {
            }
            assertEquals(0, s2.getQueuedMessageCount());

            int total = 1000;
            for (int i = 0; i < total; i++) {
                c.publish("foo", omsg);
            }
            try {
                c.flush();
            } catch (Exception e) {
            }

            int v = (int) (total * 0.15);
            r1 = s1.getQueuedMessageCount();
            r2 = s2.getQueuedMessageCount();
            assertEquals("Incorrect number of messages: ", total, r1 + r2);

            System.err.println("R1 = " + r1 + ", R2 = " + r2);
            double expected = total / 2;
            int d1 = (int) Math.abs((expected - r1));
            int d2 = (int) Math.abs((expected - r2));
            if (d1 > v || d2 > v) {
                fail(String.format("Too much variance in totals: %d, %d > %d", r1, r2, v));
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testReplyArg() {
        final String replyExpected = "bar";
        final String ts;

        final Channel<Boolean> ch = new Channel<Boolean>();
        try (Connection c = new ConnectionFactory().createConnection()) {
            try (AsyncSubscription s = c.subscribeAsync("foo", new MessageHandler() {
                @Override
                public void onMessage(Message msg) {
                    assertEquals(replyExpected, msg.getReplyTo());
                    ch.add(true);
                }
            })) {
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                }
                c.publish("foo", "bar", (byte[]) null);
                assertTrue("Message not received.", ch.get(5, TimeUnit.SECONDS));
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSyncReplyArg() {
        String replyExpected = "bar";
        try (Connection c = new ConnectionFactory().createConnection()) {
            try (SyncSubscription s = c.subscribeSync("foo")) {
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                }
                c.publish("foo", replyExpected, (byte[]) null);
                Message m = null;
                try {
                    m = s.nextMessage(1000);
                } catch (Exception e) {
                    fail("Received an err on nextMsg(): " + e.getMessage());
                }
                assertEquals(replyExpected, m.getReplyTo());
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testUnsubscribe() throws Exception {
        final Channel<Boolean> ch = new Channel<Boolean>();
        final AtomicInteger count = new AtomicInteger(0);
        final int max = 20;
        ConnectionFactory cf = new ConnectionFactory();
        cf.setReconnectAllowed(false);
        try (Connection c = cf.createConnection()) {
            try (final AsyncSubscription s = c.subscribeAsync("foo", new MessageHandler() {
                @Override
                public void onMessage(Message m) {
                    count.incrementAndGet();
                    if (count.get() == max) {
                        try {
                            m.getSubscription().unsubscribe();
                            assertFalse(m.getSubscription().isValid());
                        } catch (Exception e) {
                            fail("Unsubscribe failed with err: " + e.getMessage());
                        }
                        ch.add(true);
                    }
                }
            })) {
                for (int i = 0; i < max; i++) {
                    c.publish("foo", null, (byte[]) null);
                }
                sleep(100);
                c.flush();

                if (s.isValid()) {
                    assertTrue("Test complete signal not received", ch.get(5, TimeUnit.SECONDS));
                    assertFalse(s.isValid());
                }
                assertEquals(max, count.get());
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleUnsubscribe() {
        try (Connection c = new ConnectionFactory().createConnection()) {
            try (SyncSubscription s = c.subscribeSync("foo")) {
                s.unsubscribe();
                try {
                    s.unsubscribe();
                } catch (IllegalStateException e) {
                    throw e;
                }
            }
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testManyRequests() {
        int numMsgs = 500;
        try {
            ConnectionFactory cf = new ConnectionFactory(ConnectionFactory.DEFAULT_URL);
            try (final Connection conn = cf.createConnection()) {
                try (Subscription s = conn.subscribe("foo", new MessageHandler() {
                    public void onMessage(Message message) {
                        try {
                            conn.publish(message.getReplyTo(), "response".getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })) {
                    for (int i = 0; i < numMsgs; i++) {
                        try {
                            // System.out.println(conn.request("foo", "request".getBytes(), 5000));
                            conn.request("foo", "request".getBytes(), 5000);
                        } catch (TimeoutException e) {
                            System.err.println("got timeout " + i);
                            fail("timed out: " + i);
                        } catch (IOException e) {
                            fail(e.getMessage());
                        }
                    }
                }
            } catch (IOException | TimeoutException e1) {
                fail(e1.getMessage());
            }
        } finally {

        }
    }

    @Test
    public void testLargeSubjectAndReply() {
        try (Connection c = new ConnectionFactory().createConnection()) {
            int size = 1066;
            byte[] subjBytes = new byte[size];
            for (int i = 0; i < size; i++) {
                subjBytes[i] = 'A';
            }
            final String subject = new String(subjBytes);

            byte[] replyBytes = new byte[size];
            for (int i = 0; i < size; i++) {
                replyBytes[i] = 'A';
            }
            final String reply = new String(replyBytes);

            final Channel<Boolean> ch = new Channel<Boolean>();
            try (AsyncSubscription s = c.subscribeAsync(subject, new MessageHandler() {
                @Override
                public void onMessage(Message msg) {
                    assertEquals(subject.length(), msg.getSubject().length());
                    assertEquals(subject, msg.getSubject());
                    assertEquals(reply.length(), msg.getReplyTo().length());
                    assertEquals(reply, msg.getReplyTo());
                    ch.add(true);
                }
            })) {

                c.publish(subject, reply, (byte[]) null);
                try {
                    c.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

                assertTrue(ch.get(5, TimeUnit.SECONDS));
            }
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testLargeMessage() {
        try (final Connection c = new ConnectionFactory().createConnection()) {
            int msgSize = 51200;
            final byte[] omsg = new byte[msgSize];
            byte[] output = null;
            for (int i = 0; i < msgSize; i++) {
                {
                    omsg[i] = (byte) 'A';
                }
            }

            omsg[msgSize - 1] = (byte) 'Z';

            final Channel<Boolean> ch = new Channel<Boolean>();
            AsyncSubscription s = c.subscribeAsync("foo", new MessageHandler() {
                @Override
                public void onMessage(Message msg) {
                    assertTrue("Response isn't valid.", Arrays.equals(omsg, msg.getData()));
                    ch.add(true);
                }
            });

            c.publish("foo", omsg);
            try {
                c.flush(1000);
            } catch (Exception e1) {
                e1.printStackTrace();
                fail("Flush failed");
            }
            assertTrue("Didn't receive callback message", ch.get(2, TimeUnit.SECONDS));

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testSendAndRecv() throws Exception {
        try (Connection c = new ConnectionFactory().createConnection()) {
            assertFalse(c.isClosed());
            final AtomicInteger received = new AtomicInteger();
            int count = 1000;
            try (AsyncSubscription s = c.subscribeAsync("foo", new MessageHandler() {
                public void onMessage(Message msg) {
                    received.incrementAndGet();
                }
            })) {
                // s.start();
                assertFalse(c.isClosed());
                for (int i = 0; i < count; i++) {
                    c.publish("foo", null);
                }
                c.flush();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }

                assertTrue(String.format("Received (%s) != count (%s)", received, count),
                    received.get() == count);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadOptionTimeoutConnect() {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setConnectionTimeout(-1);
    }

    @Test
    public void testSimplePublish() {
        try (Connection c = new ConnectionFactory().createConnection()) {
            c.publish("foo", "Hello World".getBytes());
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSimplePublishNoData() {
        try (Connection c = new ConnectionFactory().createConnection()) {
            c.publish("foo", null);
        } catch (IOException | TimeoutException e) {
            fail(e.getMessage());
        }
    }
}
