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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import java.util.Properties;

@UriParams
public class JNatsdConfiguration {
    String NATS_PROPERTY_VERBOSE = "io.nats.client.verbose";
    String NATS_PROPERTY_PEDANTIC = "io.nats.client.pedantic";

    @UriParam
    @Metadata(required = "true")
    private String topic;
    @UriParam(defaultValue = "false")
    private boolean pedantic;
    @UriParam(defaultValue = "false")
    private boolean verbose;
    @UriParam(defaultValue = "4222")
    private int clientPort = 4222;
    @UriParam(label = "producer")
    private String replySubject;
    @UriParam(label = "consumer")
    private String queueGroup;
    private String user;
    private String pass;
    private String name;
    @UriParam(label = "consumer", defaultValue = "10")
    private int poolSize = 10;

    private static <T> void addPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            props.put(key, value);
        }
    }

    /**
     * The name of topic we want to use
     */
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }


    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }


    /**
     * the subject to which subscribers should send response
     */
    public String getReplySubject() {
        return replySubject;
    }

    public void setReplySubject(String replySubject) {
        this.replySubject = replySubject;
    }

    /**
     * The Queue group if we are using nats for a queue configuration
     */
    public String getQueueGroup() {
        return queueGroup;
    }

    public void setQueueGroup(String queueName) {
        this.queueGroup = queueName;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
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

    public Properties createProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, NATS_PROPERTY_VERBOSE, isVerbose());
        addPropertyIfNotNull(props, NATS_PROPERTY_PEDANTIC, isPedantic());
        return props;
    }

}
