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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoutingMapTest {

    private Address a1 = new Address("TEST.A1");
    private Address a2 = new Address("TEST.A1.B1");

    private RoutingMap routingMap;

    @Before
    public void setUp() throws Exception {
        routingMap = new RoutingMap();
    }

    @After
    public void tearDown() throws Exception {
        routingMap = null;
    }

    @Test
    public void getMatches() throws Exception {
        List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

        subscriptions.add(new Subscription("TEST.A1.B1"));
        subscriptions.add(new Subscription("TEST.A1.B2"));

        for (Subscription subscription : subscriptions) {
            routingMap.addSubcription(null, subscription);
        }
        Address key = new Address(("TEST.>"));
        assertMapValue(key, subscriptions);
    }

    @Test
    public void testSubscriptionAfterAddressCreated() throws Exception {
        Address key = new Address(("TEST.>"));
        assertMapValue(key, Collections.EMPTY_SET);

        List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

        subscriptions.add(new Subscription("TEST.A1.B1"));
        subscriptions.add(new Subscription("TEST.A1.B2"));

        for (Subscription subscription : subscriptions) {
            routingMap.addSubcription(null, subscription);
        }
        assertMapValue(key, subscriptions);
    }

    protected void assertMapValue(Address key, Collection<Subscription> expected) {
        Collection set = routingMap.getMatches(key);
        if (set == null) {
            set = Collections.EMPTY_SET;
        }
        Assert.assertEquals("map value for destinationName:  " + key, expected, set);
    }

}
