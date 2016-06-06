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

import io.fabric8.msg.jnatsd.protocol.Command;
import io.fabric8.msg.jnatsd.protocol.Connect;
import io.fabric8.msg.jnatsd.protocol.Err;
import io.fabric8.msg.jnatsd.protocol.Ok;
import io.fabric8.msg.jnatsd.protocol.Ping;
import io.fabric8.msg.jnatsd.protocol.Pong;
import io.fabric8.msg.jnatsd.protocol.Pub;
import io.fabric8.msg.jnatsd.protocol.Sub;
import io.fabric8.msg.jnatsd.protocol.UnSub;
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class JNatsAbstractClient implements JNatsClient {
    protected static final int COMPACT_LIMIT = 32 * 1024 * 1024;
    protected static final Ok OK = new Ok();
    protected static final Ping PING = new Ping();
    protected static final Pong PONG = new Pong();
    private static final Logger LOG = LoggerFactory.getLogger(JNatsAbstractClient.class);
    protected final Connect connect = new Connect();

    /**
     * Ping/Pong control
     */
    protected final AtomicBoolean dataReadSinceLastPing = new AtomicBoolean();
    protected final AtomicInteger outStandingPings = new AtomicInteger();

    protected final Map<Buffer, Subscription> subscriptions = new ConcurrentHashMap<>();
    protected final JNatsd jNatsd;
    protected final AtomicBoolean closed = new AtomicBoolean();

    public JNatsAbstractClient(JNatsd jnatsd) {
        this.jNatsd = jnatsd;
        this.connect.setVerbose(jnatsd.getConfiguration().isVerbose());
    }

    public Connect getConnect(){
        return connect;
    }

    @Override
    public void publish(Command command) {
        if (command != null) {
            dataReadSinceLastPing.set(true);
            if (connect != null && connect.isVerbose()) {
                consume(OK);
            }
            switch (command.getType()) {
                case PING:
                    consume(PONG);
                    outStandingPings.set(0);
                    break;
                case PONG:
                    outStandingPings.set(0);
                    break;
                case CONNECT:
                    connect.reset((Connect) command);
                    if (jNatsd.authorize(this, connect) == false) {
                        close("Authorization Violation");
                    }
                    break;
                case PUB:
                    Pub pub = (Pub) command;
                    if (pub.getPayloadSize() > jNatsd.getConfiguration().getMaxPayLoad()) {
                        close("Maximum Payload Exceeded");
                    } else {
                        jNatsd.publish(pub);
                    }
                    break;
                case SUB:
                    Sub sub = (Sub) command;
                    Subscription subscription = new Subscription();
                    subscription.setNatsClient(this);
                    subscription.setAddress(sub.getSubject());
                    subscription.setSid(sub.getSid());
                    subscription.setQueueGroup(sub.getQueueGroup());
                    subscriptions.put(sub.getSid(), subscription);
                    jNatsd.addSubscription(this, subscription);
                    break;
                case UNSUB:
                    UnSub unSub = (UnSub) command;
                    subscription = subscriptions.remove(unSub.getSid());
                    if (subscription != null) {
                        jNatsd.removeSubscription(this, subscription);
                    }
                    break;
                default:
                    String message = "Unknown Protocol Operation: " + command;
                    close(message);
            }
        }
    }

    @Override
    public boolean close() {
        if (closed.compareAndSet(false, true)) {
            LOG.debug("Closing client " + this);
            try {
                for (Subscription subscription : subscriptions.values()) {
                    jNatsd.removeSubscription(this, subscription);
                }
                jNatsd.removeClient(this);
            } catch (Throwable e) {
                LOG.warn("Problem closing: ", e);
            }
        }
        return closed.get();
    }

    @Override
    public void pingTime() {
        if (outStandingPings.get() >= 3) {
            close("Stale Connection");
        } else if (dataReadSinceLastPing.get()) {
            consume(PING);
            outStandingPings.incrementAndGet();
        }
        dataReadSinceLastPing.set(false);
    }

    protected void close(String message) {
        try {
            LOG.warn("Closing connection: " + message);
            consume(new Err(message));
            Thread.sleep(100);
            close();
        } catch (Throwable e) {
            LOG.warn("Failed to close with Err " + message + " " + this, e);
        }
    }

}
