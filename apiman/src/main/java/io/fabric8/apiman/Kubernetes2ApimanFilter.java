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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
import io.apiman.manager.api.beans.audit.data.MembershipData;
import io.apiman.manager.api.beans.idm.GrantRolesBean;
import io.apiman.manager.api.beans.idm.PermissionType;
import io.apiman.manager.api.beans.idm.RoleBean;
import io.apiman.manager.api.beans.idm.RoleMembershipBean;
import io.apiman.manager.api.beans.orgs.NewOrganizationBean;
import io.apiman.manager.api.beans.orgs.OrganizationBean;
import io.apiman.manager.api.beans.search.SearchCriteriaBean;
import io.apiman.manager.api.beans.search.SearchCriteriaFilterOperator;
import io.apiman.manager.api.core.IStorage;
import io.apiman.manager.api.core.IStorageQuery;
import io.apiman.manager.api.core.exceptions.StorageException;
import io.apiman.manager.api.core.logging.ApimanLogger;
import io.apiman.manager.api.core.logging.IApimanLogger;
import io.apiman.manager.api.rest.contract.exceptions.AbstractRestException;
import io.apiman.manager.api.rest.contract.exceptions.InvalidNameException;
import io.apiman.manager.api.rest.contract.exceptions.NotAuthorizedException;
import io.apiman.manager.api.rest.contract.exceptions.OrganizationAlreadyExistsException;
import io.apiman.manager.api.rest.contract.exceptions.OrganizationNotFoundException;
import io.apiman.manager.api.rest.contract.exceptions.RoleNotFoundException;
import io.apiman.manager.api.rest.contract.exceptions.SystemErrorException;
import io.apiman.manager.api.rest.contract.exceptions.UserNotFoundException;
import io.apiman.manager.api.rest.impl.audit.AuditUtils;
import io.apiman.manager.api.rest.impl.i18n.Messages;
import io.apiman.manager.api.rest.impl.util.ExceptionFactory;
import io.apiman.manager.api.rest.impl.util.FieldValidator;
import io.fabric8.kubernetes.api.model.Namespace;
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
    IStorage storage;
    
    @Inject
    IStorageQuery storageQuery;
    
    @Inject @ApimanLogger(BootstrapFilter.class)
    IApimanLogger logger;
    
    public static final String NS_TTL           = "NS_CACHE_TTL";
    public static final String NS_CACHE_MAXSIZE = "NS_CACHE_MAXSIZE";

    private static LoadingCache<String, UserInfo> nsCache = null;

    private static String kubernetesMasterUrl = Systems.getEnvVarOrSystemProperty("KUBERNETES_MASTER");
    private static boolean isWatching = false;
    
    final private static Log log = LogFactory.getLog(Kubernetes2ApimanFilter.class);

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
        // cache for 10  min
        Number nsTTL = Systems.getEnvVarOrSystemProperty(NS_TTL, 10);
        if (nsCache==null) {
        nsCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)    // allowed concurrency among update operations 
                .maximumSize(nsCacheMaxsize.longValue())                  
                .expireAfterWrite(nsTTL.longValue(), TimeUnit.MINUTES)    
                .build(
                        new CacheLoader<String, UserInfo>() {
                            public UserInfo load(String authToken) throws Exception {
                                return syncKubernetesNamespacesToApiman(authToken);
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
                nsCache.get(authHeader.substring(7));
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
    public UserInfo syncKubernetesNamespacesToApiman(final String authToken){
        log.info("KubernetesNamespacesToApiman");
        UserInfo userInfo = new UserInfo();
        Config config = new ConfigBuilder().withOauthToken(authToken).build();
        if (kubernetesMasterUrl!=null) config.setMasterUrl(kubernetesMasterUrl);
        OpenShiftClient osClient = null;
        try {
            osClient = new DefaultOpenShiftClient(config);
            String username = osClient.inAnyNamespace().users().withName("~").get().getMetadata().getName();
            userInfo.token = authToken;
            Set<String> apimanOrganizationsForUser = getApimanOrganizations(username);
            log.info(apimanOrganizationsForUser);
            //get k8s projects owned by user
            ProjectList projectList = osClient.projects().list();
            for (Project project : projectList.getItems()) {
                String orgId = BeanUtils.idFromName(project.getMetadata().getName());
                log.info("Namespace: " + orgId);
                if (! apimanOrganizationsForUser.contains(orgId)) {
                    log.info("User " + username + " is not a member of organizationId '" + orgId + "'");
                    OrganizationBean apimanOrg = getApimanOrganization(orgId);
                    if (apimanOrg==null) { // create the organization
                        log.info("Creating organizationId '" + orgId + "' as it does not yet exist in Apiman");
                        NewOrganizationBean orgBean = new NewOrganizationBean();
                        orgBean.setName(project.getMetadata().getName());
                        orgBean.setDescription("Namespace '" + orgId + "' created by Kubernetes2Apiman");
                        createApimanOrg(orgBean, username);
                    } else {
                        log.info("Adding user '" + username + "' as member to organizationId '" + orgId + "'");
                        GrantRolesBean bean = new GrantRolesBean();
                        bean.setUserId(username);
                        Set<String> roleIds = new HashSet<String>();
                        roleIds.add("Service Developer");
                        bean.setRoleIds(roleIds);
                        grant(orgId, bean);
                    }
                }
            }

        } catch(Exception e){
            log.error("Exception determining namespace info. ", e);
        }finally {
            if (osClient!=null) osClient.close();
        }
        return userInfo;
    }

    protected class UserInfo {
        String token;
    }
    
    public Set<String> getApimanOrganizations(String userId) {
        Set<String> permittedOrganizations = new HashSet<>();
        try {
            Set<RoleMembershipBean> memberships = storageQuery.getUserMemberships(userId);
            for (RoleMembershipBean membership : memberships) {
                permittedOrganizations.add(membership.getOrganizationId());
            }
            return permittedOrganizations;
        } catch (StorageException e) {
            throw new SystemErrorException(e);
        }
    }
    
    public OrganizationBean getApimanOrganization(String organizationId) throws OrganizationNotFoundException, NotAuthorizedException {
        try {
            storage.beginTx();
            OrganizationBean organizationBean = storage.getOrganization(organizationId);
            storage.commitTx();
            if (organizationBean!=null) log.debug(String.format("Got organization %s: %s", organizationBean.getName(), organizationBean)); //$NON-NLS-1$
            return organizationBean;
        } catch (AbstractRestException e) {
            storage.rollbackTx();
            throw e;
        } catch (Exception e) {
            storage.rollbackTx();
            throw new SystemErrorException(e);
        }
    }
    
    public OrganizationBean createApimanOrg(NewOrganizationBean bean, String currentUser) throws OrganizationAlreadyExistsException, InvalidNameException {
        FieldValidator.validateName(bean.getName());

        SudoSecurityContext securityContext = new SudoSecurityContext(currentUser, true);
        List<RoleBean> autoGrantedRoles;
        SearchCriteriaBean criteria = new SearchCriteriaBean();
        criteria.setPage(1);
        criteria.setPageSize(100);
        criteria.addFilter("autoGrant", "true", SearchCriteriaFilterOperator.bool_eq); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            autoGrantedRoles = storageQuery.findRoles(criteria).getBeans();
        } catch (StorageException e) {
            throw new SystemErrorException(e);
        }

        if ("true".equals(System.getProperty("apiman.manager.require-auto-granted-org", "true"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (autoGrantedRoles.isEmpty()) {
                throw new SystemErrorException(Messages.i18n.format("OrganizationResourceImpl.NoAutoGrantRoleAvailable")); //$NON-NLS-1$
            }
        }

        OrganizationBean orgBean = new OrganizationBean();
        orgBean.setName(bean.getName());
        orgBean.setDescription(bean.getDescription());
        orgBean.setId(BeanUtils.idFromName(bean.getName()));
        orgBean.setCreatedOn(new Date());
        orgBean.setCreatedBy(currentUser);
        orgBean.setModifiedOn(new Date());
        orgBean.setModifiedBy(currentUser);
        try {
            // Store/persist the new organization
            storage.beginTx();
            if (storage.getOrganization(orgBean.getId()) != null) {
                throw ExceptionFactory.organizationAlreadyExistsException(bean.getName());
            }
            storage.createOrganization(orgBean);
            storage.createAuditEntry(AuditUtils.organizationCreated(orgBean, securityContext));

            // Auto-grant memberships in roles to the creator of the organization
            for (RoleBean roleBean : autoGrantedRoles) {
                //String currentUser = securityContext.getCurrentUser();
                String orgId = orgBean.getId();
                RoleMembershipBean membership = RoleMembershipBean.create(currentUser, roleBean.getId(), orgId);
                membership.setCreatedOn(new Date());
                storage.createMembership(membership);
            }
            storage.commitTx();
            log.debug(String.format("Created organization %s: %s", orgBean.getName(), orgBean)); //$NON-NLS-1$
            return orgBean;
        } catch (AbstractRestException e) {
            storage.rollbackTx();
            throw e;
        } catch (Exception e) {
            storage.rollbackTx();
            throw new SystemErrorException(e);
        }
    }
    
    public void grant(String organizationId, GrantRolesBean bean) throws OrganizationNotFoundException,
    RoleNotFoundException, UserNotFoundException, NotAuthorizedException {

        SudoSecurityContext securityContext = new SudoSecurityContext("kubernetes2apiman",true);
        if (!securityContext.hasPermission(PermissionType.orgAdmin, organizationId))
            throw ExceptionFactory.notAuthorizedException();

        MembershipData auditData = new MembershipData();
        auditData.setUserId(bean.getUserId());
        try {
            storage.beginTx();
            for (String roleId : bean.getRoleIds()) {
                RoleMembershipBean membership = RoleMembershipBean.create(bean.getUserId(), roleId, organizationId);
                membership.setCreatedOn(new Date());
                // If the membership already exists, that's fine!
                if (storage.getMembership(bean.getUserId(), roleId, organizationId) == null) {
                    storage.createMembership(membership);
                }
                auditData.addRole(roleId);
            }
            storage.createAuditEntry(AuditUtils.membershipGranted(organizationId, auditData, securityContext));
            storage.commitTx();
        } catch (AbstractRestException e) {
            storage.rollbackTx();
            throw e;
        } catch (Exception e) {
            storage.rollbackTx();
            throw new SystemErrorException(e);
        }
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
        } finally {
            if (k8sClient!=null) k8sClient.close();
        }
    }

}
