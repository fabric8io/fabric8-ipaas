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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import java.util.concurrent.ExecutorService;

@UriEndpoint(scheme = "jnatsd", title = "JNatsd", syntax = "jnatsd:topic", label = "messaging", consumerClass = JNatsdConsumer.class)
public class JNatsdEndpoint extends DefaultEndpoint {

    @UriParam
    private JNatsdConfiguration configuration;

    public JNatsdEndpoint(String uri, JNatsdComponent component, JNatsdConfiguration config) {
        super(uri, component);
        this.configuration = config;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JNatsdProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new JNatsdConsumer(this, processor);
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "NatsTopic[" + configuration.getTopic() + "]", configuration.getPoolSize());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public JNatsdConfiguration getJNatsConfiguration() {
        return configuration;
    }
}
