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
import io.fabric8.msg.jnatsd.routing.RoutingMap;
import io.fabric8.msg.jnatsd.routing.Subscription;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static io.fabric8.msg.jnatsd.protocol.AbstractCommand.CRLF;
import static io.fabric8.msg.jnatsd.protocol.ProtocolHelper.skipWhiteSpace;

public class CommandFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CommandFactory.class);
    private static final Buffer MSG_NAME = Buffer.buffer("MSG ");
    private static final Buffer SPACE = Buffer.buffer(" ");
    private static final byte NEWLINE = (byte) '\n';

    public static AbstractCommand processBuffer(RoutingMap routingMap, boolean pedantic, Buffer buffer, final int start, final int end) throws ProtocolException {

        int startPos = skipWhiteSpace(buffer, start, end);
        if (isPub(buffer, startPos, end)) {
            return getPub(routingMap, pedantic, buffer, startPos, end);
        }
        return getCommand(buffer, pedantic, startPos, end);

    }

    static AbstractCommand getPub(RoutingMap routingMap, boolean pedantic, Buffer buffer, final int start, final int end) {
        int pos = start + 3;
        BufferWrapper subject;
        BufferWrapper replyTo = null;
        BufferWrapper sizeBuffer;

        ///
        // We know a Pub message will look like this:
        //   PUB <subject> [reply-to] <#bytes>\r\n[payload]\r\n
        //
        int firstNewLine = -1;
        int secondNewLine = -1;
        for (; pos < end; pos++) {
            byte b = buffer.getByte(pos);
            if (b == NEWLINE) {
                if (firstNewLine == -1) {
                    firstNewLine = pos;
                } else if (secondNewLine == -1) {
                    secondNewLine = pos;
                    break;
                }
            }
        }

        if (secondNewLine >= 0) {

            int messageStart = start + 3;
            int messageEnd = secondNewLine;

            BufferTokenizer tokenizer = new BufferTokenizer(buffer, messageStart, firstNewLine);
            subject = tokenizer.nextToken();
            Address address = new Address(subject);
            Collection<Subscription> matches = routingMap.getMatches(address);

            Pub pub = new Pub();
            pub.setSubject(address);
            pub.setMatches(matches);
            if (matches != null && !matches.isEmpty()) {
                if (tokenizer.countTokens() >= 2) {
                    replyTo = tokenizer.nextToken();
                    sizeBuffer = tokenizer.nextToken();
                } else {
                    sizeBuffer = tokenizer.nextToken();
                }

                int payloadSize = sizeBuffer.parseToInt();
                int offset = firstNewLine + 1;
                BufferWrapper payload = null;
                int payloadEnd = payloadSize + offset;

                if (payloadEnd < buffer.length()) {
                    payload = BufferWrapper.bufferWrapper(buffer, offset, payloadEnd);
                }

                if (replyTo != null) {
                    pub.setReplyTo(replyTo);
                }
                pub.bytesRead(messageEnd - start);
                pub.setNoBytesBuffer(sizeBuffer);
                pub.setPayloadSize(payloadSize);
                pub.setPayload(payload);
                return pub;
            } else {
                pub.bytesRead(secondNewLine - start);
                return pub;
            }
        } else if (pedantic && firstNewLine > 0) {
            //Parse the header - which should throw exception if not valid
            BufferTokenizer tokenizer = new BufferTokenizer(buffer, start + 3, firstNewLine);
            subject = tokenizer.nextToken();
            Address address = new Address(subject);

            if (tokenizer.countTokens() >= 2) {
                replyTo = tokenizer.nextToken();
                sizeBuffer = tokenizer.nextToken();
            } else {
                sizeBuffer = tokenizer.nextToken();
            }

            int size = sizeBuffer.parseToInt();

        }
        return null;
    }

    static AbstractCommand getCommand(Buffer buffer, boolean pedantic, final int start, final int end) throws ProtocolException {
        AbstractCommand command;
        int pos = start;
        StringBuffer stringBuffer = new StringBuffer(20);
        for (; pos < end; pos++) {
            byte b = buffer.getByte(pos);
            stringBuffer.append((char) b);

            if (b == NEWLINE && stringBuffer.length() > 1) {
                String strCommand = stringBuffer.toString();
                if (strCommand.startsWith("CO")) {
                    command = new Connect();
                } else if (strCommand.startsWith("PU")) {
                    command = new Pub();
                } else if (strCommand.startsWith("PI")) {
                    command = new Ping();

                } else if (strCommand.startsWith("PO")) {
                    command = new Pong();

                } else if (strCommand.startsWith("SU")) {
                    command = new Sub();
                } else if (strCommand.startsWith("UN")) {
                    command = new UnSub();
                } else {
                    throw new ProtocolException("Unexpected command: " + +strCommand.length() + " len " + strCommand + " COMPLETE BUFFER = " + buffer.toString());
                }
                command.build(buffer, start, pos);
                command.bytesRead(pos - start);
                CommandInfo commandInfo = new CommandInfo();
                commandInfo.setCommand(command);
                return command;
            }
        }
        return null;
    }

    public static Msg createMsg(Buffer sid, Pub pub) {
        Msg msg = new Msg();
        msg.setSubject(pub.getSubject());
        msg.setReplyTo(pub.getReplyTo());
        msg.setSid(sid);
        msg.setNoBytes(pub.getNoBytesBuffer());
        msg.setPayload(pub.getPayload());
        int sizeHint = MSG_NAME.length() + pub.getSubject().size();
        if (pub.getReplyTo() != null && pub.getReplyTo().length() > 0) {
            sizeHint += pub.getReplyTo().length();
        }
        if (pub.getPayload() != null) {
            sizeHint += pub.getPayload().length();
        }
        sizeHint += (CRLF.length() * 2) + 3;

        Buffer buffer = Buffer.buffer(sizeHint);
        buffer.appendBuffer(MSG_NAME);
        msg.getSubject().appendToBuffer(buffer);
        buffer.appendBuffer(SPACE);
        buffer.appendBuffer(sid);
        buffer.appendBuffer(SPACE);
        if (msg.getReplyTo() != null && msg.getReplyTo().length() > 0) {
            msg.getReplyTo().appendTo(buffer);
            buffer.appendBuffer(SPACE);
        }
        msg.getNoBytes().appendTo(buffer);
        buffer.appendBuffer(CRLF);
        if (msg.getRawPayload() != null) {
            msg.getRawPayload().appendTo(buffer);
        }
        buffer.appendBuffer(CRLF);
        return msg;
    }

    private static boolean isPub(Buffer buffer, int start, int end) {
        return (start + 1) < end && buffer.getByte(start) == 'P' && buffer.getByte(start + 1) == 'U';
    }
}
