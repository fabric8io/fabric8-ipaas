package io.fabric8.apiman;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.New;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Specializes;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;

import io.apiman.common.logging.IApimanLogger;
import io.apiman.common.util.crypt.IDataEncrypter;
import io.apiman.manager.api.core.IApiKeyGenerator;
import io.apiman.manager.api.core.IMetricsAccessor;
import io.apiman.manager.api.core.INewUserBootstrapper;
import io.apiman.manager.api.core.IPluginRegistry;
import io.apiman.manager.api.core.IStorage;
import io.apiman.manager.api.core.IStorageQuery;
import io.apiman.manager.api.core.UuidApiKeyGenerator;
import io.apiman.manager.api.core.crypt.DefaultDataEncrypter;
import io.apiman.manager.api.core.logging.ApimanLogger;
import io.apiman.manager.api.core.noop.NoOpMetricsAccessor;
import io.apiman.manager.api.es.ESMetricsAccessor;
import io.apiman.manager.api.es.EsStorage;
import io.apiman.manager.api.jpa.JpaStorage;
import io.apiman.manager.api.micro.ManagerApiMicroServiceCdiFactory;
import io.apiman.manager.api.micro.ManagerApiMicroServiceConfig;
import io.apiman.manager.api.security.ISecurityContext;
import io.apiman.manager.api.security.impl.DefaultSecurityContext;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;

