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

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apiman.gateway.platforms.war.micro.Users;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.utils.KubernetesServices;
import io.fabric8.utils.Systems;

/**
 * Starts the Apiman Gateway as a jetty8 micro service.
 */
public class ApimanGatewayStarter {
    
    public final static String APIMAN_GATEWAY_TESTMODE          = "APIMAN_GATEWAY_TESTMODE";
    public final static String APIMAN_GATEWAY_SSL               = "APIMAN_GATEWAY_SSL";
    public final static String APIMAN_GATEWAY_ELASTICSEARCH_URL = "APIMAN_GATEWAY_ELASTICSEARCH_URL";
    
    public final static String APIMAN_GATEWAY_USER_PATH         = "/secret/apiman-gateway/gateway.user";
    public final static String APIMAN_GATEWAY_PROPERTIES        = "/secret/apiman-gateway/apiman-gateway.properties";
    public final static String APIMAN_GATEWAY_ES_USERNAME       = "apiman.es.username";
    public final static String APIMAN_GATEWAY_ES_PASSWORD       = "apiman.es.password";

    //KeyStore used by Jetty to serve SSL
    public static String KEYSTORE_PATH                           = "/secret/apiman-gateway/keystore";
    public static String KEYSTORE_PASSWORD_PATH                  = "/secret/apiman-gateway/keystore.password";
    //client-keystore containing client-cert used by Apiman-Gateway to authenticate to ElasticSearch
    public static String CLIENT_KEYSTORE_PATH                    = "/secret/apiman-gateway/client.keystore";
    public static String CLIENT_KEYSTORE_PASSWORD_PATH           = "/secret/apiman-gateway/client.keystore.password";
    //Truststore used by Apiman-Gateway to trust ElasticSearch and the Apiman Gateway (self-signed cert)
    //Use: keytool -importcert -keystore truststore -file servercert.pem
    public static String TRUSTSTORE_PATH                         = "/secret/apiman-gateway/truststore";
    public static String TRUSTSTORE_PASSWORD_PATH                = "/secret/apiman-gateway/truststore.password";
    
    final private static Log log = LogFactory.getLog(ApimanGatewayStarter.class);
    /**
     * Main entry point for the Apiman Gateway micro service.
     * @param args the arguments
     * @throws Exception when any unhandled exception occurs
     */
    public static final void main(String [] args) throws Exception {
        
        String isTestModeString = Systems.getEnvVarOrSystemProperty(APIMAN_GATEWAY_TESTMODE,"false");
        boolean isTestMode = "true".equalsIgnoreCase(isTestModeString);
        if (isTestMode) log.info("Apiman Gateway Running in TestMode");
        
        String isSslString = Systems.getEnvVarOrSystemProperty(APIMAN_GATEWAY_SSL,"false");
        boolean isSsl = "true".equalsIgnoreCase(isSslString);
        log.info("Apiman Gateway running in SSL: " + isSsl);
        String protocol = "http";
        if (isSsl) protocol = "https";
        
        URL elasticEndpoint = null;
        
        File gatewayConfigFile = new File(APIMAN_GATEWAY_PROPERTIES);
        String esUsername = null;
        String esPassword = null;
        if (gatewayConfigFile.exists()) {
            PropertiesConfiguration config = new PropertiesConfiguration(gatewayConfigFile);
            esUsername = config.getString("es.username");
            esPassword = config.getString("es.password");
            if (Utils.isNotNullOrEmpty(esPassword)) 
                esPassword = new String(Base64.getDecoder().decode(esPassword),StandardCharsets.UTF_8).trim();
            setConfigProp(APIMAN_GATEWAY_ES_USERNAME, esUsername);
            setConfigProp(APIMAN_GATEWAY_ES_PASSWORD, esPassword);
        }
        log.info(esUsername + esPassword);
 
        // Require ElasticSearch and the Gateway Services to to be up before proceeding
        if (isTestMode) {
            URL url = new URL(protocol + "://localhost:9200");
            elasticEndpoint = waitForDependency(url,"elasticsearch-v1","status","200", esUsername, esPassword);
        } else {
            String defaultEsUrl = protocol + "://elasticsearch-v1:9200";
            String esURL = Systems.getEnvVarOrSystemProperty(APIMAN_GATEWAY_ELASTICSEARCH_URL, defaultEsUrl);
            URL url = new URL(esURL);
            elasticEndpoint = waitForDependency(url,"elasticsearch-v1","status","200", esUsername, esPassword);
            log.info("Found " + elasticEndpoint);
        }
        
        File usersFile = new File(APIMAN_GATEWAY_USER_PATH);
        if (usersFile.exists()) {
            setConfigProp(Users.USERS_FILE_PROP, APIMAN_GATEWAY_USER_PATH);
        }
        
        log.info("** ******************************************** **");
        
        Fabric8GatewayMicroService microService = new Fabric8GatewayMicroService(elasticEndpoint);
        
        if (isSsl) {
            microService.startSsl();
            microService.joinSsl();
        } else {
            microService.start();
            microService.join();
        }
    }
    
