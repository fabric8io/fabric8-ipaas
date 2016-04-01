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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.apiman.manager.api.beans.BeanUtils;
import io.apiman.manager.api.beans.apis.NewApiBean;
import io.apiman.manager.api.beans.idm.GrantRolesBean;
import io.apiman.manager.api.beans.orgs.NewOrganizationBean;
import io.apiman.manager.api.beans.summary.ApiSummaryBean;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.apiman.manager.api.beans.summary.OrganizationSummaryBean;
import io.apiman.manager.api.rest.contract.IOrganizationResource;
import io.apiman.manager.api.rest.contract.IUserResource;
import io.apiman.manager.api.rest.contract.exceptions.OrganizationNotFoundException;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;

/**
 * A simple implementation of an bearer token filter that checks the validity of
 * an incoming bearer token with the OpenShift issuer. The OpenShift call
 * returns a JSON from which the UserPrincipal can be set.
 * 
 */
@SuppressWarnings("nls")
@ApplicationScoped
public class Kubernetes2ApimanFilter implements Filter {
    
    @Inject
    IUserResource userResource;
    
    @Inject
    IOrganizationResource organizationResource;
    
    final private static Log log = LogFactory.getLog(Kubernetes2ApimanFilter.class);
    
    public static final String NS_TTL           = "NS_CACHE_TTL";
    public static final String NS_CACHE_MAXSIZE = "NS_CACHE_MAXSIZE";
    public static final String OPENSHIFT_API_MANAGER = "api.service.openshift.io/api-manager";

    private static LoadingCache<String, ApimanInfo> nsCache = null;

    private static String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");
    private static boolean isWatching = false;
    
