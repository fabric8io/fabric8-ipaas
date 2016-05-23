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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiman.manager.api.core.config.ApiManagerConfig;
import io.fabric8.utils.KubernetesServices;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import static io.fabric8.kubernetes.client.utils.Utils.getSystemPropertyOrEnvVar;

/**
 * Starts the API Manager as a jetty8 micro service.
 */
public class ApimanStarter {

    public final static String APIMAN_TESTMODE               = "APIMAN_TESTMODE";
    public final static String APIMAN_SSL                    = "APIMAN_SSL";
    public final static String APIMAN_ELASTICSEARCH_URL      = "APIMAN_ELASTICSEARCH_URL";
    public final static String APIMAN_GATEWAY_URL            = "APIMAN_GATEWAY_URL";

    public final static String APIMAN_GATEWAY_USERNAME       = "apiman-gateway.username";
    public final static String APIMAN_GATEWAY_PASSWORD       = "apiman-gateway.password";
    public final static String APIMAN_GATEWAY_USER_PATH      = "/secret/apiman/gateway.user";

    //KeyStore used by Jetty to serve SSL
    public final static String KEYSTORE_PATH                 = "/secret/apiman/keystore";
    public final static String KEYSTORE_PASSWORD_PATH        = "/secret/apiman/keystore.password";
    //client-keystore containing client-cert used by Apiman to authenticate to ElasticSearch
    public final static String CLIENT_KEYSTORE_PATH          = "/secret/apiman/client.keystore";
    public final static String CLIENT_KEYSTORE_PASSWORD_PATH = "/secret/apiman/client.keystore.password";
    //Truststore used by Apiman to trust ElasticSearch and the Apiman Gateway (self-signed cert)
    //Use: keytool -importcert -keystore truststore -file servercert.pem
    public final static String TRUSTSTORE_PATH               = "/secret/apiman/truststore";
    public final static String TRUSTSTORE_PASSWORD_PATH      = "/secret/apiman/truststore.password";

    final private static Log log = LogFactory.getLog(ApimanStarter.class);

