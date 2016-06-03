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

package io.fabric8.msg.jnatsd;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@PropertySource("classpath:application.properties")
@ConfigurationProperties(prefix = "jnatsd")
@Component
public class JNatsdConfiguration {

    @Max(64)
    @Min(0)
    private Integer numberOfNetServers;
    private Integer maxPayLoad;
    private Integer maxPendingSize;
    private Integer clientPort;
    private Integer pingInterval;
    private Boolean verbose;

    public int getNumberOfNetServers() {
        return numberOfNetServers;
    }

    public void setNumberOfNetServers(int numberOfNetServers) {
        this.numberOfNetServers = numberOfNetServers;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public Integer getMaxPayLoad() {
        return maxPayLoad;
    }

    public void setMaxPayLoad(Integer maxPayLoad) {
        this.maxPayLoad = maxPayLoad;
    }

    public int getMaxPendingSize() {
        return maxPendingSize;
    }

    public void setMaxPendingSize(int maxPendingSize) {
        this.maxPendingSize = maxPendingSize;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

}
