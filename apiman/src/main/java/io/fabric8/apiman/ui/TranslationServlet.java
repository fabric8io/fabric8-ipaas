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

/**
 * Generates the initial configuration JSON used by the UI when it first loads
 * up. This initial JSON is loaded into the client-side.
 *
 * Also responsible for pushing updated configuration to the client if it
 * changes.
 *
 */
public class TranslationServlet extends HttpServlet {

    private static final long serialVersionUID = -7269147670393871979L;

    /**
     * Constructor.
     */
    public TranslationServlet() {
    }

    /**
     *  @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse)
     *      
     * Returns the UI strings specific to fabric8, stored in f8-translations.js.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
            IOException {
        InputStream is = getClass().getResourceAsStream("/apimanui/apiman/f8-translations.js");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/javascript");
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }
}