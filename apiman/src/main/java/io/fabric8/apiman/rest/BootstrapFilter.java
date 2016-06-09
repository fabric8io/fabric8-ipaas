/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.apiman.rest;

import io.apiman.common.logging.IApimanLogger;
import io.apiman.common.util.AbstractMessages;
import io.apiman.common.util.AesEncrypter;
import io.apiman.manager.api.beans.gateways.GatewayBean;
import io.apiman.manager.api.beans.gateways.GatewayType;
import io.apiman.manager.api.beans.idm.PermissionType;
import io.apiman.manager.api.beans.idm.RoleBean;
import io.apiman.manager.api.beans.policies.PolicyDefinitionBean;
import io.apiman.manager.api.beans.summary.PolicyDefinitionSummaryBean;
import io.apiman.manager.api.core.IStorage;
import io.apiman.manager.api.core.IStorageQuery;
import io.apiman.manager.api.core.exceptions.StorageException;
import io.apiman.manager.api.core.logging.ApimanLogger;
import io.fabric8.apiman.ApimanStarter;
import io.fabric8.utils.Systems;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * The Boostrap class loads up default roles and policies into the Apiman backend
 * storage. This only happens once after Apiman starts.
 *
 */
@SuppressWarnings("nls")
@ApplicationScoped
public class BootstrapFilter implements Filter {

	@Inject
	IStorage storage;
	
	@Inject
	IStorageQuery storageQuery;
	
	@Inject @ApimanLogger(BootstrapFilter.class)
	IApimanLogger logger;
	
	public boolean loadDefaultPolicies() {
		boolean isLoaded=true;
		try {
			//1. Find the policies
			logger.info("Looking up /data/all-policyDefs.json on the classpath...");
			InputStream is = getClass().getResourceAsStream("/data/all-policyDefs.json");
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<List<PolicyDefinitionBean>> tRef = new TypeReference<List<PolicyDefinitionBean>>() {};
			List<PolicyDefinitionBean> policyDefList = mapper.readValue(is, tRef);
			logger.info("Found " + policyDefList.size() + " policyDefs");
			//2. Look up the already installed policies
			Map<String,PolicyDefinitionSummaryBean> existingPolicyDefinitions = new HashMap<String,PolicyDefinitionSummaryBean>(); 
			List<PolicyDefinitionSummaryBean> policyDefinitions = storageQuery.listPolicyDefinitions();
			logger.info("Found " + policyDefinitions.size() + " existing policies");
			for (PolicyDefinitionSummaryBean policyDefinitionSummaryBean: policyDefinitions) {
				existingPolicyDefinitions.put(policyDefinitionSummaryBean.getName(),policyDefinitionSummaryBean);
			}
			//3. Store the policies if they are not already installed
			for (PolicyDefinitionBean policyDefinitionBean : policyDefList) {
				String policyName = policyDefinitionBean.getName();
				if (policyDefinitionBean.getId() == null || policyDefinitionBean.getId().equals("")) policyDefinitionBean.setId(policyDefinitionBean.getName().replaceAll(" ", ""));
				if (! existingPolicyDefinitions.containsKey(policyName)) {
					storage.beginTx();
					logger.info("Creating Policy " + policyDefinitionBean.getName());
					storage.createPolicyDefinition(policyDefinitionBean);
					storage.commitTx();
				} else {
					//update if the policyImpl changed
					if (existingPolicyDefinitions.get(policyName).getPolicyImpl().length() != policyDefinitionBean.getPolicyImpl().length()) {
						logger.info("Updating Policy " + policyDefinitionBean.getName());
						storage.beginTx();
						storage.updatePolicyDefinition(policyDefinitionBean);
						storage.commitTx();
					}
				}
			}
		} catch (StorageException | IOException e) {
			isLoaded=false;
			logger.error(e);
		}
		return isLoaded;
	}
	
	public boolean createApimanGateway() {
		boolean isLoaded=true;
		try {
			Date now = new Date();
			//1. Check if the gateway is present already
			String gatewayName = "ApimanGateway";
			if (storage.getGateway(gatewayName) == null) {
				GatewayBean gateway = new GatewayBean();
				String endpoint = ApimanStarter.getGatewayUrl() + "/api";
				String username = Systems.getEnvVarOrSystemProperty(ApimanStarter.APIMAN_GATEWAY_USERNAME, "admin");
				String password = Systems.getEnvVarOrSystemProperty(ApimanStarter.APIMAN_GATEWAY_PASSWORD, "admin123!");
				password = AesEncrypter.encrypt(password);
				String configuration = "{\"endpoint\":\"" + endpoint + "\","
						+ "\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
				gateway.setConfiguration(configuration);
				gateway.setCreatedOn(now);
				gateway.setCreatedBy("admin");
				gateway.setModifiedOn(now);
				gateway.setModifiedBy("admin");
				gateway.setDescription("Default Apiman Gateway configuation created by fabric8. Please make "
						+ "make sure to run the Apiman-Gateway Service, and that 'Test Gateway' is successful. "
						+ "You can update the current configuration info if needed.");
				gateway.setId(gatewayName);
				gateway.setName(gatewayName);
				gateway.setType(GatewayType.REST);
				storage.createGateway(gateway);
			}
		} catch (StorageException e) {
			isLoaded=false;
			logger.error(e);
		}
		return isLoaded;
	}
	
