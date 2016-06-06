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

package io.fabric8.msg.jnatsd.embedded;

import io.fabric8.msg.jnatsd.JNatsd;
import io.fabric8.msg.jnatsd.protocol.Command;
import io.fabric8.msg.jnatsd.protocol.Msg;
import io.fabric8.msg.jnatsd.protocol.Pong;
import io.fabric8.msg.jnatsd.protocol.Pub;
import io.fabric8.msg.jnatsd.protocol.Sub;
import io.fabric8.msg.jnatsd.protocol.UnSub;
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class EmbeddedConnection {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedConnection.class);
    private static final Pong PONG = new Pong();
    private final Map<String, Handler<Msg>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger sidGenerator = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();
    private final JNatsd jNatsd;
    private JNatsEmbeddedClient embeddedClient;
    private boolean pedantic;
    private boolean verbose;
    private String user;
    private String pass;
    private String name;

    public EmbeddedConnection(JNatsd jNatsd){
        this.jNatsd = jNatsd;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            jNatsd.start();
            embeddedClient = new JNatsEmbeddedClient(jNatsd, command -> {
                processCommand(command);
            });
            embeddedClient.getConnect().setVerbose(isVerbose());
            embeddedClient.getConnect().setPedantic(isPedantic());
            embeddedClient.getConnect().setUser(getUser());
            embeddedClient.getConnect().setPass(getPass());
            embeddedClient.getConnect().setName(getName());
            embeddedClient.start();
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public void close() {
        if (started.compareAndSet(true, false)) {
            if (embeddedClient != null) {
                embeddedClient.close();
                subscribers.clear();
                jNatsd.removeClient(embeddedClient);
                if (jNatsd.isEmpty()) {
                    try {
                        jNatsd.stop();
                    } catch (Exception e) {
                        LOG.error("Failed to stop Jnatsd cleanly");
                    }
                }
            }
        }
    }

    /**
     * Whether or not running in pedantic mode (this affects performace)
     */
    public boolean isPedantic() {
        return pedantic;
    }

    public void setPedantic(boolean pedantic) {
        this.pedantic = pedantic;
    }

    /**
     * Whether or not running in verbose mode
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * Subscribe to messages
     *
     * @param address
     * @param handler
     * @return unique subscriber id
     */
    public String addSubscriber(String address, Handler<Msg> handler) {
        return addSubscriber(address, null, handler);
    }

    /**
     * Subscribe to messages
     *
     * @param address
     * @param group
     * @param handler
     * @return unique subscriber id
     */
    public String addSubscriber(String address, String group, Handler<Msg> handler) {
        String sid = "" + sidGenerator.incrementAndGet();
        subscribers.put(sid, handler);
        Sub sub = new Sub();
        sub.setSubject(address);
        sub.setQueueGroup(group);
        sub.setSid(sid);
        embeddedClient.publish(sub);
        return sid;
    }

    public void removeSubscriber(String sid) {
        Handler<Msg> handler = subscribers.remove(sid);
        if (handler != null) {
            UnSub unSub = new UnSub();
            unSub.setSid(sid);
            embeddedClient.publish(unSub);
        }
    }

    public void publish(String subject, String payload) {
        Pub pub = new Pub();
        pub.setSubject(subject);
        pub.setPayload(payload);
        publish(pub);
    }

    public void publish(String subject, byte[] payload) {
        Pub pub = new Pub();
        pub.setSubject(subject);
        pub.setPayload(payload);
        publish(pub);
    }

    public void publish(String subject, String replyTo, String payload) {
        Pub pub = new Pub();
        pub.setSubject(subject);
        pub.setReplyTo(replyTo);
        pub.setPayload(payload);
        publish(pub);
    }

    public void publish(String subject, String replyTo, byte[] payload) {
        Pub pub = new Pub();
        pub.setSubject(subject);
        pub.setReplyTo(replyTo);
        pub.setPayload(payload);
        publish(pub);
    }

    public void publish(String subject, String replyTo, Buffer payload) {
        Pub pub = new Pub();
        pub.setSubject(subject);
        pub.setReplyTo(replyTo);
        pub.setPayload(payload);
        publish(pub);
    }

    private void publish(Pub pub) {
        Collection<Subscription> matches = jNatsd.getRoutingMap().getMatches(pub.getSubject());
        pub.setMatches(matches);
        embeddedClient.publish(pub);
    }

    private void processCommand(Command command) {
        switch (command.getType()) {
            case INFO:
                LOG.info("Connected to NATs server " + command);
                break;
            case MSG:
                Msg msg = (Msg) command;
                String sid = msg.getSid().toString();
                Handler<Msg> handler = subscribers.get(sid);
                if (handler != null) {
                    handler.handle(msg);
                }
                break;
            case PING:
                embeddedClient.consume(PONG);
                break;
            case PONG:
                break;
            case OK:
                break;
            case ERR:
                LOG.warn(command.toString());
                break;
            default:
                LOG.warn("Unknown command " + command);
        }
    }
}
