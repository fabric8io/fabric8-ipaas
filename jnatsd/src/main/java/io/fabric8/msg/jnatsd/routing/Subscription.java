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

package io.fabric8.msg.jnatsd.routing;

import io.fabric8.msg.jnatsd.JNatsClient;
import io.vertx.core.buffer.Buffer;

public class Subscription implements Comparable<Subscription> {
    private JNatsClient natsClient;
    private Address address;
    private String queueGroup;
    private Buffer sid;

    public Subscription() {
    }

    public Subscription(String str) {
        this.address = new Address(str);
    }

    public Buffer getSid() {
        return sid;
    }

    public void setSid(Buffer sid) {
        this.sid = sid;
    }

    public String getQueueGroup() {
        return queueGroup;
    }

    public boolean isQueueGroup() {
        return queueGroup != null && !queueGroup.isEmpty();
    }

    public void setQueueGroup(String queueGroup) {
        this.queueGroup = queueGroup;
    }

    public JNatsClient getNatsClient() {
        return natsClient;
    }

    public void setNatsClient(JNatsClient natsClient) {
        this.natsClient = natsClient;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public int compareTo(Subscription anotherSubscription) {
        int result = address.compareTo(anotherSubscription.address);
        if (result == 0) {
            result = sid.getByteBuf().compareTo(anotherSubscription.sid.getByteBuf());
        }
        return result;
    }

    @Override
    public String toString() {
        return "Subscription(" + address + ") " + sid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subscription that = (Subscription) o;

        if (natsClient != null ? !natsClient.equals(that.natsClient) : that.natsClient != null) return false;
        if (!address.equals(that.address)) return false;
        if (queueGroup != null ? !queueGroup.equals(that.queueGroup) : that.queueGroup != null) return false;
        return sid.equals(that.sid);

    }

    @Override
    public int hashCode() {
        int result = natsClient.hashCode();
        result = 31 * result + address.hashCode();
        result = 31 * result + (queueGroup != null ? queueGroup.hashCode() : 0);
        result = 31 * result + sid.hashCode();
        return result;
    }
}
