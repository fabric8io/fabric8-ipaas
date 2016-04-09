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

import io.apiman.manager.api.core.config.ApiManagerConfig;
import io.fabric8.utils.KubernetesServices;
import io.fabric8.utils.Systems;

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

/**
 * Starts the API Manager as a jetty8 micro service.
 */
public class ApimanStarter {

    public final static String APIMAN_GATEWAY_USER     = "apiman-gateway.default.user";
    public final static String APIMAN_GATEWAY_PASSWORD = "apiman-gateway.default.password";

    final private static Log log = LogFactory.getLog(ApimanStarter.class);
    /**
     * Main entry point for the API Manager micro service.
     * @param args the arguments
     * @throws Exception when any unhandled exception occurs
     */
    public static final void main(String [] args) throws Exception {

        Fabric8ManagerApiMicroService microService = new Fabric8ManagerApiMicroService();
        
        String isTestModeString = Systems.getEnvVarOrSystemProperty("APIMAN_TESTMODE","false");
        boolean isTestMode = "true".equalsIgnoreCase(isTestModeString);
        if (isTestMode) log.info("APIMAN Running in TestMode");
        
        String isSslString = Systems.getEnvVarOrSystemProperty("APIMAN_SSL","false");
        boolean isSsl = "true".equalsIgnoreCase(isSslString);
        log.info("APIMAN running in SSL: " + isSsl);
        
        URL elasticEndpoint = null;
        // Require ElasticSearch and the Gateway Services to to be up before proceeding
        if (isTestMode) {
            elasticEndpoint = waitForDependency("localhost", "9200","/","status","200");
        } else {
            elasticEndpoint = waitForDependency("elasticsearch-v1", "9200","/","status","200");
            log.info("Found " + elasticEndpoint);
            URL gatewayEndpoint = waitForDependency("APIMAN-GATEWAY", "7777","/api/system/status","up","true");
            log.info("Found " + gatewayEndpoint);
        }

        setFabric8Props(elasticEndpoint);
        if (isSsl) {
            microService.startSsl();
            microService.joinSsl();
        } else {
            microService.start();
            microService.join();
        }
    }

    public static void setFabric8Props(URL elasticEndpoint) {

        log.info("** Setting API Manager Configuration Properties **");

        setConfigProp("apiman.plugins.repositories",
                "http://repository.jboss.org/nexus/content/groups/public/");

        setConfigProp("apiman.es.protocol",            elasticEndpoint.getProtocol());
        setConfigProp("apiman.es.host",                elasticEndpoint.getHost());
        setConfigProp("apiman.es.port", String.valueOf(elasticEndpoint.getPort()));

        String esIndexPrefix = Systems.getEnvVarOrSystemProperty("apiman.es.index.prefix",".apiman_");
        if (esIndexPrefix != null) {
            setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_INDEX_NAME, esIndexPrefix + "manager");
        }

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_TYPE, "es");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_PROTOCOL, "${apiman.es.protocol}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_HOST,     "${apiman.es.host}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_PORT,     "${apiman.es.port}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_USERNAME, "${apiman.es.username}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_PASSWORD, "${apiman.es.password}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_INITIALIZE, "true");

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_TYPE, "es");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_ES_PROTOCOL, "${apiman.es.protocol}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_ES_HOST,     "${apiman.es.host}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_ES_PORT,     "${apiman.es.port}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_ES_USERNAME, "${apiman.es.username}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_ES_PASSWORD, "${apiman.es.password}");

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_API_CATALOG_TYPE, KubernetesServiceCatalog.class.getName());

        log.info("** ******************************************** **");
    }

    static final void setConfigProp(String propName, String defaultValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, defaultValue);
        }
        log.info("\t" + propName + "=" + System.getProperty(propName));
    }

    public static URL resolveServiceEndpoint(String serviceName, String defaultPort) {
        URL endpoint = null;
        String host = null;
        String port = defaultPort;
        try {
            //lookup in the current namespace
            InetAddress initAddress = InetAddress.getByName(serviceName);
            host = initAddress.getCanonicalHostName();
            log.debug("Resolved host using DNS: " + host);
        } catch (UnknownHostException e) {
            log.debug("Could not resolve DNS for " + serviceName + ", trying ENV settings next.");
            host = KubernetesServices.serviceToHostOrBlank(serviceName);
            if ("".equals(host)) {
                return null;
            } else {
                log.debug("Resolved " + serviceName + " host using ENV: " + host);
            }
        }
        port = KubernetesServices.serviceToPortOrBlank(serviceName);
        if ("".equals(port)) {
            port = defaultPort;
            log.debug("Defaulting " + serviceName + " port to: " + port);
        } else {
            log.debug("Resolved " + serviceName + " port using ENV: " + port);
        }
        String scheme = "http";
        if (port.endsWith("443")) scheme = "https";
        try {
            endpoint = new URL(scheme, host, Integer.valueOf(port), "");
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return endpoint;
    }

    private static URL waitForDependency(String serviceName, String port, String path, String key, String value) throws InterruptedException {
        boolean isFoundRunningService= false;
        ObjectMapper mapper = new ObjectMapper();
        int counter = 0;
        URL endpoint = null;
        while (! isFoundRunningService) {
            endpoint = resolveServiceEndpoint(serviceName, port);
            if (endpoint!=null) {
                String isLive = null;
                try {
                    URL statusURL = new URL(endpoint.toExternalForm() + path);
                    URLConnection urlConnection =  statusURL.openConnection();
                    urlConnection.setConnectTimeout(500);
                    isLive = IOUtils.toString(urlConnection.getInputStream());
                    Map<String,Object> esResponse = mapper.readValue(isLive, new TypeReference<Map<String, Object>>(){});
                    if (esResponse.containsKey(key) && value.equals(String.valueOf(esResponse.get(key)))) {
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
