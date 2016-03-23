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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.apiman.manager.api.beans.apis.ApiDefinitionType;
import io.apiman.manager.api.beans.apis.EndpointType;
import io.apiman.manager.api.beans.summary.ApiNamespaceBean;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.apiman.manager.api.core.IApiCatalog;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.TemplateList;
import io.fabric8.openshift.api.model.User;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;

/**
 * Implementation of @IServiceCatalog that looks up service in Kubernetes. The
 * results are filtered by matching them to the search term passed in. All results
 * are returned in the search term is null, empty or '*'.
 *
 * For the resulting services it tries return the full service URL and Service Protocol
 * (REST, SOAP, etc) as well as it's definition URL and definition Type (WSDL, WADL, Swagger, etc).
 *
 * By default it is assumed that the service run at the root "/" of the serviceUrl, but if
 * it is not, the servicepath annotation can be used to set the path. Additionally the
 * protocol and definitionpath and type can be set. We look for Kubernetes Service
 * Annotations of the form:
 *
	     <li>apiman.io/servicepath,</li>
	     <li>apiman.io/servicetype,</li>
	     <li>apiman.io/servicescheme</li>
	     <li>apiman.io/descriptionpath,</li>
	     <li>apiman.io/descriptiontype</li>
 */
public class KubernetesServiceCatalog implements IApiCatalog  {

    final private static Log log = LogFactory.getLog(KubernetesServiceCatalog.class);
    private static String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");

    final public static String SERVICE_PATH         = "api.service.kubernetes.io/path";
    final public static String SERVICE_PROTOCOL     = "api.service.kubernetes.io/protocol";
    final public static String SERVICE_SCHEME       = "api.service.kubernetes.io/scheme";
    final public static String DESCRIPTION_PATH     = "api.service.kubernetes.io/description-path";
    final public static String DESCRIPTION_LANGUAGE = "api.service.kubernetes.io/description-language";
    
    @Override
    public List<AvailableApiBean> search(String keyword, String namespace) {
        log.info("Searching in Kubernetes with service keyword " + keyword);
        return searchKube(keyword, namespace);
    }
    /**
     * Returns all namespaces owned by the current user.
     */
    @Override
    public List<ApiNamespaceBean> getNamespaces(String currentUser) {
        Config config = new ConfigBuilder().withOauthToken(AuthToken.get()).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        OpenShiftClient osClient = new DefaultOpenShiftClient(config);
        List<ApiNamespaceBean> apiNamespaceList = new ArrayList<ApiNamespaceBean>();
        try {
            User user = osClient.inAnyNamespace().users().withName("~").get();
            if (user.getMetadata().getName().equals(currentUser)) {
                String currentNamespace = osClient.getNamespace();
                // This is how we'd get all namespaces for the current user.
                ProjectList projectList = osClient.projects().list();
                List<String> namespaces = new ArrayList<String>();
                for (Project item: projectList.getItems()) {
                    namespaces.add(item.getMetadata().getName());
                }
                Collections.sort(namespaces);
                for (String namespace: namespaces) {
                    ApiNamespaceBean apiNamespaceBean = new ApiNamespaceBean();
                    apiNamespaceBean.setCurrent(currentNamespace.equals(namespace));
                    apiNamespaceBean.setName(namespace);
                    apiNamespaceBean.setOwnedByUser(true);
                    apiNamespaceList.add(apiNamespaceBean);
                }
            } else {
                log.error("CurrentUser does not correspond to the authToken");
            }
            
        } finally {
            osClient.close();
        }
        return apiNamespaceList;
    }