	public boolean loadDefaultRoles() {
		boolean isLoaded=true;
		try {
			Date now = new Date();
			//Organization Owner
			String name = "Organization Owner";
			if (storage.getRole(name) == null) {
				logger.info("Creating Organization Owner Role");
				RoleBean roleBean = new RoleBean();
				roleBean.setAutoGrant(true);
				roleBean.setCreatedBy("admin");
				roleBean.setCreatedOn(now);
				roleBean.setId(name);
				roleBean.setName(name);
				roleBean.setDescription("Automatically granted to the user who creates an Organization.  Grants all privileges.");
				Set<PermissionType> permissions = new HashSet<PermissionType>();
				permissions.add(PermissionType.orgAdmin);
				permissions.add(PermissionType.orgEdit);
				permissions.add(PermissionType.orgView);
				permissions.add(PermissionType.clientAdmin);
				permissions.add(PermissionType.clientEdit);
				permissions.add(PermissionType.clientView);
				permissions.add(PermissionType.planAdmin);
				permissions.add(PermissionType.planEdit);
				permissions.add(PermissionType.planView);
				permissions.add(PermissionType.apiAdmin);
				permissions.add(PermissionType.apiEdit);
				permissions.add(PermissionType.apiView);
				roleBean.setPermissions(permissions);
				storage.createRole(roleBean);
			}
			
			//Application Developer
			name = "Application Developer";
			if (storage.getRole(name) == null) {
				logger.info("Creating Application Developer Role");
				RoleBean roleBean = new RoleBean();
				roleBean.setCreatedBy("admin");
				roleBean.setCreatedOn(now);
				roleBean.setId(name);
				roleBean.setName("Application Developer");
				roleBean.setDescription("Users responsible for creating and managing applications should be granted this role within an Organization.");
				Set<PermissionType> permissions = new HashSet<PermissionType>();
				permissions.add(PermissionType.clientAdmin);
				permissions.add(PermissionType.clientEdit);
				permissions.add(PermissionType.clientView);
				roleBean.setPermissions(permissions);
				storage.createRole(roleBean);
			}
			
			//Service Developer
			name = "Service Developer";
			if (storage.getRole(name) == null) {
				logger.info("Creating Service Developer Role");
				RoleBean roleBean = new RoleBean();
				roleBean.setCreatedBy("admin");
				roleBean.setCreatedOn(now);
				roleBean.setId(name);
				roleBean.setName("Service Developer");
				roleBean.setDescription("Users responsible for creating and managing services should be granted this role within an Organization.");
				Set<PermissionType> permissions = new HashSet<PermissionType>();
				permissions.add(PermissionType.planAdmin);
				permissions.add(PermissionType.planEdit);
				permissions.add(PermissionType.planView);
				permissions.add(PermissionType.apiAdmin);
				permissions.add(PermissionType.apiEdit);
				permissions.add(PermissionType.apiView);
				roleBean.setPermissions(permissions);
				storage.createRole(roleBean);
			}
			
		} catch (StorageException e) {
			isLoaded=false;
			logger.error(e);
		}
		return isLoaded;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		boolean isLoaded = false;
		//keep retrying until loaded
		while (isLoaded != true) {
		    try {
		        //retrying initialization in case the indexes have not yet been created
		        //because Elastic was down.
                storage.initialize();
            } catch (RuntimeException e) {}
			if (loadDefaultPolicies() && loadDefaultRoles() && createApimanGateway()) {
				isLoaded = true;
			} else {
				try {
					Thread.sleep(5000l);
				} catch (InterruptedException e) {
					logger.error(e);
				}
			}
		}
	}

	/**
	 * No-opt filter, we really only care about the init phase to bootstrap apiman.
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		//no-opt, we only cared about bootstrapping on startup
        try {
            chain.doFilter(request, response);
        } finally {
            AbstractMessages.clearLocale();
        }
	}

	@Override
	public void destroy() {}

}
