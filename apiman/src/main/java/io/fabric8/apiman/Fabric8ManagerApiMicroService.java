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
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import io.apiman.common.servlet.ApimanCorsFilter;
import io.apiman.manager.api.micro.ManagerApiMicroService;
import io.fabric8.apiman.rest.BearerTokenFilter;
import io.fabric8.apiman.rest.BootstrapFilter;
import io.fabric8.apiman.rest.Kubernetes2ApimanFilter;
import io.fabric8.apiman.ui.LinkServlet;
import io.fabric8.apiman.ui.ConfigurationServlet;
import io.fabric8.apiman.ui.TranslationServlet;

public class Fabric8ManagerApiMicroService extends ManagerApiMicroService {

    final private static Log log = LogFactory.getLog(Fabric8ManagerApiMicroService.class);
    
    private Server sslServer;
    
    protected void startSsl() throws Exception {
        long startTime = System.currentTimeMillis();
        
        //Secret should be mounted at /secret
        File passwordFile = new File(ApimanStarter.KEYSTORE_PASSWORD_PATH);
        String password = IOUtils.toString(passwordFile.toURI());
        if (password!=null) password = password.trim();
        
        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());
        
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(ApimanStarter.KEYSTORE_PATH);
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
	
    @Override
    protected void addModulesToJetty(HandlerCollection handlers)
            throws Exception {
        super.addModulesToJetty(handlers);
        //override the apimanUiServer handler 
        ServletContextHandler apimanUiServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        
        addSecurityHandler(apimanUiServer);
        apimanUiServer.setContextPath("/apimanui");
        apimanUiServer.addFilter(ApimanCorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        
        //add the servlets before the static content
        LinkServlet parseServlet    = new LinkServlet();
        apimanUiServer.addServlet(new ServletHolder(parseServlet), "/link");
        ConfigurationServlet configServlet    = new ConfigurationServlet();
        apimanUiServer.addServlet(new ServletHolder(configServlet), "/apiman/config.js");
        TranslationServlet translationServlet = new TranslationServlet();
        apimanUiServer.addServlet(new ServletHolder(translationServlet), "/apiman/translations.js");

        //figuring out from where to load the static content in the apimanui war
        String indexFile = this.getClass().getClassLoader().getResource("apimanui/index.html").toExternalForm();
        String webDir = indexFile.substring(0, indexFile.length() - 10);
        apimanUiServer.setInitParameter("org.eclipse.jetty.servlet.Default.resourceBase", webDir);
        apimanUiServer.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
        DefaultServlet defaultServlet = new DefaultServlet();
        ServletHolder holder = new ServletHolder("default", defaultServlet);
        apimanUiServer.addServlet(holder, "/*");
        
        //rewriting some paths to angularjs index.html app
        RewriteHandler rewriter = new RewriteHandler();
        rewriter.setRewriteRequestURI(true);
        rewriter.setRewritePathInfo(false);
        rewriter.setOriginalPathAttribute("requestedPath");
        RewriteRegexRule rule1 = new RewriteRegexRule();
        rule1.setRegex("/apimanui/api-manager/.*");
        rule1.setReplacement("/apimanui/index.html");
        rewriter.addRule(rule1);
        RewriteRegexRule rule2 = new RewriteRegexRule();
        rule2.setRegex("/apimanui/api-manager/|/apimanui/|/apimanui");
        rule2.setReplacement("/apimanui/index.html");
        rewriter.addRule(rule2);
        
        rewriter.setHandler(apimanUiServer);
        
        Handler[] newHandlers = new Handler[] { handlers.getHandlers()[0], rewriter};
        handlers.setHandlers(newHandlers);
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
