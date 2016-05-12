package io.fabric8.apiman.gateway;

import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;

import io.apiman.gateway.engine.es.SimpleJestClientFactory;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig.Builder;

public class Fabric8GatewayEsClientFactory extends SimpleJestClientFactory {


    final private static Log log = LogFactory.getLog(Fabric8GatewayEsClientFactory.class);

    /**
     * @param httpConfig
     */
    protected void updateHttpConfig(Builder httpConfig, Map<String, String> config) {
        String username = config.get("client.username"); //$NON-NLS-1$
        String password = config.get("client.password"); //$NON-NLS-1$
        String timeout  = config.get("client.timeout"); //$NON-NLS-1$
        if (username != null) {
            httpConfig.defaultCredentials(username, password);
        }
        if (timeout == null) {
            timeout = "10000"; //$NON-NLS-1$
        }
        
        int t = new Integer(timeout);
        httpConfig.connTimeout(t);
        httpConfig.readTimeout(t);
        httpConfig.maxTotalConnection(75);
        httpConfig.defaultMaxTotalConnectionPerRoute(75);
        httpConfig.multiThreaded(true);
        
        if ("https".equals(config.get("client.protocol"))) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                KeyStoreUtil.Info kPathInfo = new KeyStoreUtil().new Info(
                        ApimanGatewayStarter.CLIENT_KEYSTORE_PATH,
                        ApimanGatewayStarter.CLIENT_KEYSTORE_PASSWORD_PATH);
                KeyStoreUtil.Info tPathInfo = new KeyStoreUtil().new Info(
                        ApimanGatewayStarter.TRUSTSTORE_PATH,
                        ApimanGatewayStarter.TRUSTSTORE_PASSWORD_PATH);
                sslContext.init(KeyStoreUtil.getKeyManagers(kPathInfo), KeyStoreUtil.getTrustManagers(tPathInfo), null);
                HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                SchemeIOSessionStrategy httpsIOSessionStrategy = new SSLIOSessionStrategy(sslContext, hostnameVerifier);
                
                httpConfig.defaultSchemeForDiscoveredNodes("https");
                httpConfig.sslSocketFactory(sslSocketFactory); // for sync calls
                httpConfig.httpsIOSessionStrategy(httpsIOSessionStrategy); // for async calls

            } catch (Exception e) {
                log.error(e.getMessage(),e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * @param factory
     */
    protected void updateJestClientFactory(JestClientFactory factory) {
    }

}
