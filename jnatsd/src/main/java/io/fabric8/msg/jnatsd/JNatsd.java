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

import io.fabric8.msg.jnatsd.protocol.CommandFactory;
import io.fabric8.msg.jnatsd.protocol.Connect;
import io.fabric8.msg.jnatsd.protocol.Info;
import io.fabric8.msg.jnatsd.protocol.Msg;
import io.fabric8.msg.jnatsd.protocol.Pub;
import io.fabric8.msg.jnatsd.routing.RoutingMap;
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@Component
public class JNatsd {
    private static final Logger LOG = LoggerFactory.getLogger(JNatsd.class);
    private final List<NetServer> servers = new CopyOnWriteArrayList<>();
    private final List<JNatsClient> clients = new CopyOnWriteArrayList<>();
    private final RoutingMap routingMap = new RoutingMap();
    private final Info serverInfo = new Info();
    private final AtomicBoolean started = new AtomicBoolean();
    private final PingPong pingPong = new PingPong();
    @Autowired
    private JNatsdConfiguration configuration;
    private Vertx vertx;
    private int actualPort;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(JNatsd.class, args);
    }

    public JNatsdConfiguration getConfiguration() {
        if (configuration == null) {
            //not set nor autowired - so we are probably embedded
            configuration = new JNatsdConfiguration();
            configuration.setVerbose(false);
        }
        return configuration;
    }

    @PostConstruct
    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                serverInfo.setHost("0.0.0.0");
                serverInfo.setPort(getConfiguration().getClientPort());
                serverInfo.setVersion("1.0");
                serverInfo.setMaxPayload(getConfiguration().getMaxPayLoad());

                int numberOfServers = getConfiguration().getNumberOfNetServers();
                if (numberOfServers <= 0) {
                    numberOfServers = Runtime.getRuntime().availableProcessors();
                }

                final CountDownLatch countDownLatch = new CountDownLatch(numberOfServers);

                VertxOptions vertxOptions = new VertxOptions();

                vertx = Vertx.vertx(vertxOptions);

                LOG.info("Creating " + numberOfServers + " vert.x servers for JNatsd");
                for (int i = 0; i < numberOfServers; i++) {

                    NetServer server = vertx.createNetServer();
                    server.connectHandler(socket -> {
                        JNatsSocketClient natsClient = new JNatsSocketClient(this, serverInfo, socket);
                        addClient(natsClient);
                    });

                    server.listen(getConfiguration().getClientPort(), event -> {
                        if (event.succeeded()) {
                            actualPort = event.result().actualPort();
                            countDownLatch.countDown();
                        }
                    });

                    servers.add(server);
                }

                countDownLatch.await();

                pingPong.start();

                LOG.info("JNatsd initialized (" + numberOfServers + " servers:port=" + actualPort + ") and running ...");
            } catch (Throwable e) {
                LOG.error("Failed to initialize JNatsd", e);
            }
        }
    }

    public void stop() throws Exception {
        if (started.compareAndSet(true, false)) {
            pingPong.stop();
            final CountDownLatch countDownLatch = new CountDownLatch(servers.size());

            for (JNatsClient client : clients) {
                client.close();
            }

            for (NetServer server : servers) {
                server.close(handler -> {
                    countDownLatch.countDown();
                });
            }
            countDownLatch.await();
            LOG.info("JNatsd shutdown");
        }
    }

    public void addClient(JNatsClient client) {
        clients.add(client);
    }

    public void removeClient(JNatsClient client) {
        clients.remove(client);
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }

    public Info getServerInfo() {
        return serverInfo;
    }

    protected boolean authorize(JNatsClient natsClient, Connect connect) {
        return true;
    }

    protected void addSubscription(JNatsClient client, Subscription subscription) {
        routingMap.addSubcription(client, subscription);
    }

    protected void removeSubscription(JNatsClient client, Subscription subscription) {
        routingMap.removeSubscription(client, subscription);
    }

    public RoutingMap getRoutingMap() {
        return routingMap;
    }

    protected void publish(Pub pub) {
        try {
            Collection<Subscription> matches = pub.getMatches();
            if (matches != null && !matches.isEmpty()) {
                for (Subscription subscription : matches) {
                    Msg msg = CommandFactory.createMsg(subscription.getSid(), pub);
                    subscription.getNatsClient().consume(msg);
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No subscriber for " + pub);
                }
            }
        } catch (Throwable e) {
            LOG.error("Failed to publish " + pub, e);
        }
    }

    private class PingPong {
        private long timerId = -1;

        private void start() {
            int pingInterval = getConfiguration().getPingInterval();
            if (pingInterval > 0) {
                timerId = vertx.setPeriodic(getConfiguration().getPingInterval(), time -> {
                    for (JNatsClient client : clients) {
                        client.pingTime();
                    }
                });
            }
        }

        private void stop() {
            if (timerId >= 0) {
                vertx.cancelTimer(timerId);
            }
        }
    }
}
