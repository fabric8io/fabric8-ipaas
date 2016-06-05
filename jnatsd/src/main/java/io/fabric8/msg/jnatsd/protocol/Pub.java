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
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.buffer.Buffer;

import java.util.Collection;

import static io.fabric8.msg.jnatsd.protocol.BufferWrapper.bufferWrapper;

public class Pub extends AbstractCommand<Pub> {
    private Address subject;
    private BufferWrapper replyTo;
    private BufferWrapper noBytesBuffer;
    private BufferWrapper payload;
    private int payloadSize;
    private Collection<Subscription> matches;

    public Pub() {
    }

    @Override
    public CommandType getType() {
        return CommandType.PUB;
    }

    public Address getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = new Address(subject);
    }

    public void setSubject(Address subject) {
        this.subject = subject;
    }

    public BufferWrapper getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = bufferWrapper(replyTo);
    }

    public void setReplyTo(BufferWrapper replyTo) {
        this.replyTo = replyTo;
    }

    public BufferWrapper getNoBytesBuffer() {
        return noBytesBuffer;
    }

    public void setNoBytesBuffer(BufferWrapper noBytesBuffer) {
        this.noBytesBuffer = noBytesBuffer;
    }

    public BufferWrapper getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = BufferWrapper.bufferWrapper(payload);
        payloadSize = this.payload.getEnd();
        noBytesBuffer = BufferWrapper.bufferWrapper("" + payloadSize);
    }

    public void setPayload(byte[] payload) {
        this.payload = bufferWrapper(payload);
        payloadSize = this.payload.getEnd();
        noBytesBuffer = bufferWrapper("" + payloadSize);
    }

    public void setPayload(Buffer payload) {
        BufferWrapper bufferWrapper = bufferWrapper(payload);
        payloadSize = bufferWrapper.getEnd();
        noBytesBuffer = bufferWrapper("" + payloadSize);
    }

    public void setPayload(BufferWrapper payload) {
        this.payload = payload;
    }

    public Pub build(Buffer buffer, int start, int end) {
        return this;
    }

    public Collection<Subscription> getMatches() {
        return matches;
    }

    public void setMatches(Collection<Subscription> matches) {
        this.matches = matches;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(int payloadSize) {
        this.payloadSize = payloadSize;
    }

    public String toString() {
        String result = "PUB " + subject;
        if (replyTo != null) {
            result += " " + replyTo;
        }
        result += "#" + noBytesBuffer;

        if (payload != null && payload.length() > 0) {
            result += " " + payload;
        }
        return result;
    }
}
