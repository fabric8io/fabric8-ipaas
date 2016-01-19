/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.apiman;

import io.fabric8.utils.KubernetesServices;
import io.fabric8.utils.Systems;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Starts the API Manager as a jetty8 micro service.
 */
public class ApimanStarter {

	public final static String APIMAN_GATEWAY_USER     = "apiman-gateway.default.user";
	public final static String APIMAN_GATEWAY_PASSWORD = "apiman-gateway.default.password";
	
    /**
     * Main entry point for the API Manager micro service.
     * @param args the arguments
     * @throws Exception when any unhandled exception occurs
     */
    public static final void main(String [] args) throws Exception {
        
    	Fabric8ManagerApiMicroService microService = new Fabric8ManagerApiMicroService();
    	setFabric8Props();
        microService.start();
        microService.join();
    }
    
    public static void setFabric8Props() {
        URL elasticEndpoint = resolveServiceEndpoint("elasticsearch-v1", "9200");

        System.out.println("** Setting API Manager Configuration Properties **");

        setConfigProp("apiman.plugins.repositories",
                "http://repository.jboss.org/nexus/content/groups/public/");

        setConfigProp("apiman.es.protocol", elasticEndpoint.getProtocol());
        setConfigProp("apiman.es.host", elasticEndpoint.getHost());
        setConfigProp("apiman.es.port", String.valueOf(elasticEndpoint.getPort()));

        setConfigProp("apiman-manager.storage.type", "es");
        setConfigProp("apiman-manager.storage.es.protocol", "${apiman.es.protocol}");
        setConfigProp("apiman-manager.storage.es.host", "${apiman.es.host}");
        setConfigProp("apiman-manager.storage.es.port", "${apiman.es.port}");
        setConfigProp("apiman-manager.storage.es.username", "${apiman.es.username}");
        setConfigProp("apiman-manager.storage.es.password", "${apiman.es.password}");
        setConfigProp("apiman-manager.storage.es.initialize", "true");

        setConfigProp("apiman-manager.metrics.type", "es");
        setConfigProp("apiman-manager.metrics.es.protocol", "${apiman.es.protocol}");
        setConfigProp("apiman-manager.metrics.es.host", "${apiman.es.host}");
        setConfigProp("apiman-manager.metrics.es.port", "${apiman.es.port}");
        setConfigProp("apiman-manager.metrics.es.username", "${apiman.es.username}");
        setConfigProp("apiman-manager.metrics.es.password", "${apiman.es.password}");

        setConfigProp("apiman-manager.api-catalog.type", KubernetesServiceCatalog.class.getName());

        System.out.println("** ******************************************** **");
    }

    private static final void setConfigProp(String propName, String propValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, propValue);
        }
        System.out.println("\t" + propName + "=" + System.getProperty(propName));
    }
    
    public static URL resolveServiceEndpoint(String serviceName, String defaultPort) {
        URL endpoint = null;
        String host = "localhost";
        String port = defaultPort;
        try {
            //lookup in the current namespace
            InetAddress initAddress = InetAddress.getByName(serviceName);
            host = initAddress.getCanonicalHostName();
            System.out.println("Resolved host using DNS: " + host);
        } catch (UnknownHostException e) {
            System.out.println("Could not resolve DNS for " + serviceName + ", trying ENV settings next.");
            host = KubernetesServices.serviceToHostOrBlank(serviceName);
            if ("".equals(host)) {
                host = "localhost";
                System.out.println("Defaulting " + serviceName + " host to: " + host);
            } else {
                System.out.println("Resolved " + serviceName + " host using ENV: " + host);
            }
        }
        port = KubernetesServices.serviceToPortOrBlank(serviceName);
        if ("".equals(port)) {
            port = defaultPort;
            System.out.println("Defaulting " + serviceName + " port to: " + port);
        } else {
            System.out.println("Resolved " + serviceName + " port using ENV: " + port);
        }
        String scheme = "http";
        if (port.endsWith("443")) scheme = "https";
        try {
            endpoint = new URL(scheme, host, Integer.valueOf(port), "");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return endpoint;
    }
}
