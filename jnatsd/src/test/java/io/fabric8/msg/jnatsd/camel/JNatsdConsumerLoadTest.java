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
package io.fabric8.msg.jnatsd.camel;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class JNatsdConsumerLoadTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testLoadConsumer() throws InterruptedException, IOException, TimeoutException {
        mockResultEndpoint.setExpectedMessageCount(10000);
        Connection connection = new ConnectionFactory().createConnection();

        for (int i = 0; i < 10000; i++) {
            connection.publish("test", ("test" + i).getBytes());
        }

        connection.close();
        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:send").to("jnatsd:test");
                from("jnatsd:test").to(mockResultEndpoint);
            }
        };
    }

}
