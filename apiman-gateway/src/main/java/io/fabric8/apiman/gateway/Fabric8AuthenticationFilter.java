package io.fabric8.apiman.gateway;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.apiman.common.servlet.AuthenticationFilter;

public class Fabric8AuthenticationFilter extends AuthenticationFilter {

    final private static Log log = LogFactory.getLog(Fabric8AuthenticationFilter.class);
    private String realm;
    
    @Override
    public void init(FilterConfig config) throws ServletException {
        // Realm
        String parameter = config.getInitParameter("realm"); //$NON-NLS-1$
        if (parameter != null && parameter.trim().length() > 0) {
            realm = parameter;
        } else {
            realm = defaultRealm();
        }
        super.init(config);
    }
        
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
    ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        log.info(req.getPathInfo());
        if ("/system/status".equals(req.getPathInfo()) || "/swagger.json".equals(req.getPathInfo())) {
            log.debug("Allowing anonymous access to " + req.getPathInfo());
            chain.doFilter(request, response);
        } else {
            String authHeader = req.getHeader("Authorization"); //$NON-NLS-1$
            if (authHeader!=null && authHeader.toUpperCase().startsWith("BASIC")) { //$NON-NLS-1$
                Creds credentials = parseAuthorizationBasic(authHeader);
                if  (credentials == null) {
                    sendAuthResponse((HttpServletResponse) response);
                } else {
                    doBasicAuth(credentials, req, (HttpServletResponse) response, chain);
                }
            } else {
                sendAuthResponse((HttpServletResponse) response);
            }
        }
    }
    
    /**
     * Sends a response that tells the client that authentication is required.
     * @param response the response
     * @throws IOException when an error cannot be sent
     */
    private void sendAuthResponse(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", String.format("Basic realm=\"%1$s\"", realm)); //$NON-NLS-1$ //$NON-NLS-2$
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    
    /**
     * Parses the Authorization request header into a username and password.
     * @param authHeader the auth header
     */
    private Creds parseAuthorizationBasic(String authHeader) {
        String userpassEncoded = authHeader.substring(6);
        String data = StringUtils.newStringUtf8(Base64.decodeBase64(userpassEncoded));
        int sepIdx = data.indexOf(':');
        if (sepIdx > 0) {
            String username = data.substring(0, sepIdx);
            String password = data.substring(sepIdx + 1);
            return new Creds(username, password);
        } else {
            return new Creds(data, null);
        }
    }
}
