/*
 *
 *  * Copyright 2005-2015 Red Hat, Inc.
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

package io.fabric8.amq;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.deltaspike.core.api.config.ConfigProperty;

import javax.inject.Inject;
import java.util.HashMap;

public class MSMBroker {

    @ConfigProperty(name = "AMQ_HOST", defaultValue = "0.0.0.0")
    private String host;
    @Inject
    @ConfigProperty(name = "AMQ_PORT", defaultValue = "61616")
    private int port;


    private ActiveMQServer server;
    private Configuration configuration;



    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }


    public void start() throws Exception{

        HashMap<String, Object> configMap = new HashMap<>();
        configMap.put("host", host);
        configMap.put("port",port);
        TransportConfiguration transportConfiguration = new TransportConfiguration(NettyAcceptorFactory.class.getName(),configMap,"fabric8-msm");
        configuration = new ConfigurationImpl().setJournalDirectory("data")
         .setPersistenceEnabled(false).setSecurityEnabled(false)
                            .addAcceptorConfiguration(transportConfiguration);

        server = ActiveMQServers.newActiveMQServer(configuration);
        try {
            server.start();
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }


    public void stop() throws  Exception{
        if (server != null){
            server.stop(true);
        }
    }
}