@Specializes
public class Fabric8ManagerApiMicroServiceCdiFactory
        extends ManagerApiMicroServiceCdiFactory {
    
    private static JestClient sStorageESClient;
    private static JestClient sMetricsESClient;
    
    final private static Log log = LogFactory.getLog(Fabric8ManagerApiMicroServiceCdiFactory.class);
    
    @Produces @ApimanLogger
    public static IApimanLogger provideLogger(ManagerApiMicroServiceConfig config, InjectionPoint injectionPoint) {
        return ManagerApiMicroServiceCdiFactory.provideLogger(config, injectionPoint);
    }
    
    @Produces @ApplicationScoped
    public static INewUserBootstrapper provideNewUserBootstrapper(ManagerApiMicroServiceConfig config, IPluginRegistry pluginRegistry) {
        return ManagerApiMicroServiceCdiFactory.provideNewUserBootstrapper(config, pluginRegistry);
    }
    
    @Produces
    @ApplicationScoped
    public static IStorage provideStorage(ManagerApiMicroServiceConfig config, @New JpaStorage jpaStorage,
            @New EsStorage esStorage, IPluginRegistry pluginRegistry) {
        return ManagerApiMicroServiceCdiFactory.provideStorage(config, jpaStorage, esStorage, pluginRegistry);
    }

    @Produces @ApplicationScoped
    public static ISecurityContext provideSecurityContext(@New DefaultSecurityContext defaultSC) {
        return defaultSC;
    }

    @Produces @ApplicationScoped
    public static IStorageQuery provideStorageQuery(ManagerApiMicroServiceConfig config, @New JpaStorage jpaStorage,
            @New EsStorage esStorage, IPluginRegistry pluginRegistry) {
        return ManagerApiMicroServiceCdiFactory.provideStorageQuery(config, jpaStorage, esStorage, pluginRegistry);
    }

    @Produces @ApplicationScoped @Specializes
    public static IMetricsAccessor provideMetricsAccessor(ManagerApiMicroServiceConfig config,
            @New NoOpMetricsAccessor noopMetrics, @New ESMetricsAccessor esMetrics, IPluginRegistry pluginRegistry) {
        return ManagerApiMicroServiceCdiFactory.provideMetricsAccessor(config, noopMetrics, esMetrics, pluginRegistry);
    }

    @Produces @ApplicationScoped
    public static IApiKeyGenerator provideApiKeyGenerator(ManagerApiMicroServiceConfig config,
            IPluginRegistry pluginRegistry, @New UuidApiKeyGenerator uuidApiKeyGen) {
        return ManagerApiMicroServiceCdiFactory.provideApiKeyGenerator(config, pluginRegistry, uuidApiKeyGen);
    }

    @Produces @ApplicationScoped
    public static IDataEncrypter provideDataEncrypter(ManagerApiMicroServiceConfig config,
            IPluginRegistry pluginRegistry, @New DefaultDataEncrypter defaultEncrypter) {
       return ManagerApiMicroServiceCdiFactory.provideDataEncrypter(config, pluginRegistry, defaultEncrypter);
    }

    /**
     * 
     * @param config
     * @return
     */
    @Produces @ApplicationScoped @Named("storage")
    public static JestClient provideStorageESClient(ManagerApiMicroServiceConfig config) {
        if ("https".equalsIgnoreCase(config.getStorageESProtocol())) {
            if ("es".equals(config.getStorageType()) && sStorageESClient == null) { //$NON-NLS-1$
                sStorageESClient = createSslStorageJestClient(config);
            }
            return sStorageESClient;
        } else {
            return ManagerApiMicroServiceCdiFactory.provideStorageESClient(config);
        }        
    }

    @Produces @ApplicationScoped @Named("metrics")
    public static JestClient provideMetricsESClient(ManagerApiMicroServiceConfig config) {
        if ("https".equalsIgnoreCase(config.getStorageESProtocol())) {
            if ("es".equals(config.getMetricsType()) && sMetricsESClient == null) { //$NON-NLS-1$
                sMetricsESClient = createSslMetricsJestClient(config);
            }
            return sMetricsESClient;
        } else {
            return ManagerApiMicroServiceCdiFactory.provideMetricsESClient(config);
        }
    }
    
    /**
     * @param config
     * @return create a new test ES client
     */
    private static JestClient createSslStorageJestClient(ManagerApiMicroServiceConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getStorageESProtocol());
        builder.append("://"); //$NON-NLS-1$
        builder.append(config.getStorageESHost());
        builder.append(":"); //$NON-NLS-1$
        builder.append(config.getStorageESPort());
        String connectionUrl = builder.toString();
        
        JestClientFactory factory = null;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(getKeyManagers(), getTrustManagers(), null);
            HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            SchemeIOSessionStrategy httpsIOSessionStrategy = new SSLIOSessionStrategy(sslContext, hostnameVerifier);
            factory = new JestClientFactory();
            
            HttpClientConfig.Builder httpClientConfig = new HttpClientConfig.Builder(connectionUrl)
                    .multiThreaded(true)
                    .defaultSchemeForDiscoveredNodes("https")
                    .sslSocketFactory(sslSocketFactory) // for sync calls
                    .httpsIOSessionStrategy(httpsIOSessionStrategy) // for async calls
                    .multiThreaded(true)
                    .connTimeout(config.getStorageESTimeout())
                    .readTimeout(config.getStorageESTimeout());
            String esUsername = config.getStorageESUsername();
            String esPassword = config.getStorageESPassword();
            if (esUsername != null) {
                httpClientConfig.defaultCredentials(esUsername, esPassword);
            }
            factory.setHttpClientConfig(httpClientConfig.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return factory.getObject();
    }
    
    /**
     * @param config
     * @return create a new test ES client
     */
    private static JestClient createSslMetricsJestClient(ManagerApiMicroServiceConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getMetricsESProtocol());
        builder.append("://"); //$NON-NLS-1$
        builder.append(config.getMetricsESHost());
        builder.append(":"); //$NON-NLS-1$
        builder.append(config.getMetricsESPort());
        String connectionUrl = builder.toString();
        
        JestClientFactory factory = null;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(getKeyManagers(), getTrustManagers(), null);
            HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            SchemeIOSessionStrategy httpsIOSessionStrategy = new SSLIOSessionStrategy(sslContext, hostnameVerifier);
            factory = new JestClientFactory();
            
            HttpClientConfig.Builder httpClientConfig = new HttpClientConfig.Builder(connectionUrl)
                    .multiThreaded(true)
                    .defaultSchemeForDiscoveredNodes("https")
                    .sslSocketFactory(sslSocketFactory) // for sync calls
                    .httpsIOSessionStrategy(httpsIOSessionStrategy) // for async calls
                    .multiThreaded(true)
                    .connTimeout(config.getMetricsESTimeout())
                    .readTimeout(config.getMetricsESTimeout());
            String esUsername = config.getMetricsESUsername();
            String esPassword = config.getMetricsESPassword();
            if (esUsername != null) {
                httpClientConfig.defaultCredentials(esUsername, esPassword);
            }
            factory.setHttpClientConfig(httpClientConfig.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return factory.getObject();
    }
    
    protected static KeyManager[] getKeyManagers() throws Exception {
        
        File clientKeyStorePasswordFile = new File(ApimanStarter.CLIENT_KEYSTORE_PASSWORD_PATH);
        File clientKeyStoreFile = new File(ApimanStarter.CLIENT_KEYSTORE_PATH);
        if (clientKeyStorePasswordFile.exists() && clientKeyStoreFile.exists()) {
            String clientKeyStorePassword = IOUtils.toString(clientKeyStorePasswordFile.toURI());
            if (clientKeyStorePassword!=null) clientKeyStorePassword = clientKeyStorePassword.trim();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance("JKS");
            
            FileInputStream clientFis = new FileInputStream(ApimanStarter.CLIENT_KEYSTORE_PATH);
            
            keyStore.load(clientFis, clientKeyStorePassword.toCharArray());
            clientFis.close();
            kmf.init(keyStore, clientKeyStorePassword.toCharArray());
            return kmf.getKeyManagers();
        } else {
            log.warn("No KeyManager for ES Connection");
            return null;
        }
    }
    
    protected static TrustManager[] getTrustManagers() throws Exception {
        
        File truststorePasswordFile = new File(ApimanStarter.TRUSTSTORE_PASSWORD_PATH);
        File trustStoreFile = new File(ApimanStarter.TRUSTSTORE_PATH);
        TrustManagerFactory tmf = null;
        if (truststorePasswordFile.exists() && trustStoreFile.exists()) {
            String trustStorePassword = IOUtils.toString(truststorePasswordFile.toURI());
            if (trustStorePassword!=null) trustStorePassword = trustStorePassword.trim();
            
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore truststore = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(ApimanStarter.TRUSTSTORE_PATH);
            truststore.load(fis, trustStorePassword.toCharArray());
            fis.close();
            tmf.init(truststore);
            return tmf.getTrustManagers();
        } else {
            log.warn("No TrustManager for ES Connection");
            return null;
        }
    }
}
