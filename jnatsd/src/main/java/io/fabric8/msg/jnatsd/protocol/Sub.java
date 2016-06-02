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

import io.fabric8.msg.jnatsd.routing.Address;
import io.vertx.core.buffer.Buffer;

public class Sub extends Command<Sub> {

    private Address subject;
    private String queueGroup;
    private Buffer sid;

    @Override
    public CommandType getType() {
        return CommandType.SUB;
    }

    public Sub build(Buffer buffer, int start, int end) {
        String[] splits = buffer.getString(start, end).split("\\s+");
        subject = new Address(splits[1]);
        if (splits.length >= 4) {
            queueGroup = splits[2];
            sid = Buffer.buffer(splits[3]);
        } else {
            sid = Buffer.buffer(splits[2]);
        }
        return this;
    }

    public Buffer getSid() {
        return sid;
    }

    public void setSid(Buffer sid) {
        this.sid = sid;
    }

    public Address getSubject() {
        return subject;
    }

    public void setSubject(Address subject) {
        this.subject = subject;
    }

    public String getQueueGroup() {
        return queueGroup;
    }

    public void setQueueGroup(String queueGroup) {
        this.queueGroup = queueGroup;
    }

    public String toString() {
        String result = "SUB " + subject;
        if (queueGroup != null) {
            result += " " + queueGroup;
        }
        result += " " + sid;
        return result;
    }
}
