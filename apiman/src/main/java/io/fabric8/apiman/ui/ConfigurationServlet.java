/*
 * Copyright 2014 JBoss Inc
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
package io.fabric8.apiman.ui;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generates the initial configuration JSON used by the UI when it first loads
 * up. This initial JSON is loaded into the client-side.
 *
 * Also responsible for pushing updated configuration to the client if it
 * changes.
 *
 */
public class ConfigurationServlet extends HttpServlet {

    private static final long serialVersionUID = 968846195079402775L;

    final private static Log log = LogFactory.getLog(ConfigurationServlet.class);
    /**
     * Constructor.
     */
    public ConfigurationServlet() {
    }

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse)
     * Grabs the authToken from the user's sessions and sticks it in the config.js using
     * a token replacement of the token '@token@'.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException 
    {
        String authToken = String.valueOf(request.getSession().getAttribute(LinkServlet.AUTH_TOKEN));
        log.error("No authToken in the user's session with id " + request.getSession().getId());
        InputStream is = getClass().getResourceAsStream("/apimanui/apiman/f8-config.js");
        String configFile = IOUtils.toString(is);
        configFile = configFile.replace("@token@", authToken);
        try {
            response.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
            response.setDateHeader("Expires", 0);
            response.getOutputStream().write(configFile.getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }
}