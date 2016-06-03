/*
 *
 *  * Copyright 2005-2016 Red Hat, Inc.
 *  * Red Hat licenses this file to you under the Apache License, version
 *  * 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  * implied.  See the License for the specific language governing
 *  * permissions and limitations under the License.
 *
 */

package io.fabric8.msg.jnatsd.protocol;

import io.vertx.core.buffer.Buffer;

import java.util.Map;

public class Connect extends Command<Connect> {

    private boolean verbose = true;
    private boolean pedantic;
    private boolean sslRequired;
    private String auth_token;
    private String user;
    private String pass;
    private String name;
    private String lang;
    private String version;

    @Override
    public CommandType getType() {
        return CommandType.CONNECT;
    }

    public Connect build(Buffer command, int start, int end) {

        Map<String, String> parameters = ProtocolHelper.parse(command, start, end);
        this.verbose = Boolean.parseBoolean(parameters.get("verbose"));
        this.pedantic = Boolean.parseBoolean(parameters.get("pedantic"));
        this.sslRequired = Boolean.parseBoolean(parameters.get("ssl_required"));
        this.user = parameters.get("user");
        this.pass = parameters.get("pass");
        this.name = parameters.get("name");
        this.lang = parameters.get("lang");
        this.version = parameters.get("version");
        return this;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isPedantic() {
        return pedantic;
    }

    public void setPedantic(boolean pedantic) {
        this.pedantic = pedantic;
    }

    public boolean isSslRequired() {
        return sslRequired;
    }

    public void setSslRequired(boolean sslRequired) {
        this.sslRequired = sslRequired;
    }

    public String getAuth_token() {
        return auth_token;
    }

    public void setAuth_token(String auth_token) {
        this.auth_token = auth_token;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void reset(Connect other) {
        this.verbose = other.verbose;
        this.pedantic = other.pedantic;
        this.sslRequired = other.sslRequired;
        this.user = other.user;
        this.pass = other.pass;
        this.name = other.name;
        this.lang = other.lang;
        this.version = other.version;
    }

    public String getProperties() {
        return String.format(
            "\"verbose\":\"%b\",\"pedantic\":\"%b\",\"sslRequired\":\"%b\","
                + "\"user\":\"%s\",\"pass\":%s,\"name\":sb,\"lang\":%s,"
                + "\"version\":%s",
            this.verbose, this.pedantic, this.sslRequired, this.user, this.pass, this.name,
            this.lang, this.version);
    }

    public String toString() {
        String result = "CONNECT {" + getProperties() + " }";
        return result;
    }
}
