/**
 *  Copyright 2005-2016 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.camel.master;

import io.fabric8.camel.master.MasterEndpoint;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration
public class MasterEndpointKT extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "seda:bar")
    protected ProducerTemplate template;

    @Before
    public void startService() throws Exception {
        ServiceHelper.startService(camelContext);
        ServiceHelper.startService(template);
    }

    @After
    public void afterRun() throws Exception {
        ServiceHelper.stopServices(camelContext);
    }

    @Test
    public void  testEndpoint() throws Exception {
        // check the endpoint configuration
        List<Route> registeredRoutes = camelContext.getRoutes();
        assertThat(registeredRoutes).describedAs("routes").hasSize(1);

        MasterEndpoint endpoint = (MasterEndpoint) registeredRoutes.get(0).getEndpoint();
        assertThat(endpoint.getChild()).describedAs("endpoint URI").isEqualTo("seda:bar");

        System.out.println("===== starting test of Master endpoint!");

        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        // lets wait for the entry to be registered...
        Thread.sleep(5000);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        MockEndpoint.assertIsSatisfied(camelContext);

        System.out.println("===== completed test of Master endpoint!");
 
    }
}
