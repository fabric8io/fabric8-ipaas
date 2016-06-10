package io.fabric8.apiman.gateway;

import java.io.File;
import java.net.URL;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import io.apiman.gateway.engine.GatewayConfigProperties;
import io.apiman.gateway.engine.es.PollCachingESRegistry;
import io.apiman.gateway.platforms.war.micro.GatewayMicroService;
import io.apiman.gateway.platforms.war.micro.GatewayMicroServicePlatform;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;

public class Fabric8GatewayMicroService extends GatewayMicroService {
    
    private URL elasticEndpoint = null;
    
    private Server sslServer;
    
    final private static Log log = LogFactory.getLog(Fabric8GatewayMicroService.class);
    
    protected void startSsl() throws Exception {
        long startTime = System.currentTimeMillis();
        
        //Secret should be mounted at /secret
        File passwordFile = new File(ApimanGatewayStarter.KEYSTORE_PASSWORD_PATH);
        String password = IOUtils.toString(passwordFile.toURI());
        if (password!=null) password = password.trim();
        
        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());
        
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(ApimanGatewayStarter.KEYSTORE_PATH);
        sslContextFactory.setKeyStorePassword(password);
        sslContextFactory.setKeyManagerPassword(password);
        
        // Create the server.
        int serverPort = serverPort();
        log.info("**** Starting SslServer (" + getClass().getSimpleName() + ") on port: " + serverPort);
        sslServer = new Server();
        
        ServerConnector sslConnector = new ServerConnector(sslServer,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https));
        sslConnector.setPort(serverPort);
        sslServer.setConnectors(new Connector[] { sslConnector});
        
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        addModulesToJetty(handlers);

        sslServer.setHandler(handlers);
        sslServer.start();
        long endTime = System.currentTimeMillis();
        log.info("******* Started in " + (endTime - startTime) + "ms");
    }
    
    /**
     * Stop the server.
     * @throws Exception when any exception occurs
     */
    public void stopSsl() throws Exception {
        sslServer.stop();
    }
    
    public void joinSsl() throws InterruptedException {
        sslServer.join();
    }
    
    public Fabric8GatewayMicroService(URL elasticEndpoint) {
        this.elasticEndpoint = elasticEndpoint;
        super.configure();
        log.info("** **********CONFIG COMPLETED*********** **");
    }


	@Override
	protected void configureGlobalVars() {
	   
	    String gatewayRoute = "http://apiman-gateway";
	    DefaultOpenShiftClient osClient = new DefaultOpenShiftClient();
	    try {
	        Route route = osClient.routes().withName("apiman-gateway").get();
            gatewayRoute = "http://" + route.getSpec().getHost();
	    } catch (Exception e) {
            log.warn("Warning: Not an Openshift client - no route info can be looked up");
        } finally {
            osClient.close();
        }
    	// Discover and set the gateway endpoint.
    	if (Systems.getEnvVarOrSystemProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT) == null) {
    	    log.info("Using Gateway Route " + gatewayRoute);
    	    System.setProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT, gatewayRoute + "/gateway/");
        }
        setConfigProperty("apiman.es.protocol", elasticEndpoint.getProtocol());
        setConfigProperty("apiman.es.host"    , elasticEndpoint.getHost());
        setConfigProperty("apiman.es.port"    , String.valueOf(elasticEndpoint.getPort()));
        setConfigProperty("apiman-gateway.registry.client.type", Fabric8GatewayEsClientFactory.class.getName());
        String esIndexPrefix = Systems.getEnvVarOrSystemProperty("apiman.es.index.prefix",".apiman_");
        if (esIndexPrefix != null) {
            log.info("Setting index prefix to " + esIndexPrefix);
            setConfigProperty("apiman-gateway.registry.client.index" , esIndexPrefix + "gateway");
            setConfigProperty("apiman-gateway.metrics.client.index" , esIndexPrefix + "metrics");
            setConfigProperty("apiman-gateway.components.ICacheStoreComponent.client.index"  , esIndexPrefix + "cache");
        }
	}
	
	/**
	 * @see io.apiman.gateway.platforms.war.micro.GatewayMicroService#configureRegistry()
	 */
	@Override
	protected void configureRegistry() {
        setConfigProperty(GatewayConfigProperties.REGISTRY_CLASS, PollCachingESRegistry.class.getName());
	    super.configureRegistry();
	}

    protected void setConfigProperty(String propName, String propValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, propValue);
        }
        log.info("\t" + propName + "=" + System.getProperty(propName));
    }
    
    @Override
    protected void addApiAuthFilter(ServletContextHandler apiManServer) {
        apiManServer.addFilter(Fabric8AuthenticationFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    }
    
}
