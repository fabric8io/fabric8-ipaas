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

public class UnSub extends AbstractCommand<UnSub> {

    int maxMessages = -1;
    private Buffer sid;

    @Override
    public CommandType getType() {
        return CommandType.UNSUB;
    }

    public UnSub build(Buffer buffer, int start, int end) {
        String[] splits = buffer.getString(start, end).split("\\s+");
        sid = Buffer.buffer(splits[0]);
        if (splits.length >= 2) {
            maxMessages = Integer.parseInt(splits[1]);
        }
        return this;
    }

    public Buffer getSid() {
        return sid;
    }

    public void setSid(Buffer sid) {
        this.sid = sid;
    }

    public void setSid(String sid) {
        this.sid = Buffer.buffer(sid);
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public String toString() {
        String result = "UNSUB " + sid;
        if (maxMessages > 0) {
            result += " " + maxMessages;
        }
        return result;
    }
}