    private static String gatewayUrl = null;
    /**
     * Main entry point for the API Manager micro service.
     * @param args the arguments
     * @throws Exception when any unhandled exception occurs
     */
    public static final void main(String [] args) throws Exception {

        Fabric8ManagerApiMicroService microService = new Fabric8ManagerApiMicroService();

        boolean isTestMode = getSystemPropertyOrEnvVar(APIMAN_TESTMODE, false);
        if (isTestMode) log.info("Apiman running in TestMode");

        boolean isSsl = getSystemPropertyOrEnvVar(APIMAN_SSL, false);
        log.info("Apiman running in SSL: " + isSsl);
        String protocol = "http";
        if (isSsl) protocol = "https";

        URL elasticEndpoint = null;
        // Require ElasticSearch and the Gateway Services to to be up before proceeding
        if (isTestMode) {
            URL url = new URL("https://localhost:9200");
            elasticEndpoint = waitForDependency(url,"","elasticsearch","status","200");
        } else {
            String esURL = getSystemPropertyOrEnvVar(APIMAN_ELASTICSEARCH_URL, protocol + "://elasticsearch-v1:9200");
            URL url = new URL(esURL);
            elasticEndpoint = waitForDependency(url,"","elasticsearch","status","200");
            log.info("Found " + elasticEndpoint);
            gatewayUrl = getSystemPropertyOrEnvVar(APIMAN_GATEWAY_URL, protocol + "://APIMAN-GATEWAY:7777");

            URL gatewayEndpoint = waitForDependency(new URL(gatewayUrl),"/api/system/status","apiman-gateway","up","true");
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

    public static String getGatewayUrl() {
        return gatewayUrl;
    }

    public static void setFabric8Props(URL elasticEndpoint) throws IOException {

        log.info("** Setting API Manager Configuration Properties **");

        setConfigProp("apiman.plugins.repositories",
                "http://repository.jboss.org/nexus/content/groups/public/");

        setConfigProp("apiman.es.protocol",            elasticEndpoint.getProtocol());
        setConfigProp("apiman.es.host",                elasticEndpoint.getHost());
        setConfigProp("apiman.es.port", String.valueOf(elasticEndpoint.getPort()));

        String esIndexPrefix = getSystemPropertyOrEnvVar("apiman.es.index.prefix",".apiman_");
        if (esIndexPrefix != null) {
            setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_INDEX_NAME, esIndexPrefix + "manager");
        }
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_CLIENT_FACTORY, Fabric8EsClientFactory.class.getName());

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_TYPE, "es");
        setConfigProp("apiman-manager.storage.es.protocol", "${apiman.es.protocol}");
        setConfigProp("apiman-manager.storage.es.host",     "${apiman.es.host}");
        setConfigProp("apiman-manager.storage.es.port",     "${apiman.es.port}");
        setConfigProp("apiman-manager.storage.es.username", "${apiman.es.username}");
        setConfigProp("apiman-manager.storage.es.protocol.password", "${apiman.es.password}");
        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_STORAGE_ES_INITIALIZE, "true");

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_METRICS_TYPE, "es");
        setConfigProp("apiman-manager.metrics.es.protocol", "${apiman.es.protocol}");
        setConfigProp("apiman-manager.metrics.es.host",     "${apiman.es.host}");
        setConfigProp("apiman-manager.metrics.es.port",     "${apiman.es.port}");
        setConfigProp("apiman-manager.metrics.es.username", "${apiman.es.username}");
        setConfigProp("apiman-manager.metrics.es.password", "${apiman.es.password}");

        setConfigProp(ApiManagerConfig.APIMAN_MANAGER_API_CATALOG_TYPE, KubernetesServiceCatalog.class.getName());

        File gatewayUserFile = new File(ApimanStarter.APIMAN_GATEWAY_USER_PATH);
        if (gatewayUserFile.exists()) {
            String[] user = IOUtils.toString(gatewayUserFile.toURI()).split(",");
            setConfigProp(ApimanStarter.APIMAN_GATEWAY_USERNAME, user[0]);
            setConfigProp(ApimanStarter.APIMAN_GATEWAY_PASSWORD, user[1]);
        }

        log.info("** ******************************************** **");
    }

    static final void setConfigProp(String propName, String defaultValue) {
        if (getSystemPropertyOrEnvVar(propName) == null) {
            System.setProperty(propName, defaultValue);
        }
        if (propName.toLowerCase().contains("password")) {
            log.info("\t" + propName + "=********");
        } else {
            log.info("\t" + propName + "=" + System.getProperty(propName));
        }
    }

    public static URL resolveServiceEndpoint(String scheme, String serviceName, String defaultPort) {
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

    private static URL waitForDependency(URL url, String path, String serviceName, String key, String value) throws InterruptedException {
        boolean isFoundRunningService= false;
        ObjectMapper mapper = new ObjectMapper();
        int counter = 0;
        URL endpoint = null;
        while (! isFoundRunningService) {
            endpoint = resolveServiceEndpoint(url.getProtocol(), url.getHost(), String.valueOf(url.getPort()));
            if (endpoint!=null) {
                String isLive = null;
                try {
                    URL statusURL = new URL(endpoint.toExternalForm() + path);
                    HttpURLConnection urlConnection =  (HttpURLConnection) statusURL.openConnection();
                    urlConnection.setConnectTimeout(500);
                    if (urlConnection instanceof HttpsURLConnection) {
                        try {
                            KeyStoreUtil.Info tPathInfo = new KeyStoreUtil().new Info(
                                    ApimanStarter.TRUSTSTORE_PATH,
                                    ApimanStarter.TRUSTSTORE_PASSWORD_PATH);
                            TrustManager[] tms = KeyStoreUtil.getTrustManagers(tPathInfo);
                            KeyStoreUtil.Info kPathInfo = new KeyStoreUtil().new Info(
                                    ApimanStarter.CLIENT_KEYSTORE_PATH,
                                    ApimanStarter.CLIENT_KEYSTORE_PASSWORD_PATH);
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
