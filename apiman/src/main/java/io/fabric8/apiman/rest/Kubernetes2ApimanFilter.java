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
package io.fabric8.apiman.rest;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.apiman.manager.api.beans.BeanUtils;
import io.apiman.manager.api.beans.actions.ActionBean;
import io.apiman.manager.api.beans.actions.ActionType;
import io.apiman.manager.api.beans.apis.ApiPlanBean;
import io.apiman.manager.api.beans.apis.ApiStatus;
import io.apiman.manager.api.beans.apis.NewApiBean;
import io.apiman.manager.api.beans.idm.GrantRolesBean;
import io.apiman.manager.api.beans.orgs.NewOrganizationBean;
import io.apiman.manager.api.beans.summary.ApiSummaryBean;
import io.apiman.manager.api.beans.summary.ApiVersionSummaryBean;
import io.apiman.manager.api.beans.summary.AvailableApiBean;
import io.apiman.manager.api.beans.summary.ClientSummaryBean;
import io.apiman.manager.api.beans.summary.OrganizationSummaryBean;
import io.apiman.manager.api.beans.summary.PolicySummaryBean;
import io.apiman.manager.api.rest.contract.IActionResource;
import io.apiman.manager.api.rest.contract.IOrganizationResource;
import io.apiman.manager.api.rest.contract.IUserResource;
import io.apiman.manager.api.rest.contract.exceptions.OrganizationNotFoundException;
import io.fabric8.apiman.ApimanStarter;
import io.fabric8.apiman.Kubernetes2ApimanMapper;
import io.fabric8.apiman.SudoSecurityContext;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.client.utils.Utils.getSystemPropertyOrEnvVar;

/**
 * This filter compares the services in the namespaces of the current user, to 
 * the data in Apiman. If a service is annotated with the apiman.io/publish annotation
 * it will be imported into the Apiman. 
 *
 */
@SuppressWarnings("nls")
@ApplicationScoped
public class Kubernetes2ApimanFilter implements Filter {

    @Inject
    IUserResource userResource;

    @Inject
    IOrganizationResource organizationResource;

    @Inject
    IActionResource actionResource;

    final private static Log log = LogFactory.getLog(Kubernetes2ApimanFilter.class);
    
    public static final String NS_TTL                 = "NS_CACHE_TTL";
    public static final String NS_CACHE_MAXSIZE       = "NS_CACHE_MAXSIZE";
    public static final String APIMAN_PLANS           = "apiman.io/plans";
    public static final String APIMAN_PUBLISH         = "apiman.io/publish";
    
    public static final String APIMAN_PUBLISH_PUBLISH = "publish";
    public static final String APIMAN_PUBLISH_IMPORT  = "import";

    private static LoadingCache<String, ApimanInfo> nsCache = null;

