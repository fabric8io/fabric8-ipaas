package io.fabric8.apiman.gateway;

import io.apiman.gateway.engine.es.PollCachingESRegistry;
import io.apiman.gateway.platforms.war.WarEngineConfig;
import io.apiman.gateway.platforms.war.micro.GatewayMicroService;
import io.apiman.gateway.platforms.war.micro.GatewayMicroServicePlatform;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.KubernetesServices;
import io.fabric8.utils.Systems;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

public class Fabric8GatewayMicroService extends GatewayMicroService {
    
    String gatewayRoute = "http://apiman-gateway";
    
    /**
     * @see io.apiman.gateway.platforms.war.micro.GatewayMicroService#configure()
     */
    @Override
    protected void configure() {
        System.out.println("** Setting API Manager Configuration Properties **");
        KubernetesClient kubernetes = new DefaultKubernetesClient();
        try {
            OpenShiftClient osClient = kubernetes.adapt(OpenShiftClient.class);
            Route route = osClient.routes().withName("apiman-gateway").get();
            gatewayRoute = "http://" + route.getSpec().getHost();
        } catch (Exception e) {
            System.out.println("Warning: Not an Openshift client - no route info can be looked up");
        } finally {
            kubernetes.close();
        }
        super.configure();
        System.out.println("** **********CONFIG COMPLETED*********** **");
    }

	@Override
	protected void configureGlobalVars() {
	   
	    URL elasticEndpoint = resolveServiceEndpoint("elasticsearch-v1", "9200");
    	// Discover and set the gateway endpoint.
    	if (Systems.getEnvVarOrSystemProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT) == null) {
    	    System.out.println("Using Gateway Route " + gatewayRoute);
    	    System.setProperty(GatewayMicroServicePlatform.APIMAN_GATEWAY_ENDPOINT, gatewayRoute + "/gateway/");
        }
        setConfigProperty("apiman.es.protocol", elasticEndpoint.getProtocol());
        setConfigProperty("apiman.es.host", elasticEndpoint.getHost());
        setConfigProperty("apiman.es.port", String.valueOf(elasticEndpoint.getPort()));
	}
	
	/**
	 * @see io.apiman.gateway.platforms.war.micro.GatewayMicroService#configureRegistry()
	 */
	@Override
	protected void configureRegistry() {
        setConfigProperty(WarEngineConfig.APIMAN_GATEWAY_REGISTRY_CLASS, PollCachingESRegistry.class.getName());
	    super.configureRegistry();
	}

    protected void setConfigProperty(String propName, String propValue) {
        if (Systems.getEnvVarOrSystemProperty(propName) == null) {
            System.setProperty(propName, propValue);
        }
        System.out.println("\t" + propName + "=" + System.getProperty(propName));
    }
    
    public URL resolveServiceEndpoint(String serviceName, String defaultPort) {
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
