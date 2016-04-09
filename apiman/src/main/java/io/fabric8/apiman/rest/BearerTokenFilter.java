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

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.apiman.common.auth.AuthPrincipal;
import io.fabric8.apiman.AuthToken;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReview;
import io.fabric8.openshift.api.model.SubjectAccessReviewBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.api.model.User;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Systems;

/**
 * A simple implementation of an bearer token filter that checks the validity of
 * an incoming bearer token with the OpenShift issuer. The OpenShift call
 * returns a JSON from which the UserPrincipal can be set.
 */
public class BearerTokenFilter implements Filter {

    public static final String KUBERNETES_OSAPI_URL = "/oapi/"
            + KubernetesHelper.defaultOsApiVersion;

    public static final String BEARER_TOKEN_TTL           = "BEARER_TOKEN_CACHE_TTL";
    public static final String BEARER_TOKEN_CACHE_MAXSIZE = "BEARER_TOKEN_CACHE_MAXSIZE";

    private static LoadingCache<String, UserInfo> bearerTokenCache = null;

    final private static Log log = LogFactory.getLog(BearerTokenFilter.class);

    /**
     * Constructor.
     */
    public BearerTokenFilter() {
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        // maximum 10000 tokens in the cache
        Number bearerTokenCacheMaxsize = Systems.getEnvVarOrSystemProperty(BEARER_TOKEN_CACHE_MAXSIZE, 10000);
        // cache for 10  min
        Number bearerTokenTTL = Systems.getEnvVarOrSystemProperty(BEARER_TOKEN_TTL, 10);

        bearerTokenCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)                                               // allowed concurrency among update operations 
                .maximumSize(bearerTokenCacheMaxsize.longValue())                  
                .expireAfterWrite(bearerTokenTTL.longValue(), TimeUnit.MINUTES)    
                .build(
                        new CacheLoader<String, UserInfo>() {
                            public UserInfo load(String authToken) throws Exception {
                                return getUserInfoFromK8s(authToken);
                            }
                        });
    }
    
    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
    ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String authHeader = req.getHeader("Authorization");
        AuthToken.set(null);
        
        if (authHeader != null && authHeader.toUpperCase().startsWith("BEARER")) {
            //validate token with issuer
            try {
                String authToken = authHeader.substring(7);
                UserInfo userInfo = bearerTokenCache.get(authToken);
                AuthToken.set(authToken);
                AuthPrincipal principal = new AuthPrincipal(userInfo.username);
                principal.addRole("apiuser");
                if (userInfo.isClusterAdmin) {
                    principal.addRole("apiadmin");
                }
                request = wrapTheRequest(request, principal);
                chain.doFilter(request, response);
            } catch (Exception e) {
                e.printStackTrace();
                String errMsg = e.getMessage();
                if (e.getMessage().contains("Server returned HTTP response code")) {
                    errMsg = "Invalid BearerToken";
                } else {
                    errMsg = "Cannot validate BearerToken";
                    log.error(errMsg, e);
                }
                sendInvalidTokenResponse((HttpServletResponse)response, errMsg);
            }
        } else if (("/".equals(req.getPathInfo())) || ("/swagger.json".equals(req.getPathInfo()))
                || ("/swagger.yaml".equals(req.getPathInfo())) || (req.getPathInfo().startsWith("/downloads/"))) {
            //allow anonymous requests to the root or swagger document
            log.debug("Allowing anonymous access to " + req.getPathInfo());
            chain.doFilter(request, response);
        } else {
            //no bearer token present
            sendInvalidTokenResponse((HttpServletResponse)response, "No BearerToken");
        }
    }
    /**
     * Wrap the request to provide the principal.
     * 
     * @param request
     *            the request
     * @param principal
     *            the principal
     */
    private HttpServletRequest wrapTheRequest(final ServletRequest request,
            final AuthPrincipal principal) {
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(
                (HttpServletRequest) request) {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return principal.getRoles().contains(role);
            }

            @Override
            public String getRemoteUser() {
                return principal.getName();
            }
        };
        return wrapper;
    }

    /**
     * Sends a response that tells the client that authentication is required.
     * 
     * @param response
     *            the response
     * @throws IOException
     *             when an error cannot be sent
     */
    private void sendInvalidTokenResponse(HttpServletResponse response, String errMsg)
            throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, errMsg);
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
    public UserInfo getUserInfoFromK8s(final String token){
        if (log.isDebugEnabled()) log.debug("Calling k8s to validate header abd obtain username and role for token " + token);
        UserInfo userInfo = new UserInfo();
        ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
        OpenShiftClient osClient = null;
        try {
            osClient = new DefaultOpenShiftClient(builder.build());
            //get the userName of the current user
            User user = osClient.inAnyNamespace().users().withName("~").get();
            if (log.isDebugEnabled()) log.debug("user: " + user);
            //check to see if this user has the clusterAdmin role
            SubjectAccessReview request = new SubjectAccessReviewBuilder().withVerb("*").withResource("*")
                    .withApiVersion("v1").build();
            SubjectAccessReviewResponse response = osClient.subjectAccessReviews().create(request);
            if (log.isDebugEnabled()) log.debug("isAdminResponse: " + response);
            userInfo.isClusterAdmin = response.getAllowed();
            userInfo.username = user.getMetadata().getName();
        }catch(Exception e){
            log.error("Exception determining user's info. ", e);
        }finally {
            if (osClient!=null) osClient.close();
        }
        return userInfo;
    }

    protected class UserInfo {
        String username;
        boolean isClusterAdmin;
    }

}