    private static String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");
    private static boolean isWatching = false;
    private static boolean isDeleteNamespaces = true;
    private static Set<String> deletedNamespaces = new HashSet<String>();

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
        boolean isTestMode = getSystemPropertyOrEnvVar(ApimanStarter.APIMAN_TESTMODE, false);
        if (! isTestMode && ! isWatching) {
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
                //delete namespaces if needed (true at first)
                if (isDeleteNamespaces) {
                    isDeleteNamespaces=false;
                    SudoSecurityContext sudoSecurityContext = new SudoSecurityContext();
                    for (Iterator<String> iter = deletedNamespaces.iterator(); iter.hasNext();) {
                        String orgId = BeanUtils.idFromName(iter.next());
                        deleteOrganization(orgId, sudoSecurityContext, "Kubernetes2Apiman");
                        sudoSecurityContext.exit();
                        iter.remove();
                    }
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
     * @param authToken
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
            String username = osClient.users().withName("~").get().getMetadata().getName();
            apimanInfo.token = authToken;
            
            //get k8s projects owned by user
            Set<String> namespaceIds = new HashSet<String>();
            ProjectList projectList = osClient.projects().list();
            for (Project project : projectList.getItems()) {
                String orgId = BeanUtils.idFromName(project.getMetadata().getName());
                namespaceIds.add(orgId);
            }

            List<OrganizationSummaryBean> orgBeans = userResource.getOrganizations(username);
            Set<String> apimanOrganizationIdsForUser = new HashSet<String>();

            //if apiman holds a namespace that was deleted in openshift then delete it
            for (OrganizationSummaryBean org: orgBeans) {
                if (! namespaceIds.contains(org.getId())) {
                    //delete the organization in apiman
                    deleteOrganization(org.getId(), sudoSecurityContext, username);
                } else {
                    apimanOrganizationIdsForUser.add(org.getId());
                }
            }
            log.info(apimanOrganizationIdsForUser.toString());

            //add namespaces to apiman if not there already
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
                //Get servicesIn Kubernetes Namespace
                Set<String> serviceIds = new HashSet<String>();
                ServiceList serviceList = osClient.services().inNamespace(orgId).list();
                for (Service service : serviceList.getItems()) {
                    String serviceId = BeanUtils.idFromName(service.getMetadata().getName());
                    serviceIds.add(serviceId);
                }
                //APIs in organization
                List<ApiSummaryBean> apiSummaryBeans = organizationResource.listApi(orgId);
                Set<String> apimanApiIds = new HashSet<String>();
                for (ApiSummaryBean bean : apiSummaryBeans) {
                    //retire and delete from apiman if no longer in openshift
                    if (! serviceIds.contains(bean.getId())) {
                        sudoSecurityContext.sudo(actionResource, username, true);
                        retireApi(orgId, bean.getId());
                        sudoSecurityContext.exit();
                        sudoSecurityContext.sudo(organizationResource, username, true);
                        deleteApi(orgId, bean.getId(), sudoSecurityContext);
                        sudoSecurityContext.exit();
                    } else {
                        apimanApiIds.add(bean.getId());
                    }
                }

                sudoSecurityContext.sudo(organizationResource, username, false);
                sudoSecurityContext.sudo(actionResource, username, false);
                Kubernetes2ApimanMapper mapper = new Kubernetes2ApimanMapper(osClient);
                for (Service service : serviceList.getItems()) {
                    if (! apimanApiIds.contains(BeanUtils.idFromName(service.getMetadata().getName()))) {
                        String action = getApimanPublishAnnotation(service);
                        if (action!=null) {
                            log.info("Creating API '" + service.getMetadata().getName() + "' in apiman");
                            //map service to bean
                            AvailableApiBean bean = mapper.createAvailableApiBean(service, null);
                            if (bean!=null) {
                                if (! isServiceReady(bean)) {
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

                                Set<ApiPlanBean> apiPlanBeans = getPlansForApiman(service);
                                if (apiPlanBeans == null) {
                                    newApiBean.setPublicAPI(true);
                                } else {
                                    newApiBean.setPlans(apiPlanBeans);
                                }
                                log.info("New API: " + newApiBean);
                                organizationResource.createApi(orgId, newApiBean);
                                String apiId = BeanUtils.idFromName(service.getMetadata().getName());
                                apimanInfo.apis.add(apiId);
                                
                                if (action.equalsIgnoreCase("publish")) {
                                    ActionBean publishApiAction = new ActionBean();
                                    publishApiAction.setOrganizationId(orgId);
                                    publishApiAction.setEntityId(apiId);
                                    publishApiAction.setEntityVersion("1.0");
                                    publishApiAction.setType(ActionType.publishAPI);
                                    log.info("Publish API: " + publishApiAction);
                                    actionResource.performAction(publishApiAction);
                                }
                            }
                        } else {
                            log.debug("Apiman import not requested for this service");
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
    private boolean isServiceReady(AvailableApiBean bean) {
        log.debug("DefinitionType: " + bean.getDefinitionType());
        URL defUrl = null;
        if (bean.getDefinitionType()!=null && ! "None".equals(bean.getDefinitionType().name())) {
            try {
                defUrl = new URL(bean.getDefinitionUrl());
                URLConnection urlConnection =  defUrl.openConnection();
                log.info("Trying to obtain descriptionDoc for service " + bean.getName());
                urlConnection.setConnectTimeout(250);
                if (urlConnection.getContentLength() > 0) {
                    log.debug("DefinitionDoc at 'Ready to be read " + urlConnection.getContent());
                    return true;
                } else {
                    //try the route
                    defUrl = new URL(bean.getRouteDefinitionUrl());
                    urlConnection =  defUrl.openConnection();
                    log.info("Trying to obtain descriptionDoc for service " + bean.getName());
                    urlConnection.setConnectTimeout(250);
                    if (urlConnection.getContentLength() > 0) {
                        bean.setDefinitionUrl(defUrl.toExternalForm());
                        return true;
                    } else {
                        log.info("DefinitionDoc for '" + bean.getName() + "' not ready to be read from " +  defUrl.toExternalForm());
                    }
                    return false;
                }
            } catch (Exception e) {
                log.info("DefinitionDoc for '" + bean.getName() + "' can't be read. " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private String getApimanPublishAnnotation(Service service) {
        Map<String,String> annotations = service.getMetadata().getAnnotations();
        if (annotations!=null && annotations.containsKey(APIMAN_PUBLISH)) {
            return annotations.get(APIMAN_PUBLISH);
        }
        return null;
    }

    private Set<ApiPlanBean> getPlansForApiman(Service service) {
        Map<String,String> annotations = service.getMetadata().getAnnotations();
        Set<ApiPlanBean> apiPlanBeans = null;
        if (annotations!=null && annotations.containsKey(APIMAN_PLANS)) {
            apiPlanBeans = new HashSet<ApiPlanBean>();
            String[] plans = annotations.get(APIMAN_PLANS).split(",");
            for (String plan : plans) {
                String version = "1.0";
                if (plan.contains(":")) {
                    version = plan.split(":")[1];
                    plan = plan.split(":")[0];
                }
                ApiPlanBean apiPlanBean = new ApiPlanBean();
                apiPlanBean.setPlanId(plan);
                apiPlanBean.setVersion(version);
                apiPlanBeans.add(apiPlanBean);
            }
        }
        return apiPlanBeans;
    }

    public synchronized static void watchNamespaces() {
        isWatching = true;
        Config config = new ConfigBuilder().withWatchReconnectLimit(1).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        log.info("Starting service and namespace watcher");
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
                    } else if (Action.DELETED.equals(action)) {
                        //mark namespace for deletion in apiman
                        isDeleteNamespaces = true;
                        deletedNamespaces.add(resource.getMetadata().getName());
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
                    if (Action.ADDED.equals(action) || Action.DELETED.equals(action)) {
                        log.info("Invalidating nsCache");
                        nsCache.invalidateAll();
                    }
                }
            });

        } finally {
            if (k8sClient!=null) k8sClient.close();
        }
    }

    private void retireApi(String organizationId, String apiId) {
        List<ApiVersionSummaryBean> versionBeans = organizationResource.listApiVersions(organizationId, apiId);
        for (ApiVersionSummaryBean versionBean : versionBeans) {
            if (ApiStatus.Published.equals(versionBean.getStatus())) {
                ActionBean retireApiAction = new ActionBean();
                retireApiAction.setOrganizationId(organizationId);
                retireApiAction.setEntityId(apiId);
                retireApiAction.setEntityVersion(versionBean.getVersion());
                retireApiAction.setType(ActionType.retireAPI);
                actionResource.performAction(retireApiAction);
            }
        }
    }

    private void unregisterClient(String organizationId, String id) {
        ActionBean unregisterClientAction = new ActionBean();
        unregisterClientAction.setOrganizationId(organizationId);
        unregisterClientAction.setEntityId(id);
        unregisterClientAction.setType(ActionType.unregisterClient);
        actionResource.performAction(unregisterClientAction);
    }
    
    private void deleteApi(String orgId, String apiId, SudoSecurityContext sudoSecurityContext) {
        List<ApiVersionSummaryBean> versionBeans = organizationResource.listApiVersions(orgId, apiId);
        for (ApiVersionSummaryBean versionBean : versionBeans) {
            List<PolicySummaryBean> apiPolicies = organizationResource.listApiPolicies(orgId, apiId, versionBean.getVersion());
            for (PolicySummaryBean apiPolicy : apiPolicies) {
                organizationResource.deleteApiPolicy(orgId, apiId, versionBean.getVersion(), apiPolicy.getId());
            }
            //organizationResource.getApiVersionContracts(organizationId, apiId, version, page, pageSize)
        }
        organizationResource.deleteApi(orgId, apiId);
    }

    private void deleteOrganization(String organizationId, SudoSecurityContext sudoSecurityContext, String username) {
        List<ApiSummaryBean> apiSymmaryBeans = organizationResource.listApi(organizationId);
        sudoSecurityContext.sudo(organizationResource, username, true);
        sudoSecurityContext.sudo(actionResource, username, true);
        for (ApiSummaryBean apiSummaryBean : apiSymmaryBeans) {
            retireApi(organizationId, apiSummaryBean.getId());
        }
        List<ClientSummaryBean> clientSymmaryBeans = organizationResource.listClients(organizationId);
        for (ClientSummaryBean clientSymmaryBean : clientSymmaryBeans) {
            unregisterClient(organizationId, clientSymmaryBean.getId());
        }
        organizationResource.delete(organizationId);
        sudoSecurityContext.exit();
    }


}
