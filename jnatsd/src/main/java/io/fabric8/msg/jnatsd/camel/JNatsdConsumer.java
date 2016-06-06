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
package io.fabric8.msg.jnatsd.camel;

import io.fabric8.msg.jnatsd.embedded.EmbeddedConnection;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class JNatsdConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(JNatsdConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private EmbeddedConnection connection;
    private String sid;
    private String topic;
    private String queueGroup;
    private boolean subscribed;

    public JNatsdConsumer(JNatsdEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public JNatsdEndpoint getEndpoint() {
        return (JNatsdEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Consumer");
        executor = getEndpoint().createExecutor();

        LOG.debug("Getting Nats Connection");
        connection = new EmbeddedConnection(getEndpoint().getjNatsd());
        connection.start();

        topic = getEndpoint().getJNatsConfiguration().getTopic();
        queueGroup = getEndpoint().getJNatsConfiguration().getQueueGroup();

        sid = connection.addSubscriber(topic, queueGroup, msg -> {
            Exchange exchange = getEndpoint().createExchange();
            Message message = exchange.getIn();
            message.setBody(msg.getPayloadAsString());
            message.setHeader("topic", topic);
            message.setHeader("natsTimeStamp", System.currentTimeMillis());
            message.setHeader("sid", sid);
            if (queueGroup != null && !queueGroup.isEmpty()) {
                message.setHeader("queueGroup", queueGroup);
            }
            try {
                processor.process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error during processing", exchange, e);
            }
        });

        setSubscribed(true);

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.debug("Stopping JNats Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;

        LOG.debug("Closing JNats Connection");
        if (!connection.isStarted()) {
            connection.close();
        }
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

}
