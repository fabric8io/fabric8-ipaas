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

public class Msg extends AbstractCommand<Msg> {
    private Address subject;
    private Buffer sid;
    private BufferWrapper replyTo;
    private BufferWrapper noBytes;
    private BufferWrapper payload;

    @Override
    public CommandType getType() {
        return CommandType.MSG;
    }

    public Address getSubject() {
        return subject;
    }

    public void setSubject(Address subject) {
        this.subject = subject;
    }

    public Buffer getSid() {
        return sid;
    }

    public void setSid(Buffer sid) {
        this.sid = sid;
    }

    public BufferWrapper getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(BufferWrapper replyTo) {
        this.replyTo = replyTo;
    }

    public BufferWrapper getNoBytes() {
        return noBytes;
    }

    public void setNoBytes(BufferWrapper noBytes) {
        this.noBytes = noBytes;
    }

    public BufferWrapper getRawPayload() {
        return payload;
    }

    public String getPayloadAsString() {
        return payload != null ? payload.getAsString() : null;
    }

    public byte[] getPayloadAsBytes() {
        return payload != null ? payload.getAsBytes() : null;
    }

    public void setPayload(BufferWrapper payload) {
        this.payload = payload;
    }

    public Msg build(Buffer buffer, int start, int end) {
        return this;
    }

    public String toString() {
        String result = "MSG " + subject + " " + sid + " ";
        if (replyTo != null) {
            result += replyTo + " ";
        }
        result += noBytes + " " + payload;
        return result;
    }

    @Override
    public Buffer getBuffer() {
        //MSG + space + subject + space + replyTo + space + size + space + payload
        int sizeHint = 11 + subject.size() + (replyTo != null ? replyTo.length() : 0);
        if (payload != null) {
            sizeHint += payload.length();
        }
        Buffer buffer = Buffer.buffer(sizeHint);
        buffer.appendString("MSG ");
        subject.appendToBuffer(buffer);
        buffer.appendString(" ");
        buffer.appendBuffer(sid);
        buffer.appendString(" ");
        if (replyTo != null) {
            replyTo.appendTo(buffer);
            buffer.appendString(" ");
        }
        noBytes.appendTo(buffer);
        buffer.appendBuffer(CRLF);
        if (payload != null) {
            payload.appendTo(buffer);
        }
        buffer.appendBuffer(CRLF);
        return buffer;
    }

}
