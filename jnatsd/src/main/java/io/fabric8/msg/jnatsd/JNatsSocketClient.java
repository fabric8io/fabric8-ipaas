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

import io.fabric8.msg.jnatsd.protocol.AbstractCommand;
import io.fabric8.msg.jnatsd.protocol.Command;
import io.fabric8.msg.jnatsd.protocol.CommandFactory;
import io.fabric8.msg.jnatsd.protocol.Info;
import io.fabric8.msg.jnatsd.protocol.ProtocolException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JNatsSocketClient extends JNatsAbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(JNatsSocketClient.class);
    private final NetSocket socket;

    /**
     * Read buffer
     */
    private int bufferReadEnd;
    private int bufferStart;
    private Buffer current;

    public JNatsSocketClient(JNatsd jnatsd, Info serverInfo, NetSocket netSocket) {
        super(jnatsd);
        this.socket = netSocket;
        this.socket.setWriteQueueMaxSize(jnatsd.getConfiguration().getMaxPendingSize());
        socket.exceptionHandler(e -> {
            LOG.error("Socket error", e);
            close();
        });

        socket.closeHandler(v -> {
            close();
        });

        socket.handler(buffer -> {
            try {
                process(buffer);
            } catch (Throwable e) {
                String message = "Parser Error: " + buffer;
                close(message);
            }
        });
        consume(serverInfo);
    }

    @Override
    public String toString() {
        String result = "NatsClient{";
        if (connect != null) {
            result += connect.getProperties();
        }
        result += "}";
        return result;
    }

    @Override
    public boolean close() {
        boolean result = false;
        if ((result = super.close())) {
            try {
                socket.close();
            } catch (Throwable e) {
                LOG.warn("Problem closing: ", e);
            }
        }
        return result;
    }

    @Override
    public void consume(Command command) {
        if (command != null && closed.get() == false) {
            try {
                socket.write(command.getBuffer());
            } catch (Throwable e) {
                LOG.warn("Failed to consume " + command, e);
                close();
            }
        }
    }

    private void process(Buffer buffer) throws ProtocolException {
        if (closed.get() == false) {
            if (current == null) {
                current = buffer;
                bufferStart = 0;
            } else {
                current.appendBuffer(buffer);
            }
            bufferReadEnd = current.length();

            AbstractCommand command;

            do {
                command = CommandFactory.processBuffer(jNatsd.getRoutingMap(), connect.isPedantic(), current, bufferStart, bufferReadEnd);
                if (command != null) {
                    bufferStart += command.bytesRead() + 1;
                    publish(command);
                    if (bufferStart >= current.length()) {
                        current = null;
                        bufferStart = bufferReadEnd = 0;
                        break;
                    }
                }
            } while (command != null);
        }
    }
}