    public static URL resolveServiceEndpoint(String scheme, String serviceName, String defaultPort) {
        URL endpoint = null;
        String host = null;
        String port = defaultPort;
        try {
            //lookup in the current namespace
            log.info("Looking up service " + serviceName);
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
   
        if (scheme==null) {
            if (port.endsWith("443")) scheme = "https";
            else scheme = "http";
        }
        try {
            endpoint = new URL(scheme, host, Integer.valueOf(port), "");
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return endpoint;
    }
    
    private static URL waitForDependency(URL url, String serviceName, String key, String value, String username, String password) throws InterruptedException {
        boolean isFoundRunningService= false;
        ObjectMapper mapper = new ObjectMapper();
        int counter = 0;
        URL endpoint = null;
        while (! isFoundRunningService) {
            endpoint = resolveServiceEndpoint(url.getProtocol(), url.getHost(), String.valueOf(url.getPort()));
            if (endpoint!=null) {
                String isLive = null;
                try {
                    URL statusURL = new URL(endpoint.toExternalForm() + url.getPath());
                    HttpURLConnection urlConnection =  (HttpURLConnection) statusURL.openConnection();
                    urlConnection.setConnectTimeout(500);
                    if (urlConnection instanceof HttpsURLConnection) {
                        try {
                            KeyStoreUtil.Info tPathInfo = new KeyStoreUtil().new Info(
                                    TRUSTSTORE_PATH,
                                    TRUSTSTORE_PASSWORD_PATH);
                            TrustManager[] tms = KeyStoreUtil.getTrustManagers(tPathInfo);
                            KeyStoreUtil.Info kPathInfo = new KeyStoreUtil().new Info(
                                    CLIENT_KEYSTORE_PATH,
                                    CLIENT_KEYSTORE_PASSWORD_PATH);
                            KeyManager[] kms = KeyStoreUtil.getKeyManagers(kPathInfo);
                            final SSLContext sc = SSLContext.getInstance("TLS");
                            sc.init(kms, tms, new java.security.SecureRandom());
                            final SSLSocketFactory socketFactory = sc.getSocketFactory();
                            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
                            HttpsURLConnection httpsConnection = (HttpsURLConnection) urlConnection;
                            httpsConnection.setHostnameVerifier(new DefaultHostnameVerifier());
                            httpsConnection.setSSLSocketFactory(socketFactory);
                        } catch (Exception e) {
                            log.error(e.getMessage(),e);
                            throw e;
                        }
                    }
                    if (Utils.isNotNullOrEmpty(username)) {
                        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
                        log.info(username + ":******");
                        urlConnection.setRequestProperty("Authorization", "Basic "+ encoded);
                    }
                    isLive = IOUtils.toString(urlConnection.getInputStream());
                    Map<String,Object> esResponse = mapper.readValue(isLive, new TypeReference<Map<String, Object>>(){});
                    if (esResponse.containsKey(key) && value.equals(String.valueOf(esResponse.get(key)))) {
                        isFoundRunningService = true;
                    } else {
                        if (counter%10==0) log.info(endpoint.toExternalForm() + " not yet up (host=" + endpoint.getHost() + ")" + isLive);
                    }
                } catch (Exception e) {
                    if (counter%10==0) log.info(endpoint.toExternalForm() + " not yet up. (host=" + endpoint.getHost() + ")" + e.getMessage());
                }
            } else {
                if (counter%10==0) log.info("Could not find " + serviceName  + " in namespace, waiting..");
            }
            counter++;
            Thread.sleep(1000l);
        }
        return endpoint;
    }
    
    static final void setConfigProp(String propName, String defaultValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, defaultValue);
        }
        if (propName.toLowerCase().contains("password")) {
            log.info("\t" + propName + "=********");
        } else {
            log.info("\t" + propName + "=" + System.getProperty(propName));
        }
    }
    
}
