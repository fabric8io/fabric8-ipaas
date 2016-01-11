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

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.fabric8.apiman.KubernetesServiceCatalog;

import org.junit.Ignore;
import org.junit.Test;

public class KubernetesServiceCatalogTest {

	@Test @Ignore
	public void singleServiceAnnotations() {
		String serviceUrl = "http://localhost:8080/";
		KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
		Map<String,String> annotations = new HashMap<String,String>();
		annotations.put("apiman.io/servicepath", "cxfcdi");
		annotations.put("apiman.io/servicetype", "rest");
		annotations.put("apiman.io/descriptionpath", "_?wsdl");
		annotations.put("apiman.io/descriptiontype", "wsdl");
		KubernetesServiceCatalog.ServiceContract sc = catalog.createServiceContract(annotations, serviceUrl);
	
		assertEquals("http://localhost:8080/cxfcdi", sc.getServiceUrl());
		assertEquals("rest", sc.getServiceType());
		assertEquals("http://localhost:8080/_?wsdl", sc.getDescriptionUrl());
		assertEquals("wsdl", sc.getDescriptionType());
	}
	
	@Test @Ignore
	public void kube() {
	    
	    System.getProperties().put("KUBERNETES_MASTER", "https://192.168.100.37:8443");
	    System.getProperties().put("KUBERNETES_TRUST_CERTIFICATE", "true");
	    KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
	    List<AvailableApiBean> beans = catalog.search("*");
	    for (AvailableApiBean availableServiceBean : beans) {
            System.out.println("Bean=" + availableServiceBean.getDescription());
        }
	}

}
