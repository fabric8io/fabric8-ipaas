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

import io.apiman.manager.api.beans.services.EndpointType;
import io.apiman.manager.api.beans.services.ServiceDefinitionType;
import io.apiman.manager.api.beans.summary.AvailableServiceBean;
import io.apiman.manager.api.core.IServiceCatalog;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.TemplateList;
import io.fabric8.openshift.client.DefaultOpenshiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;

import java.util.ArrayList;
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
 * it is not, or if there are multiple endpoints in one service, the servicepath annotation can
 * be used to set the path. Additionally the protocol and definitionpath and type can be set.
 * For example for single andpoint we look for for Kubernetes service annotations of the form:
	     servicepath,
	     protocol,
	     definitionpath,
	     definitiontype
	For multiple endpoints the service developer can name the endpoints
	     servicepath.myfirstservice,
	     protocol.myfirstservice,
	     definitionpath.myfirstservice,
	     definitiontype.myfirstservice,
	     servicepath.mysecondservice,
	     protocol.mysecondservice,
	     definitionpath.mysecondservice,
	     definitiontype.mysecondservice
 */
public class KubernetesServiceCatalog implements IServiceCatalog  {

	@Override
	public List<AvailableServiceBean> search(String keyword) {
		System.out.println("Searching in Kubernetes with service keyword " + keyword);
		return searchKube(keyword);
	}
	
	/**
	 * 
	 */
	private List<AvailableServiceBean> searchKube(String keyword){
		List<AvailableServiceBean> availableServiceBeans = new ArrayList<AvailableServiceBean>();
		//Obtain a list from Kubernetes, using the Kubernetes API
		String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER", "https://172.28.128.4:8443");
		String kubernetesNamespace = Systems.getEnvVarOrSystemProperty("KUBERNETES_NAMESPACE", "default");
		if (Systems.getEnvVarOrSystemProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY) == null) 
			System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		OpenShiftClient osClient = new DefaultOpenshiftClient(kubernetesMasterUrl);
		TemplateList templateList = osClient.templates().list();
		Map<String,String> descriptions = new HashMap<String,String>();
		for(Template template : templateList.getItems()) {
			String name = template.getMetadata().getName();
			for (String annotation : template.getMetadata().getAnnotations().keySet()) {
				if (annotation.contains("summary")) {
					String description = template.getMetadata().getAnnotations().get(annotation);
					descriptions.put(name, description);
				}
			}
		}
		osClient.close();
		
		KubernetesClient kubernetes = new DefaultKubernetesClient(kubernetesMasterUrl);
		Map<String, Service> serviceMap = KubernetesHelper.getServiceMap(kubernetes, kubernetesNamespace);
		
	    for (String serviceName : serviceMap.keySet()) {
			if (keyword==null || keyword.equals("") || keyword.equals("*") || serviceName.toLowerCase().contains(keyword.toLowerCase())) {
				Service service = serviceMap.get(serviceName);
				Map<String,String> annotations = service.getMetadata().getAnnotations();
				String scheme = "http";
				String port = KubernetesHelper.serviceToPort(service.getMetadata().getName());
				if (port!=null && port.endsWith("443")) scheme = "https";
				if (annotations!=null && annotations.containsKey("servicescheme")) scheme = annotations.get("servicescheme");
				String serviceUrl = KubernetesHelper.getServiceURL(kubernetes, service.getMetadata().getName(),kubernetesNamespace, scheme, true);
				List<ServiceContract> serviceContracts = createServiceContract(annotations, serviceUrl);
				
				for (ServiceContract serviceContract: serviceContracts) {
					AvailableServiceBean bean = new AvailableServiceBean();
					bean.setName(service.getMetadata().getName() + serviceContract.getName());
					bean.setDescription(descriptions.get(service.getMetadata().getName()));
					bean.setEndpoint(serviceContract.getServiceUrl());
					if (serviceContract.getProtocol()!=null) {
						for (EndpointType type: EndpointType.values()) {
							if (type.toString().equalsIgnoreCase(serviceContract.getProtocol())) {
								bean.setEndpointType(EndpointType.valueOf(type.name()));
							}
						}
					} else {
						bean.setEndpointType(null);
					}
					bean.setDefinitionUrl(serviceContract.getDescriptionUrl());
					if (serviceContract.getDescriptionType()!=null) {
						for (ServiceDefinitionType type: ServiceDefinitionType.values()) {
							if (type.toString().equalsIgnoreCase(serviceContract.getDescriptionType())) {
								bean.setDefinitionType(ServiceDefinitionType.valueOf(type.name()));
							}
						}
					} else {
						bean.setDefinitionType(ServiceDefinitionType.None);
					}
					availableServiceBeans.add(bean);
				}
			} 
		}
	    kubernetes.close();
	    return availableServiceBeans;
	}
	
	protected List<ServiceContract> createServiceContract(Map<String,String> annotations, String serviceUrl) {
		
		List<ServiceContract> serviceContracts = new ArrayList<ServiceContract>();
		if (annotations!=null) {
			for (String key: annotations.keySet()) {
				if (key.startsWith("servicepath")) {
					ServiceContract serviceContract = new ServiceContract();
					String[] annotation = key.split("\\.");
					String name = "";
					if (annotation.length > 1) name = "/" + annotation[1];
					serviceContract.setName(name);
					serviceContract.setServiceUrl(serviceUrl + "/" + annotations.get(key));
					String protocolKey = key.replace("servicepath", "protocol");
					if (annotations.containsKey(protocolKey)) {
						serviceContract.setProtocol(annotations.get(protocolKey));
					}
					String descriptionKey = key.replace("servicepath", "descriptionpath");
					if (annotations.containsKey(descriptionKey)) {
						serviceContract.setDescriptionUrl(serviceUrl + "/" + annotations.get(descriptionKey));
					}
					String definitionTypeKey = key.replace("servicepath", "descriptiontype");
					if (annotations.containsKey(definitionTypeKey)) {
						serviceContract.setDescriptionType(annotations.get(definitionTypeKey));
					}
					serviceContracts.add(serviceContract);
				}
			}
		}
		//add default contract for the root application "/"
		if (serviceContracts.size()==0) {
			ServiceContract serviceContract = new ServiceContract();
			serviceContract.setName("");
			serviceContract.setServiceUrl(serviceUrl + "/");
			if (annotations!=null) {
				for (String key: annotations.keySet()) {
					if (key.startsWith("protocol")) {
						serviceContract.setProtocol(annotations.get(key));
					}
					if (key.startsWith("descriptionpath")) {
						serviceContract.setDescriptionUrl(serviceUrl + "/" + annotations.get(key));
					}
					if (key.startsWith("descriptiontype")) {
						serviceContract.setDescriptionType(annotations.get(key));
					}
				}
			}
			serviceContracts.add(serviceContract);
		}
		return serviceContracts;
	}
	
	public static void main(String[] args) {
		KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
		List<AvailableServiceBean> beanList = catalog.search("");
		for (AvailableServiceBean bean: beanList) {
			System.out.println(bean.getName() + " " + bean.getEndpoint() + " " + bean.getEndpointType());
			System.out.println(bean.getDescription() + " " + bean.getDefinitionUrl() + " " + bean.getDefinitionType());
		}
	}
	
	protected class ServiceContract {
		
		String name;
		String serviceUrl;
		String protocol;
		String descriptionUrl;
		String descriptionType;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getServiceUrl() {
			return serviceUrl;
		}
		public String getProtocol() {
			return protocol;
		}
		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}
		public void setServiceUrl(String serviceUrl) {
			this.serviceUrl = serviceUrl;
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
