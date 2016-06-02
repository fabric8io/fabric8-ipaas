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

import io.apiman.manager.api.beans.summary.ApiNamespaceBean;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.apiman.manager.api.core.IApiCatalog;
import io.apiman.manager.api.security.impl.DefaultSecurityContext;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.TemplateList;
import io.fabric8.openshift.api.model.User;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	     <li>api.service.kubernetes.io/path,</li>
	     <li>api.service.kubernetes.io/protocol,</li>
	     <li>api.service.kubernetes.io/scheme</li>
	     <li>api.service.kubernetes.io/description-path,</li>
	     <li>api.service.kubernetes.io/description-language</li>
 */
public class KubernetesServiceCatalog implements IApiCatalog  {

    final private static Log log = LogFactory.getLog(KubernetesServiceCatalog.class);
    private static String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");

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
        String authHeader = DefaultSecurityContext.servletRequest.get().getHeader("Authorization");
        List<ApiNamespaceBean> apiNamespaceList = new ArrayList<ApiNamespaceBean>();
        if (authHeader != null && authHeader.toUpperCase().startsWith("BEARER")) {
            Config config = new ConfigBuilder().withOauthToken(authHeader.substring(7)).build();
            if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
            NamespacedOpenShiftClient osClient = new DefaultOpenShiftClient(config);

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
                        log.info(apiNamespaceBean.getName() + "\t" + apiNamespaceBean.isOwnedByUser() + "\t" + apiNamespaceBean.isCurrent());
                    }
                    return apiNamespaceList;
                } else {
                    String error = String.format("CurrentUser '{}' does not correspond to the authHeader '{}'", currentUser, authHeader);
                    log.error(error);
                    throw new RuntimeException(error);
                }

            } finally {
                osClient.close();
            }
        } else {
            log.error("Could not find AuthHeader");
            throw new RuntimeException("Could not find AuthHeader");
        }
    }

    /**
     * Returns all available services in the namespace. If namespace is null, use
     * the current namespace.
     */
    private List<AvailableApiBean> searchKube(String keyword, String namespace){
        Config config = new ConfigBuilder().withOauthToken(AuthToken.get()).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        KubernetesClient k8sClient = new DefaultKubernetesClient(config);
        OpenShiftClient osClient = new DefaultOpenShiftClient(config);
        List<AvailableApiBean> availableServiceBeans = new ArrayList<AvailableApiBean>();
        try {
            if (namespace==null) namespace = k8sClient.getNamespace();
            //Obtain a list from Kubernetes, using the Kubernetes API
            Map<String,String> iconUrls = new HashMap<String,String>();
            TemplateList templateList = osClient.inNamespace(namespace).templates().inNamespace(namespace).list();
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

            Map<String, Service> serviceMap = KubernetesHelper.getServiceMap(k8sClient, namespace);
            Kubernetes2ApimanMapper mapper = new Kubernetes2ApimanMapper(osClient);
            for (String serviceName : serviceMap.keySet()) {
                if (keyword==null || keyword.equals("") || keyword.equals("*") || serviceName.toLowerCase().contains(keyword.toLowerCase())) {
                    Service service = serviceMap.get(serviceName);
                    AvailableApiBean bean = mapper.createAvailableApiBean(service, iconUrls);
                    if (bean!=null) availableServiceBeans.add(bean);
                }
            }
        } finally {
            k8sClient.close();
            osClient.close();
        }
        return availableServiceBeans;
    }



}
