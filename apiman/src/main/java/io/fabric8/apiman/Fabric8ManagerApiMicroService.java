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

import java.io.File;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import io.apiman.manager.api.micro.ManagerApiMicroService;

public class Fabric8ManagerApiMicroService extends ManagerApiMicroService {

    final private static Log log = LogFactory.getLog(Fabric8ManagerApiMicroService.class);
    
    public final static String KEYSTORE_PATH="/secret/keystore";
    public final static String KEYSTORE_PASSWORD_PATH="/secret/keystore-password";
    private Server sslServer;
    
    protected void startSsl() throws Exception {
        long startTime = System.currentTimeMillis();
        
        //Secret should be mounted at /secret
        File passwordFile = new File(KEYSTORE_PASSWORD_PATH);
        String password = IOUtils.toString(passwordFile.toURI());
        if (password!=null) password = password.trim();
        
        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());
        
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(KEYSTORE_PATH);
        sslContextFactory.setKeyStorePassword(password);
        sslContextFactory.setKeyManagerPassword(password);
        
        // Create the server.
        int serverPort = serverPort();
        log.info("**** Starting SslServer (" + getClass().getSimpleName() + ") on port: " + serverPort);
        sslServer = new Server();
        
        ServerConnector sslConnector = new ServerConnector(sslServer,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https));
        sslConnector.setPort(serverPort);
        sslServer.setConnectors(new Connector[] { sslConnector});
        
        HandlerCollection handlers = new HandlerCollection();
        addModulesToJetty(handlers);

        sslServer.setHandler(handlers);
        sslServer.start();
        long endTime = System.currentTimeMillis();
        log.info("******* Started in " + (endTime - startTime) + "ms");
    }
    /**
     * @see io.apiman.manager.api.micro.ManagerApiMicroService#getConfigResource(java.lang.String)
     */
    @Override
    protected Resource getConfigResource(String path) {
        return super.getConfigResource("/apimanui/apiman/f8-config.js");
    }
    
    @Override
    protected Resource getTranslationsResource(String path) {
        return super.getTranslationsResource("/apimanui/apiman/f8-translations.js");
    }

	@Override
	protected void addAuthFilter(ServletContextHandler apiManServer) {
		apiManServer.addFilter(BootstrapFilter.class,  "/*", EnumSet.of(DispatcherType.REQUEST));
		apiManServer.addFilter(BearerTokenFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
		apiManServer.addFilter(Kubernetes2ApimanFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
	}

	@Override
	protected SecurityHandler createSecurityHandler() {
	    // Security should be handled through the BearerTokenFilter.  No need for a valid
	    // login service.
        return null;
	}
	
    /**
     * Stop the server.
     * @throws Exception when any exception occurs
     */
    public void stopSsl() throws Exception {
        sslServer.stop();
    }
	
    public void joinSsl() throws InterruptedException {
        sslServer.join();
    }


}
