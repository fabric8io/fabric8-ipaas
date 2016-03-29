/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.msg.gateway.brokers.impl;

import io.fabric8.msg.gateway.ArtemisClient;
import io.fabric8.msg.gateway.brokers.BrokerControl;
import io.fabric8.msg.gateway.brokers.DestinationMapper;
import io.fabric8.utils.Systems;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.RetryForever;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import java.io.Closeable;
import java.util.concurrent.Callable;

import static io.fabric8.utils.URLUtils.pathJoin;

/**
 */
public class ZKDestinationMapper implements DestinationMapper, Closeable {
    private static final transient Logger LOG = LoggerFactory.getLogger(ZKDestinationMapper.class);

    private BrokerControl brokerControl;
    private RetryPolicy retryPolicy = new RetryForever(1000);
    private String zookeeperConnectionString;
    private String cachePath = "/io/fabric8/message-gateway/";
    private String queueCachePath = cachePath + "queue";
    private String topicCachePath = cachePath + "topic";
    private CuratorFramework client;
    private TreeCache queueCache;
    private TreeCache topicCache;

    public ZKDestinationMapper() {
    }

    public void start() throws Exception {
        zookeeperConnectionString = Systems.getEnvVarOrSystemProperty("ZOOKEEPER", "zookeeper") + ":2181";
        LOG.info("Connecting to ZooKeeper on " + zookeeperConnectionString);

        client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
        client.start();

        queueCache = new TreeCache(client, queueCachePath);
        topicCache = new TreeCache(client, topicCachePath);
        queueCache.start();
        topicCache.start();
    }

    public void close() {
        if (queueCache != null) {
            try {
                queueCache.close();
            } catch (Exception e) {
                LOG.warn("Failed to close queueCache: " + e, e);
            }
        }
        if (topicCache != null) {
            try {
                topicCache.close();
            } catch (Exception e) {
                LOG.warn("Failed to close topicCache: " + e, e);
            }
        }
        if (client != null) {
            client.close();
        }
    }

    @Override
    public ArtemisClient get(Destination destination, Callable<ArtemisClient> factoryCallback) throws Exception {
        checkStarted();

        TreeCache cache;
        String prefix;
        if (destination instanceof Queue) {
            cache = queueCache;
            prefix = queueCachePath;
        } else {
            cache = topicCache;
            prefix = topicCachePath;
        }
        String relativePath = destinationToZkPath(destination);
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        String path = pathJoin(prefix, relativePath);

        ArtemisClient answer = null;
        for (int i = 0; i < 10; i++) {
            ChildData childData = cache.getCurrentData(path);
            if (childData != null) {
                byte[] data = childData.getData();
                if (data != null) {
                    String brokerName = new String(data);
                    if (brokerName != null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Looking up path " + path + " found broker: " + brokerName);
                        }
                        answer = resultToArtemisClient(brokerName);
                    }
                }
            }
            if (answer == null) {
                answer = factoryCallback.call();
                String hostAndPort = answer.getHostAndPort();
                byte[] data = hostAndPort.getBytes();
                LOG.info("About to try write to ZK path " + path + " creating parents if needed for broker: " + hostAndPort);
                try {
                    client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path, data);
                    answer = resultToArtemisClient(new String(data));
                } catch (KeeperException.NodeExistsException e) {
                    // lets look it up again in the next loop
                    LOG.info("Node already exists for " + path + " so retrying attempt " + (i + 1));

                    // lets sleep a bit to wait for the cache to catch up
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        // ignore
                    }
                }
            }
            if (answer != null) {
                break;
            }
        }
        return answer;
    }

    protected ArtemisClient resultToArtemisClient(String result) throws Exception {
        if (brokerControl != null && result != null && result.contains(":")) {
            return brokerControl.getOrCreateArtemisClient(result);
        }
        return null;
    }

    public static String destinationToZkPath(Destination destination) throws JMSException {
        String name;
        if (destination instanceof Queue) {
            Queue queue = (Queue) destination;
            name = queue.getQueueName();
        } else if (destination instanceof Topic) {
            Topic topic = (Topic) destination;
            name = topic.getTopicName();
        } else {
            name = destination.toString();
        }
        return destinationToZkPath(name);
    }

    public static String destinationToZkPath(String destination) {
        return destination.replace('.', '/');
    }

    protected void checkStarted() {
        if (queueCache == null || topicCache == null) {
            throw new IllegalArgumentException("No tree caches. Did you call start() on the " + getClass().getName());
        }
    }

    public BrokerControl getBrokerControl() {
        return brokerControl;
    }

    public void setBrokerControl(BrokerControl brokerControl) {
        this.brokerControl = brokerControl;
    }
}
