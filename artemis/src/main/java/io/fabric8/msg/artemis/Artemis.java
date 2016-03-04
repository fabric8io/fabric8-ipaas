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

package io.fabric8.msg.artemis;

import io.fabric8.utils.Systems;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;

@SpringBootApplication
public class Artemis {
    private EmbeddedJMS broker;

    public void start() throws Exception {

        broker = new EmbeddedJMS();
        String port = Systems.getEnvVarOrSystemProperty("AMQ_PORT","AMQ_PORT","61616");
        String dataDirectory = Systems.getEnvVarOrSystemProperty("AMQ_DATA_DIRECTORY","AMQ_DATA_DIRECTORY","data");

        HashMap<String, Object> configMap = new HashMap<>();
        configMap.put("port",port);
        TransportConfiguration transportConfiguration = new TransportConfiguration(NettyAcceptorFactory.class.getName(),configMap,"artemis");
        Configuration configuration = new ConfigurationImpl().setJournalDirectory("data")
                            .setPersistenceEnabled(false).setSecurityEnabled(false)
                            .addAcceptorConfiguration(transportConfiguration)
                            .setJournalDirectory(dataDirectory)
                            .setCreateJournalDir(true);


        JMSConfiguration jmsConfig = new JMSConfigurationImpl();

        broker = new EmbeddedJMS();
        broker.setConfiguration(configuration);
        broker.setJmsConfiguration(jmsConfig);
        broker.start();
    }



    public void stop() throws  Exception{
        if (broker != null){
            broker.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Artemis.class, args);
    }
}
