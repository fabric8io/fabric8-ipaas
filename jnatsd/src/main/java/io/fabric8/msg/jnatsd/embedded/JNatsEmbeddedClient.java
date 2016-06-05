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

package io.fabric8.msg.jnatsd.embedded;

import io.fabric8.msg.jnatsd.JNatsAbstractClient;
import io.fabric8.msg.jnatsd.JNatsd;
import io.fabric8.msg.jnatsd.protocol.Command;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JNatsEmbeddedClient extends JNatsAbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(JNatsEmbeddedClient.class);
    private final Handler<Command> commandHandler;

    public JNatsEmbeddedClient(JNatsd jnatsd, Handler<Command> handler) {
        super(jnatsd);
        this.commandHandler = handler;
        consume(jnatsd.getServerInfo());
    }

    @Override
    public void consume(Command command) {
        if (command != null && closed.get() == false) {
            commandHandler.handle(command);
        }
    }
}
