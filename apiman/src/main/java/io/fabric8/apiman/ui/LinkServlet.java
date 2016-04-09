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

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 * POST to the AuthTokenParseServlet with and 'access_token' and
 * 'redirect'. The access_token is set in the user's session and 
 * a redirect will send the browser to the appropriate page in the
 * apiman-console. 
 */
public class LinkServlet extends HttpServlet {

    private static final long serialVersionUID = 968846195079402775L;

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REDIRECT     = "redirect";
    
    public static final String AUTH_TOKEN   = "authToken";
    
    private static final Log log = LogFactory.getLog(LinkServlet.class);
    /**
     * Constructor.
     */
    public LinkServlet() {
    }

    /**
     * Expects JSON data input of the form:
     * {
     *  "access_token": "dXUkkQ7vX6Gv1-d9h1ZTqy_7OM0mAeHOYLV9odpA9r0",
     *  "redirect": "http://localhost:7070/apimanui/api-manager/"
     *  }
     *  It then sets the authToken into the user's session and send
     *  a 301 redirect to the supplied 'redirect' address within the apimanui. 
     *  The browser should follow this and the apimanui will load the
     *  f8-config.js file. This file is served up from the AuthTokenParseServlet
     *  that reads the token from user's session and sets it in the config.js
     *  return, and SSO is accomplished.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        log.info(req.getSession().getId());
        String authToken = (String) req.getParameter(ACCESS_TOKEN);
        String redirect  = (String) req.getParameter(REDIRECT);
        if (authToken==null) {
            StringBuffer jb = new StringBuffer();
            String line = null;
            BufferedReader reader = req.getReader();
            while ((line = reader.readLine()) != null)
              jb.append(line);
            String data = jb.toString();
            JSONObject json = new JSONObject(data);
            authToken = json.getString(ACCESS_TOKEN);
            redirect = json.getString("redirect");
        }
        log.info("301 redirect to " + redirect);
        //Set the authToken in the session
        req.getSession().setAttribute("authToken", authToken);
        //Reply with a redirect to the redirect URL
        resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        resp.setHeader("Location", redirect);
        resp.sendRedirect(redirect);
    }
}