    /**
     * Constructor.
     */
    public Kubernetes2ApimanFilter() {
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        // maximum 10000 tokens in the cache
        Number nsCacheMaxsize = Systems.getEnvVarOrSystemProperty(NS_CACHE_MAXSIZE, 10000);
        // cache for 60  min
        Number nsTTL = Systems.getEnvVarOrSystemProperty(NS_TTL, 10);
        if (nsCache==null) {
        nsCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)    // allowed concurrency among update operations 
                .maximumSize(nsCacheMaxsize.longValue())                  
                .expireAfterWrite(nsTTL.longValue(), TimeUnit.MINUTES)    
                .build(
                        new CacheLoader<String, ApimanInfo>() {
                            public ApimanInfo load(String authToken) throws Exception {
                                return syncKubernetesToApiman(authToken);
                            }
                        });
        }
        if (! isWatching) {
            watchNamespaces();
        }
        
    }
    
    /**
     * This filter syncs up the namespaces owned by the user to Apiman.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
    ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.toUpperCase().startsWith("BEARER")) {
            try {
                //check namespaceCache
                ApimanInfo apimanInfo = nsCache.get(authHeader.substring(7));
                if (! apimanInfo.isReady) {
                    nsCache.invalidate(apimanInfo.token);
                }
            } catch (Exception e) {
                String errMsg = e.getMessage();
                log.error(errMsg, e);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }

    /**
     * Given a token is 
     * @param token
     * @return
     */
    public ApimanInfo syncKubernetesToApiman(final String authToken){
        log.info("KubernetesToApiman");
        SudoSecurityContext sudoSecurityContext = new SudoSecurityContext();
        ApimanInfo apimanInfo = new ApimanInfo();

        OpenShiftClient osClient = null;
        try {
            Config config = new ConfigBuilder().withOauthToken(authToken).build();
            if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
            osClient = new DefaultOpenShiftClient(config);
            String username = osClient.inAnyNamespace().users().withName("~").get().getMetadata().getName();
            apimanInfo.token = authToken;

            List<OrganizationSummaryBean> beans = userResource.getOrganizations(username);
            Set<String> apimanOrganizationIdsForUser = new HashSet<String>();
            for (OrganizationSummaryBean bean: beans) {
                apimanOrganizationIdsForUser.add(bean.getId());
            }
            log.info(apimanOrganizationIdsForUser.toString());
            //get k8s projects owned by user
            ProjectList projectList = osClient.projects().list();
            
            for (Project project : projectList.getItems()) {
                String orgId = BeanUtils.idFromName(project.getMetadata().getName());
                log.info("Namespace: " + orgId);
                if (! apimanOrganizationIdsForUser.contains(orgId)) {
                    log.info("User " + username + " is not a member of organizationId '" + orgId + "'"); 
                    try {
                        organizationResource.get(orgId);
                        log.info("Adding user '" + username + "' as member to organizationId '" + orgId + "'");
                        GrantRolesBean bean = new GrantRolesBean();
                        bean.setUserId(username);
                        Set<String> roleIds = new HashSet<String>();
                        roleIds.add("Organization Owner");
                        bean.setRoleIds(roleIds);
                        sudoSecurityContext.sudo(organizationResource, "Kubernetes2Apiman", true);
                        organizationResource.grant(orgId, bean);
                        sudoSecurityContext.exit();
                    } catch (OrganizationNotFoundException e) {
                        log.info("Creating organizationId '" + orgId + "' as it does not yet exist in Apiman");
                        NewOrganizationBean orgBean = new NewOrganizationBean();
                        orgBean.setName(project.getMetadata().getName());
                        orgBean.setDescription("Namespace '" + orgId + "' created by Kubernetes2Apiman");
                        sudoSecurityContext.sudo(organizationResource, username, false);
                        organizationResource.create(orgBean);
                        sudoSecurityContext.exit();
                    }
                    apimanInfo.organizations.add(orgId);
                }

                List<ApiSummaryBean> apiSummaryBeans = organizationResource.listApi(orgId);
                Set<String> apimanApiIds = new HashSet<String>();
                for (ApiSummaryBean bean : apiSummaryBeans) {
                    apimanApiIds.add(bean.getId());
                }
                ServiceList serviceList = osClient.services().inNamespace(orgId).list();
                sudoSecurityContext.sudo(organizationResource, username, false);
                Kubernetes2ApimanMapper mapper = new Kubernetes2ApimanMapper(osClient);
                for (Service service : serviceList.getItems()) {
                    if (! apimanApiIds.contains(BeanUtils.idFromName(service.getMetadata().getName()))) {
                        if (isServiceRegisterToApiman(service)) {
                            log.info("Creating API '" + service.getMetadata().getName() + "' in apiman");
                            //map service to bean
                            AvailableApiBean bean = mapper.createAvailableApiBean(service, null);
                            if (bean!=null) {
                                if (! isReady(bean)) {
                                    apimanInfo.isReady = false;
                                    break;
                                }     
                                NewApiBean newApiBean = new NewApiBean();
                                newApiBean.setDefinitionType(bean.getDefinitionType());
                                newApiBean.setDefinitionUrl(bean.getDefinitionUrl());
                                newApiBean.setDescription(bean.getDescription());
                                newApiBean.setEndpoint(bean.getEndpoint());
                                newApiBean.setEndpointType(bean.getEndpointType());
                                newApiBean.setInitialVersion("1.0");
                                newApiBean.setName(bean.getName());
                                newApiBean.setPublicAPI(true);
                                log.info("New API: " + newApiBean);
                                organizationResource.createApi(orgId, newApiBean);
                                apimanInfo.apis.add(BeanUtils.idFromName(service.getMetadata().getName()));
                            }
                        } else {
                            log.debug("Auto registration not requested for this service");
                        }
                    }
                }
                sudoSecurityContext.exit();
            }
        } catch(Exception e){
            log.error("Kubernetes2Apiman mapping Exception. ", e);
        }finally {
            sudoSecurityContext.exit();
            if (osClient!=null) osClient.close();
            
        }
        return apimanInfo;
    }

    protected class ApimanInfo {
        String token;
        boolean isReady = true;
        Set<String> organizations = new HashSet<String>();
        Set<String> apis = new HashSet<String>();
    }
    
    /**
     * Checks of the descriptionDocument can be obtained from the service. If the
     * service is still deploying we will hold off and return false.
     * 
     * @param bean
     * @return true of the desciptionDocument is ready to be read.
     */
    private boolean isReady(AvailableApiBean bean) {
        log.debug("DefinitionType: " + bean.getDefinitionType());
        if (bean.getDefinitionType()!=null && ! "".equals(bean.getDefinitionType())) {
            try {
                URL defUrl = new URL(bean.getDefinitionUrl());
                URLConnection urlConnection =  defUrl.openConnection();
                log.info("Trying to obtain descriptionDoc for service " + bean.getName());
                urlConnection.setConnectTimeout(250);
                if (urlConnection.getContentLength() > 0) {
                    log.debug("DefinitionDoc Ready to be read " + urlConnection.getContent());
                    return true;
                } else {
                    log.info("DefinitionDoc for '" + bean.getName() + "' not ready to be read " + urlConnection.getContent());
                    return false;
                }
            } catch (Exception e) {
                log.info("DefinitionDoc for '" + bean.getName() + "' not ready to be read. " + e.getMessage());
                return false;
            }
        }
        return true;
    }
    
    private boolean isServiceRegisterToApiman(Service service) {
        Map<String,String> annotations = service.getMetadata().getAnnotations();
        if (annotations!=null && annotations.containsKey(OPENSHIFT_API_MANAGER)) {
            return "apiman".equalsIgnoreCase(annotations.get(OPENSHIFT_API_MANAGER));
        }
        return false;
    }
    
    public synchronized static void watchNamespaces() {
        isWatching = true;
        Config config = new ConfigBuilder().withWatchReconnectLimit(1).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        log.info("Starting namespace watcher");
        KubernetesClient k8sClient = new DefaultKubernetesClient(config);
        try {
            k8sClient.namespaces().watch(new Watcher<Namespace>() {
                
                @Override
                public void onClose(KubernetesClientException cause) {
                    log.error(cause.getMessage(),cause);
                }
                
                @Override
                public void eventReceived(Action action, Namespace resource) {
                    log.info("Watcher received namespace " + action.name() + " action");
                    if (Action.ADDED.equals(action)) {
                        log.info("Invalidating nsCache");
                        nsCache.invalidateAll();
                    }
                }
            });
            k8sClient.services().watch(new Watcher<Service>() {
                
                @Override
                public void onClose(KubernetesClientException cause) {
                    log.error(cause.getMessage(),cause);
                }
                
                @Override
                public void eventReceived(Action action, Service resource) {
                    log.info("Watcher received service " + action.name() + " action");
                    if (Action.ADDED.equals(action)) {
                        log.info("Invalidating nsCache");
                        nsCache.invalidateAll();
                    }
                }
            });
            
        } finally {
            if (k8sClient!=null) k8sClient.close();
        }
    }

}
