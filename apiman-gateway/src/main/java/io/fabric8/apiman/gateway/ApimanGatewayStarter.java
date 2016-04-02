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
package io.fabric8.apiman.gateway;

import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.utils.KubernetesServices;

/**
 * Starts the Apiman Gateway as a jetty8 micro service.
 */
public class ApimanGatewayStarter {
    
    final private static Log log = LogFactory.getLog(ApimanGatewayStarter.class);
    /**
     * Main entry point for the Apiman Gateway micro service.
     * @param args the arguments
     * @throws Exception when any unhandled exception occurs
     */
    public static final void main(String [] args) throws Exception {
        
        URL elasticEndpoint = waitForDependency("elasticsearch-v1", "9200");
        log.info("Found elasticEndpoint " + elasticEndpoint.toExternalForm());
        Fabric8GatewayMicroService microService = new Fabric8GatewayMicroService(elasticEndpoint);
        microService.start();
        microService.join();
    }
    
    private static URL resolveServiceEndpoint(String serviceName, String defaultPort) {
        URL endpoint = null;
        String host = "";
        String port = defaultPort;
        try {
            //lookup in the current namespace
            InetAddress initAddress = InetAddress.getByName(serviceName);
            host = initAddress.getCanonicalHostName();
            log.info("Resolved host using DNS: " + host);
        } catch (UnknownHostException e) {
            log.debug("Could not resolve DNS for " + serviceName + ", trying ENV settings next.");
            host = KubernetesServices.serviceToHostOrBlank(serviceName);
            if ("".equals(host)) {
                return null;
            } else {
                log.info("Resolved " + serviceName + " host using ENV: " + host);
            }
        }
        port = KubernetesServices.serviceToPortOrBlank(serviceName);
        if ("".equals(port)) {
            port = defaultPort;
            log.info("Defaulting " + serviceName + " port to: " + port);
        } else {
            log.info("Resolved " + serviceName + " port using ENV: " + port);
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
    
    private static URL waitForDependency(String serviceName, String port) throws InterruptedException {
        boolean isFoundRunningService= false;
        ObjectMapper mapper = new ObjectMapper();
        int counter = 0;
        URL endpoint = null;
        while (! isFoundRunningService) {
            endpoint = resolveServiceEndpoint(serviceName, port);
            if (endpoint!=null) {
                String isLive = null;
                try {
                    URLConnection urlConnection =  endpoint.openConnection();
                    urlConnection.setConnectTimeout(500);
                    isLive = IOUtils.toString(urlConnection.getInputStream());
                    Map<String,Object> esResponse = mapper.readValue(isLive, new TypeReference<Map<String, Object>>(){});
                    if (esResponse.containsKey("status") && "200".equals(String.valueOf(esResponse.get("status")))) {
                        isFoundRunningService = true;
                    } else {
                        if (counter%10==0) log.info(endpoint.toExternalForm() + " not yet up. " + isLive);
                    }
                } catch (Exception e) {
                    if (counter%10==0) log.info(endpoint.toExternalForm() + " not yet up. " + e.getMessage());
                }
            } else {
                if (counter%10==0) log.info("Could not find " + serviceName  + " in namespace, waiting..");
            }
            counter++;
            Thread.sleep(1000l);
        }
        return endpoint;
    }
    
}
