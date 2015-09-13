package io.fabric8.apiman;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.apiman.KubernetesServiceCatalog;

import org.junit.Test;

public class KubernetesServiceCatalogTest {

	@Test
	public void singleServiceAnnotations() {
		String serviceUrl = "http://localhost:8080";
		KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
		Map<String,String> annotations = new HashMap<String,String>();
		annotations.put("servicepath", "cxfcdi");
		annotations.put("protocol", "rest");
		annotations.put("descriptionpath", "_?wsdl");
		annotations.put("descriptiontype", "wsdl");
		List<KubernetesServiceCatalog.ServiceContract> scList = catalog.createServiceContract(annotations, serviceUrl);
		KubernetesServiceCatalog.ServiceContract sc = scList.get(0);
		assertEquals("", sc.getName());
		assertEquals("http://localhost:8080/cxfcdi", sc.getServiceUrl());
		assertEquals("rest", sc.getProtocol());
		assertEquals("http://localhost:8080/_?wsdl", sc.getDescriptionUrl());
		assertEquals("wsdl", sc.getDescriptionType());
	}
	
	@Test
	public void namedServiceAnnotations() {
		String serviceUrl = "http://localhost:8080";
		KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
		Map<String,String> annotations = new HashMap<String,String>();
		annotations.put("servicepath.cxfcdi", "cxfcdi");
		annotations.put("protocol.cxfcdi", "rest");
		annotations.put("descriptionpath.cxfcdi", "_?wsdl");
		annotations.put("descriptiontype.cxfcdi", "wsdl");
		
		List<KubernetesServiceCatalog.ServiceContract> scList = catalog.createServiceContract(annotations, serviceUrl);
		KubernetesServiceCatalog.ServiceContract sc = scList.get(0);
		assertEquals("/cxfcdi", sc.getName());
		assertEquals("http://localhost:8080/cxfcdi", sc.getServiceUrl());
		assertEquals("rest", sc.getProtocol());
		assertEquals("http://localhost:8080/_?wsdl", sc.getDescriptionUrl());
		assertEquals("wsdl", sc.getDescriptionType());
	}
	
	@Test
	public void twoNamedServiceAnnotations() {
		String serviceUrl = "http://localhost:8080";
		KubernetesServiceCatalog catalog = new KubernetesServiceCatalog();
		Map<String,String> annotations = new HashMap<String,String>();
		annotations.put("servicepath.cxfcdi", "cxfcdi");
		annotations.put("protocol.cxfcdi", "rest");
		annotations.put("descriptionpath.cxfcdi", "_?wsdl");
		annotations.put("descriptiontype.cxfcdi", "wsdl");
		
		annotations.put("servicepath.api", "api");
		annotations.put("protocol.api", "rest");
		annotations.put("descriptionpath.api", "_?wadl");
		annotations.put("descriptiontype.api", "wadl");
		
		List<KubernetesServiceCatalog.ServiceContract> scList = catalog.createServiceContract(annotations, serviceUrl);
		
		assertEquals(2, scList.size());
		
		assertTrue(scList.get(0).getName().contains("cxfcdi") || scList.get(1).getName().contains("cxfcdi"));
		assertTrue(scList.get(0).getName().contains("api") || scList.get(1).getName().contains("api"));
		
		for (KubernetesServiceCatalog.ServiceContract sc : scList) {
			
			if (sc.getName().contains("cxfcdi")) {
				assertEquals("/cxfcdi", sc.getName());
				assertEquals("http://localhost:8080/cxfcdi", sc.getServiceUrl());
				assertEquals("rest", sc.getProtocol());
				assertEquals("http://localhost:8080/_?wsdl", sc.getDescriptionUrl());
				assertEquals("wsdl", sc.getDescriptionType());
			}
			
			if (sc.getName().contains("api")) {
				assertEquals("/api", sc.getName());
				assertEquals("http://localhost:8080/api", sc.getServiceUrl());
				assertEquals("rest", sc.getProtocol());
				assertEquals("http://localhost:8080/_?wadl", sc.getDescriptionUrl());
				assertEquals("wadl", sc.getDescriptionType());
			}
		}
	}

}
