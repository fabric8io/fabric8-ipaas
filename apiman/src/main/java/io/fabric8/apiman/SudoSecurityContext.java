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
package io.fabric8.apiman;



import io.apiman.manager.api.security.impl.AbstractSecurityContext;

public class SudoSecurityContext extends AbstractSecurityContext {

    String currentUser;
    boolean isAdmin;
    
    /**
     * Constructor.
     */
    public SudoSecurityContext(String currentUser, boolean isAdmin) {
        this.currentUser = currentUser;
        this.isAdmin = isAdmin;
    }

    /**
     * @see io.apiman.manager.api.security.ISecurityContext#getRequestHeader(java.lang.String)
     */
    @Override
    public String getRequestHeader(String headerName) {
        return null;
    }

    /**
     * @see io.apiman.manager.api.security.ISecurityContext#getCurrentUser()
     */
    @Override
    public String getCurrentUser() {
        return currentUser;
    }

    /**
     * @see io.apiman.manager.api.security.ISecurityContext#getEmail()
     */
    @Override
    public String getEmail() {
        return null;
    }

    /**
     * @see io.apiman.manager.api.security.ISecurityContext#getFullName()
     */
    @Override
    public String getFullName() {
        return null;
    }

    /**
     * @see io.apiman.manager.api.security.ISecurityContext#isAdmin()
     */
    @Override
    public boolean isAdmin() {
        return isAdmin;
    }

    /**
     * Called to clear the current thread local permissions bean.
     */
    protected static void clearPermissions() {
        AbstractSecurityContext.clearPermissions();
    }

    /**
     * Called to clear the context http servlet request.
     */
    protected static void clearServletRequest() {
    }

}
