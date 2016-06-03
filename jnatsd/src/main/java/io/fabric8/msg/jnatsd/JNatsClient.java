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

import io.fabric8.msg.jnatsd.protocol.*;
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class JNatsClient {
    private static final Logger LOG = LoggerFactory.getLogger(JNatsClient.class);
    private static final int COMPACT_LIMIT = 32 * 1024 * 1024;
    private static final Ok OK = new Ok();
    private static final Ping PING = new Ping();
    private static final Pong PONG = new Pong();
    private final Map<Buffer, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final JNatsd jNatsd;
    private final NetSocket socket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Connect connect = new Connect();
    private final AtomicInteger outStandingPings = new AtomicInteger();
    int readEnd;
    int readStart;
    private Buffer current;

    public JNatsClient(JNatsd jnatsd, Info serverInfo, NetSocket netSocket) {
        this.jNatsd = jnatsd;
        this.socket = netSocket;
        this.socket.setWriteQueueMaxSize(jnatsd.getConfiguration().getMaxPendingSize());
        this.connect.setVerbose(jnatsd.getConfiguration().isVerbose());
        socket.exceptionHandler(e -> {
            LOG.error("Socket error", e);
            close();
        });

        socket.closeHandler(v -> {
            close();
        });

        socket.handler(buffer -> {
            try {
                process(buffer, true);
            } catch (Throwable e) {
                String message = "Parser Error: " + buffer;
                close(message);
            }
        });
        writeCommand(serverInfo);
    }

    @Override
    public String toString() {
        String result = "NatsClient{";
        if (connect != null) {
            result += connect.getProperties();
        }
        result += "}";
        return result;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.debug("Closing client " + this);
            try {
                socket.close();
                for (Subscription subscription : subscriptions.values()) {
                    jNatsd.removeSubscription(this, subscription);
                }
                jNatsd.removeClient(this);
            } catch (Throwable e) {
                LOG.warn("Problem closing: ", e);
            }
        }
    }

    private void close(String message) {
        try {
            LOG.warn(message);
            writeCommand(new Err(message));
            Thread.sleep(100);
            close();
        } catch (Throwable e) {
            LOG.warn("Failed to close with Err " + message + " " + this, e);
        }
    }

    public void writeCommand(Command command) {
        if (command != null && closed.get() == false) {
            try {
                socket.write(command.getBuffer());
            } catch (Throwable e) {
                LOG.warn("Failed to writeCommand " + command, e);
                close();
            }
        }
    }

    protected void pingTime() {
        if (outStandingPings.get() >= 3) {
            close("Stale Connection");
        } else {
            writeCommand(PING);
            outStandingPings.incrementAndGet();
        }
    }

    private void process(Buffer buffer, boolean append) throws ProtocolException {
        if (closed.get() == false) {
            if (current == null) {
                current = buffer;
                readStart = 0;
            } else if (append) {
                current.appendBuffer(buffer);
            }
            readEnd = current.length();

            Command command;

            do {
                command = CommandFactory.processBuffer(jNatsd.getRoutingMap(), current, readStart, readEnd);
                if (command != null) {
                    readStart += command.bytesRead() + 1;
                    processCommand(command);
                    if (readStart >= current.length()) {
                        current = null;
                        readStart = readEnd = 0;
                        break;
                    }
                }
            } while (command != null);
        }
    }

    private void processCommand(Command command) {
        if (command != null) {
            if (connect != null && connect.isVerbose()) {
                writeCommand(OK);
            }
            switch (command.getType()) {
                case PING:
                    writeCommand(PONG);
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
}
