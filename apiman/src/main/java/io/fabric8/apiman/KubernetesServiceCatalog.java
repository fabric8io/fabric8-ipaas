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

import io.apiman.manager.api.beans.apis.ApiDefinitionType;
import io.apiman.manager.api.beans.apis.EndpointType;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.apiman.manager.api.core.IApiCatalog;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.TemplateList;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.KubernetesServices;
import io.fabric8.utils.Systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

	final public static String SERVICE_PATH     = "apiman.io/servicepath";
	final public static String SERVICE_TYPE     = "apiman.io/servicetype";
	final public static String SERVICE_SCHEME   = "apiman.io/servicescheme";
	final public static String DESCRIPTION_PATH = "apiman.io/descriptionpath";
	final public static String DESCRIPTION_TYPE = "apiman.io/descriptiontype";

	@Override
	public List<AvailableApiBean> search(String keyword) {
		log.info("Searching in Kubernetes with service keyword " + keyword);
		return searchKube(keyword);
	}

	/**
	 *
	 */
	private List<AvailableApiBean> searchKube(String keyword){
		List<AvailableApiBean> availableServiceBeans = new ArrayList<AvailableApiBean>();
		//Obtain a list from Kubernetes, using the Kubernetes API
		KubernetesClient kubernetes = null;
		String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");
		if (kubernetesMasterUrl!=null) {
			kubernetes = new DefaultKubernetesClient(kubernetesMasterUrl);
		} else {
			kubernetes = new DefaultKubernetesClient();
		}
		Map<String,String> iconUrls = new HashMap<String,String>();
	    OpenShiftClient osClient = new DefaultOpenShiftClient(kubernetes.getMasterUrl().toExternalForm());
//
// This is how we'd get all namespaces for the current user. For now we require one apiman per namespace
//	    ProjectList projectList = osClient.projects().list();
//	    for (Project item: projectList.getItems()) {
//	        String namespace = item.getMetadata().getName();
//	    }
	    TemplateList templateList = osClient.templates().list();
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
	    osClient.close();

		Map<String, Service> serviceMap = KubernetesHelper.getServiceMap(kubernetes);

	    for (String serviceName : serviceMap.keySet()) {
			if (keyword==null || keyword.equals("") || keyword.equals("*") || serviceName.toLowerCase().contains(keyword.toLowerCase())) {
				Service service = serviceMap.get(serviceName);
				Map<String,String> annotations = service.getMetadata().getAnnotations();
				String scheme = "http";
				String port = KubernetesServices.serviceToPortOrBlank(service.getMetadata().getName());
				if (port!=null && port.endsWith("443")) scheme = "https";
				if (annotations!=null && annotations.containsKey(SERVICE_SCHEME)) scheme = annotations.get(SERVICE_SCHEME);
				String serviceUrl = KubernetesHelper.getServiceURL(kubernetes, service.getMetadata().getName(),kubernetes.getNamespace(), scheme, true);
				if (! serviceUrl.endsWith("/")) serviceUrl += "/";
				ServiceContract serviceContract = createServiceContract(annotations, serviceUrl);

				AvailableApiBean bean = new AvailableApiBean();
				String name = service.getMetadata().getName();
				bean.setName(name);
				String iconUrlKey = "fabric8." + name + "/iconUrl";
				bean.setIcon(iconUrls.get(iconUrlKey));
				String summaryKey = "fabric8." + name + "/summary";
                if (service.getMetadata().getAnnotations()!=null && service.getMetadata().getAnnotations().keySet().contains(summaryKey)) {
                    String description = service.getMetadata().getAnnotations().get(summaryKey);
                    bean.setDescription(description);
                }
				bean.setEndpoint(serviceContract.getServiceUrl());
				if (serviceContract.getServiceType()!=null) {
					for (EndpointType type: EndpointType.values()) {
						if (type.toString().equalsIgnoreCase(serviceContract.getServiceType())) {
							bean.setEndpointType(EndpointType.valueOf(type.name()));
						}
					}
				} else {
					bean.setEndpointType(null);
				}
				bean.setDefinitionUrl(serviceContract.getDescriptionUrl());
				if (serviceContract.getDescriptionType()!=null) {
					for (ApiDefinitionType type: ApiDefinitionType.values()) {
						if (type.toString().equalsIgnoreCase(serviceContract.getDescriptionType())) {
							bean.setDefinitionType(ApiDefinitionType.valueOf(type.name()));
						}
					}
				} else {
					bean.setDefinitionType(ApiDefinitionType.None);
				}
				if (log.isDebugEnabled()) {
    				log.debug(bean.getName() + " : " + bean.getDescription());
    				log.debug("  " + bean.getEndpoint() + " : " + bean.getEndpointType());
    				log.debug("  " + bean.getDefinitionUrl() + " : " + bean.getDefinitionType());
				}
				availableServiceBeans.add(bean);
			}
		}
	    kubernetes.close();
	    return availableServiceBeans;
	}

	protected ServiceContract createServiceContract(Map<String,String> annotations, String serviceUrl) {

		ServiceContract serviceContract = new ServiceContract();
		if (annotations!=null) {
			serviceContract.setServiceUrl(serviceUrl + annotations.get(SERVICE_PATH));
			serviceContract.setServiceType(annotations.get(SERVICE_TYPE));
			serviceContract.setDescriptionUrl(serviceUrl + annotations.get(DESCRIPTION_PATH));
			serviceContract.setDescriptionType(annotations.get(DESCRIPTION_TYPE));
		} else {
			serviceContract.setServiceUrl(serviceUrl);
		}
		return serviceContract;
	}

	protected class ServiceContract {

		String serviceUrl;
		String serviceType;
		String descriptionUrl;
		String descriptionType;

		public void setServiceUrl(String serviceUrl) {
			this.serviceUrl = serviceUrl;
		}
		public String getServiceUrl() {
			return serviceUrl;
		}
		public String getServiceType() {
			return serviceType;
		}
		public void setServiceType(String serviceType) {
			this.serviceType = serviceType;
		}
		public String getDescriptionUrl() {
			return descriptionUrl;
		}
		public void setDescriptionUrl(String descriptionUrl) {
			this.descriptionUrl = descriptionUrl;
		}
		public String getDescriptionType() {
			return descriptionType;
		}
		public void setDescriptionType(String descriptionType) {
			this.descriptionType = descriptionType;
		}

	}


}
