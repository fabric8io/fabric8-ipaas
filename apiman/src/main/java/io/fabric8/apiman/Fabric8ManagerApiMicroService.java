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

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;

import io.apiman.manager.api.micro.ManagerApiMicroService;

public class Fabric8ManagerApiMicroService extends ManagerApiMicroService {

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


}
