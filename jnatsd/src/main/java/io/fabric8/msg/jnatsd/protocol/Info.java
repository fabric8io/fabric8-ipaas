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

public class Info extends AbstractCommand<Info> {
    private String id;
    private String host;
    private int port;
    private String version;
    private boolean authRequired;
    private boolean sslRequired;
    private long maxPayload = 4 * 1024 * 1024;

    @Override
    public CommandType getType() {
        return CommandType.INFO;
    }

    public Info build(Buffer command, int start, int end) {

        Map<String, String> parameters = ProtocolHelper.parse(command, start, end);
        this.id = parameters.get("server_id");
        this.host = parameters.get("host");
        this.port = Integer.parseInt(parameters.get("port"));
        this.version = parameters.get("version");
        this.authRequired = Boolean.parseBoolean(parameters.get("auth_required"));
        this.sslRequired = Boolean.parseBoolean(parameters.get("ssl_required"));
        this.maxPayload = Long.parseLong(parameters.get("max_payload"));
        return this;
    }

    public String toString() {
        String result = String.format(
            "INFO {\"server_id\":\"%s\",\"version\":\"%s\","
                + "\"host\":\"%s\",\"port\":%d,\"auth_required\":%b,\"ssl_required\":%b,"
                + "\"max_payload\":%d}\r\n",
            this.id, this.version, this.host, this.port, this.authRequired,
            this.sslRequired, this.maxPayload);
        return result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    public boolean isSslRequired() {
        return sslRequired;
    }

    public void setSslRequired(boolean sslRequired) {
        this.sslRequired = sslRequired;
    }

    public long getMaxPayload() {
        return maxPayload;
    }

    public void setMaxPayload(long maxPayload) {
        this.maxPayload = maxPayload;
    }
}
