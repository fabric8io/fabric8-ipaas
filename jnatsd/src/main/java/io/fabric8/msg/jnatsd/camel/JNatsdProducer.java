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
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JNatsdProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JNatsdProducer.class);

    private EmbeddedConnection connection;

    public JNatsdProducer(JNatsdEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public JNatsdEndpoint getEndpoint() {
        return (JNatsdEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        JNatsdConfiguration config = getEndpoint().getJNatsConfiguration();
        String body = exchange.getIn().getMandatoryBody(String.class);

        LOG.debug("Publishing to topic: {}", config.getTopic());

        if (ObjectHelper.isNotEmpty(config.getReplySubject())) {
            String replySubject = config.getReplySubject();
            connection.publish(config.getTopic(), replySubject, body.getBytes());
        } else {
            connection.publish(config.getTopic(), body.getBytes());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Producer");

        LOG.debug("Getting Nats Connection");
        connection = new EmbeddedConnection();
        connection.start();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.debug("Stopping Nats Producer");

        LOG.debug("Closing Nats Connection");
        if (connection != null && !connection.isStarted()) {
            connection.close();
        }
    }

}
