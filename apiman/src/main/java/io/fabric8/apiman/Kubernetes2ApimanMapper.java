package io.fabric8.apiman;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.apiman.manager.api.beans.apis.ApiDefinitionType;
import io.apiman.manager.api.beans.apis.EndpointType;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.client.OpenShiftClient;

public class Kubernetes2ApimanMapper {

    final public static String SERVICE_PATH         = "api.service.kubernetes.io/path";
    final public static String SERVICE_PROTOCOL     = "api.service.kubernetes.io/protocol";
    final public static String SERVICE_SCHEME       = "api.service.kubernetes.io/scheme";
    final public static String DESCRIPTION_PATH     = "api.service.kubernetes.io/description-path";
    final public static String DESCRIPTION_LANGUAGE = "api.service.kubernetes.io/description-language";

    final private static Log log = LogFactory.getLog(Kubernetes2ApimanMapper.class);
    private OpenShiftClient osClient = null;

    public Kubernetes2ApimanMapper(OpenShiftClient osClient) {
        super();
        this.osClient = osClient;
    }

    public AvailableApiBean createAvailableApiBean(Service service, Map<String,String> iconUrls) {

        Map<String,String> routeUrls= new HashMap<String,String>();
        String namespace = service.getMetadata().getNamespace();
        RouteList routeList = osClient.routes().inNamespace(namespace).list();
        for (Route route: routeList.getItems()) {
            routeUrls.put(route.getMetadata().getName(), route.getSpec().getHost());
        }
        
        AvailableApiBean bean = null;
        String clusterIP = service.getSpec().getClusterIP();
        String serviceName = service.getMetadata().getName();
        if ("None".equals(clusterIP)) {
            log.debug("Ignoring headless service " + serviceName);
        } else {
            bean = new AvailableApiBean();
            Map<String,String> annotations = service.getMetadata().getAnnotations();
            String port = "";
            if (service.getSpec().getPorts().size() > 0) port = String.valueOf(service.getSpec().getPorts().get(0).getPort());
            String scheme = "http";
            if (annotations!=null && annotations.containsKey(SERVICE_SCHEME)) {
                scheme = annotations.get(SERVICE_SCHEME);
            }
            String serviceUrl = getUrl(scheme, serviceName, namespace, service.getSpec().getPortalIP(), port);
            String routeUrl = routeUrls.get(serviceName);
            if (routeUrl!=null) routeUrl = scheme + "://" + routeUrl;
            ServiceContract serviceContract = createServiceContract(annotations, serviceUrl, routeUrl);

            String name = service.getMetadata().getName();
            bean.setName(name);
            bean.setId(service.getMetadata().getUid());
            bean.setNamespace(namespace);
            bean.setInternal(false);
            Map<String,String> labels = service.getMetadata().getLabels();

            Set<String> tags = new HashSet<String>();
            if (labels!=null) {
                for (String key: labels.keySet()) {
                    tags.add(key + "=" + labels.get(key));
                    if (("group".equals(key) && labels.get(key).startsWith("io.fabric8")) ||
                            ("provider".equals(key) && labels.get(key).equals("kubernetes")) ||
                            ("router".equals(key) && labels.get(key).equals("router")) ||
                            ("docker-registry".equals(key) && labels.get(key).equals("default"))) {
                        bean.setInternal(true);
                    }
                }
            }
            bean.setTags(tags);
            if (routeUrl!=null) {
                bean.setEndpoint(scheme + "://" + routeUrl + "/");
            }
            if (iconUrls!=null) {
                String iconUrlKey = "fabric8." + name + "/iconUrl";
                bean.setIcon(iconUrls.get(iconUrlKey));
            }
            String summaryKey = "fabric8." + name + "/summary";
            if (service.getMetadata().getAnnotations()!=null && service.getMetadata().getAnnotations().keySet().contains(summaryKey)) {
                String description = service.getMetadata().getAnnotations().get(summaryKey);
                bean.setDescription(description);
            }
            bean.setEndpoint(serviceContract.serviceUrl);
            bean.setRouteEndpoint(serviceContract.serviceRouteUrl);
            if (serviceContract.serviceProtocol!=null) {
                for (EndpointType type: EndpointType.values()) {
                    if (type.toString().equalsIgnoreCase(serviceContract.serviceProtocol)) {
                        bean.setEndpointType(EndpointType.valueOf(type.name()));
                    }
                }
            } else {
                bean.setEndpointType(null);
            }
            bean.setDefinitionUrl(serviceContract.descriptionUrl);
            bean.setRouteDefinitionUrl(serviceContract.descriptionRouteUrl);
            if (serviceContract.descriptionLanguage!=null) {
                for (ApiDefinitionType type: ApiDefinitionType.values()) {
                    if (type.toString().equalsIgnoreCase(serviceContract.descriptionLanguage)) {
                        bean.setDefinitionType(ApiDefinitionType.valueOf(type.name()));
                    }
                }
            } else {
                bean.setDefinitionType(ApiDefinitionType.None);
            }
            log.info(bean.getName() + " with definition set to " + bean.getRouteDefinitionUrl());
            if (log.isDebugEnabled()) {
                log.debug(bean.getName() + " : " + bean.getDescription());
                log.debug("  " + bean.getEndpoint() + " : " + bean.getEndpointType());
                log.debug("  " + bean.getDefinitionUrl() + " : " + bean.getDefinitionType());
            }
        }
        return bean;
    }

    protected ServiceContract createServiceContract(Map<String,String> annotations, String serviceUrl, String routeUrl) {

        if (! serviceUrl.endsWith("/")) serviceUrl += "/";
        if (routeUrl!=null && !routeUrl.endsWith("/")) routeUrl += "/";
        ServiceContract serviceContract = new ServiceContract();
        if (annotations!=null) {
            serviceContract.serviceUrl           = serviceUrl + (annotations.get(SERVICE_PATH)    ==null ? "" : annotations.get(SERVICE_PATH));
            serviceContract.serviceRouteUrl      = routeUrl   + (annotations.get(SERVICE_PATH)    ==null ? "" : annotations.get(SERVICE_PATH));
            serviceContract.serviceProtocol      = (annotations.get(SERVICE_PROTOCOL)==null ? "REST" : annotations.get(SERVICE_PROTOCOL));
            serviceContract.descriptionUrl       = (annotations.get(DESCRIPTION_PATH)==null ? null : (serviceUrl + annotations.get(DESCRIPTION_PATH)));
            serviceContract.descriptionRouteUrl  = (annotations.get(DESCRIPTION_PATH)==null ? null : (routeUrl   + annotations.get(DESCRIPTION_PATH)));
            serviceContract.descriptionLanguage  = annotations.get(DESCRIPTION_LANGUAGE);
        } else {
            serviceContract.serviceUrl = serviceUrl;
        }
        return serviceContract;
    }

    protected class ServiceContract {

        String serviceUrl;
        String serviceRouteUrl;
        String serviceProtocol;
        String descriptionUrl;
        String descriptionRouteUrl;
        String descriptionLanguage;
    }
    
    protected String getUrl(String scheme, String serviceName, String namespace, String defaultIpAddress, String port) {
        String hostname;
        try {
            //lookup in the current namespace
            InetAddress initAddress = InetAddress.getByName(serviceName + "." + namespace);
            hostname = initAddress.getCanonicalHostName();
            log.debug("Resolved hostname using DNS: " + hostname);
        } catch (UnknownHostException e) {
            hostname = defaultIpAddress;
        }
        return MessageFormat.format("{0}://{1}:{2}", scheme, hostname, port);
    }
}
