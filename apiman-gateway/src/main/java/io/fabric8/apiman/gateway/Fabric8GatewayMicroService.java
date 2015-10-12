package io.fabric8.apiman.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.apiman.gateway.platforms.war.WarEngineConfig;
import io.apiman.gateway.platforms.war.micro.GatewayMicroService;
import io.apiman.gateway.platforms.war.micro.GatewayMicroServicePlatform;
import io.fabric8.utils.Systems;

public class Fabric8GatewayMicroService extends GatewayMicroService {

	@Override
	protected void configureGlobalVars() {
    	
//        System.setProperty("apiman.es.protocol", "http");
//        System.setProperty("apiman.es.host", "localhost");
//        System.setProperty("apiman.es.port", "9200");
//        System.setProperty("apiman.es.cluster-name", "elasticsearch");
        
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
    	
    	 if (Systems.getEnvVarOrSystemProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT) == null) {
            String kubernetesDomain = System.getProperty("KUBERNETES_DOMAIN", "vagrant.f8");
            System.setProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT, "http://apiman-gateway." + kubernetesDomain + "/gateway/");
        }
    	 
        if (Systems.getEnvVarOrSystemProperty("apiman.es.protocol") == null)
        	System.setProperty("apiman.es.protocol", protocol);
        if (Systems.getEnvVarOrSystemProperty("apiman.es.host") == null)
        	System.setProperty("apiman.es.host", host);
        if (Systems.getEnvVarOrSystemProperty("apiman.es.port") == null)
        	System.setProperty("apiman.es.port", hp[1]);
        if (Systems.getEnvVarOrSystemProperty("apiman.es.cluster-name") == null)
        	System.setProperty("apiman.es.cluster-name", "elasticsearch");
        if (Systems.getEnvVarOrSystemProperty(WarEngineConfig.APIMAN_GATEWAY_REGISTRY_CLASS) == null) 
        	System.setProperty(WarEngineConfig.APIMAN_GATEWAY_METRICS_CLASS,"io.apiman.gateway.engine.es.PollCachingESRegistry");
        
        System.out.print("Elastic " + System.getProperty("apiman.es.host"));
        System.out.print(System.getProperty("apiman.es.port"));
        System.out.print(System.getProperty("apiman.es.protocol"));
        System.out.println(System.getProperty("apiman.es.cluster-name"));
        System.out.println("Gateway Registry: " + System.getProperty(WarEngineConfig.APIMAN_GATEWAY_REGISTRY_CLASS));
        System.out.println("Gateway Endpoint: " + System.getProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT));
	}

}