    /**
     * Returns all available services in the namespace. If namespace is null, use
     * the current namespace.
     */
    private List<AvailableApiBean> searchKube(String keyword, String namespace){
        Config config = new ConfigBuilder().withOauthToken(AuthToken.get()).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        KubernetesClient k8sClient = new DefaultKubernetesClient(config);
        OpenShiftClient osClient = k8sClient.adapt(OpenShiftClient.class);
        List<AvailableApiBean> availableServiceBeans = new ArrayList<AvailableApiBean>();
        try {
            if (namespace==null) namespace = k8sClient.getNamespace();
            //Obtain a list from Kubernetes, using the Kubernetes API
            Map<String,String> iconUrls = new HashMap<String,String>();
            Map<String,String> routeUrls= new HashMap<String,String>();
    
            TemplateList templateList = osClient.templates().inNamespace(namespace).list();
            for (Template item: templateList.getItems()) {
                if (item.getMetadata().getAnnotations() != null) {
                    for (String annotationName:item.getMetadata().getAnnotations().keySet()) {
                        if (annotationName.endsWith("/iconUrl")) {
                            String iconUrl = item.getMetadata().getAnnotations().get(annotationName);
                            iconUrls.put(annotationName, iconUrl);
                        }
                    }
                }
            }
            RouteList routeList = osClient.routes().inNamespace(namespace).list();
            for (Route route: routeList.getItems()) {
                routeUrls.put(route.getMetadata().getName(), route.getSpec().getHost());
            }
            Map<String, Service> serviceMap = KubernetesHelper.getServiceMap(k8sClient, namespace);
            for (String serviceName : serviceMap.keySet()) {
                if (keyword==null || keyword.equals("") || keyword.equals("*") || serviceName.toLowerCase().contains(keyword.toLowerCase())) {
                    Service service = serviceMap.get(serviceName);
                    String clusterIP = service.getSpec().getClusterIP();
                    if ("None".equals(clusterIP)) {
                        log.debug("Ignoring headless service " + serviceName);
                    } else {
                        Map<String,String> annotations = service.getMetadata().getAnnotations();
                        String port = "";
                        if (service.getSpec().getPorts().size() > 0) port = String.valueOf(service.getSpec().getPorts().get(0).getPort());
                        URL url = ApimanStarter.resolveServiceEndpoint(serviceName, port);
                        String serviceUrl = url.toExternalForm();
                        String scheme = url.getProtocol();
                        if (annotations!=null && annotations.containsKey(SERVICE_SCHEME)) {
                            scheme = annotations.get(SERVICE_SCHEME);
                            serviceUrl = serviceUrl.replace(url.getProtocol(), scheme);
                        }
                        String routeUrl = routeUrls.get(serviceName);
                        if (routeUrl!=null) routeUrl = scheme + "://" + routeUrl;
                        ServiceContract serviceContract = createServiceContract(annotations, serviceUrl, routeUrl);
    
                        AvailableApiBean bean = new AvailableApiBean();
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
                        String iconUrlKey = "fabric8." + name + "/iconUrl";
                        bean.setIcon(iconUrls.get(iconUrlKey));
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
                        availableServiceBeans.add(bean);
                    }
                }
            }
        } finally {
            k8sClient.close();
            
        }
        return availableServiceBeans;
    }

    protected ServiceContract createServiceContract(Map<String,String> annotations, String serviceUrl, String routeUrl) {

        if (! serviceUrl.endsWith("/")) serviceUrl += "/";
        if (routeUrl!=null && !routeUrl.endsWith("/")) routeUrl += "/";
        ServiceContract serviceContract = new ServiceContract();
        if (annotations!=null) {
            serviceContract.serviceUrl           = serviceUrl + (annotations.get(SERVICE_PATH)==null ? "" : annotations.get(SERVICE_PATH));
            serviceContract.serviceRouteUrl      = routeUrl + (annotations.get(SERVICE_PATH)==null ? "" : annotations.get(SERVICE_PATH));
            serviceContract.serviceProtocol      = annotations.get(SERVICE_PROTOCOL);
            serviceContract.descriptionUrl       = serviceUrl + (annotations.get(DESCRIPTION_PATH)==null ? "" : annotations.get(DESCRIPTION_PATH));
            serviceContract.descriptionRouteUrl  = routeUrl + (annotations.get(DESCRIPTION_PATH)==null ? "" : annotations.get(DESCRIPTION_PATH));
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

}
