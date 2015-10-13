package io.fabric8.apiman.gateway;

import io.apiman.gateway.engine.es.PollCachingESRegistry;
import io.apiman.gateway.platforms.war.WarEngineConfig;
import io.apiman.gateway.platforms.war.micro.GatewayMicroService;
import io.apiman.gateway.platforms.war.micro.GatewayMicroServicePlatform;
import io.fabric8.utils.Systems;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Fabric8GatewayMicroService extends GatewayMicroService {
    
    /**
     * Constructor.
     */
    public Fabric8GatewayMicroService() {
    }
    
    /**
     * @see io.apiman.gateway.platforms.war.micro.GatewayMicroService#configure()
     */
    @Override
    protected void configure() {
        System.out.println("** Setting API Manager Configuration Properties **");
        super.configure();
        System.out.println("** ******************************************** **");
    }

	@Override
	protected void configureGlobalVars() {
    	String host = null;
		try {
			InetAddress initAddress = InetAddress.getByName("ELASTICSEARCH");
			host = initAddress.getCanonicalHostName();
		} catch (UnknownHostException e) {
		    System.out.println("Could not resolve DNS for ELASTICSEARCH, trying ENV settings next.");
		}
    	String hostAndPort = Systems.getServiceHostAndPort("ELASTICSEARCH", "localhost", "9200");
    	String[] hp = hostAndPort.split(":");
    	if (host == null) {
    	    System.out.println("ELASTICSEARCH host:port is set to " + hostAndPort + " using ENV settings.");
    		host = hp[0];
    	}
    	String protocol = Systems.getEnvVarOrSystemProperty("ELASTICSEARCH_PROTOCOL", "http");
    	
    	// Discover and set the gateway endpoint.
    	if (Systems.getEnvVarOrSystemProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT) == null) {
    	    String kubernetesDomain = System.getProperty("KUBERNETES_DOMAIN", "vagrant.f8");
    	    System.setProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT, "http://apiman-gateway." + kubernetesDomain + "/gateway/");
        }

        setConfigProperty("apiman.es.protocol", protocol);
        setConfigProperty("apiman.es.host", host);
        setConfigProperty("apiman.es.port", hp[1]);
        setConfigProperty("apiman.es.username", "");
        setConfigProperty("apiman.es.password", "");
	}
	
	/**
	 * @see io.apiman.gateway.platforms.war.micro.GatewayMicroService#configureRegistry()
	 */
	@Override
	protected void configureRegistry() {
        setConfigProperty(WarEngineConfig.APIMAN_GATEWAY_METRICS_CLASS, PollCachingESRegistry.class.getName());
	    super.configureRegistry();
	}

    protected void setConfigProperty(String propName, String propValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, propValue);
        }
        System.out.println("\t" + propName + "=" + System.getProperty(propName));
    }
    
}
