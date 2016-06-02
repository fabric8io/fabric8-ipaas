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

import io.fabric8.msg.jnatsd.JNatsClient;
import io.fabric8.msg.jnatsd.JNatsd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class RoutingMap {
    private static final Logger LOG = LoggerFactory.getLogger(JNatsd.class);
    private CopyOnWriteArraySet<Subscription> subscriptions = new CopyOnWriteArraySet<>();
    private Map<Address, SubscriptionSet> subscriptionMap = new ConcurrentHashMap<>();

    public void addSubcription(JNatsClient client, Subscription subscription) {
        subscriptions.add(subscription);
        addOrInsert(subscription);
        LOG.info("Added " + subscription + " to client " + client);
    }

    public void removeSubscription(JNatsClient client, Subscription subscription) {
        subscriptions.remove(subscription);
        clearSubscription(subscription);
        LOG.info("Removed " + subscription + " from client " + client);
    }

    public Collection<Subscription> getMatches(Address address) {
        Collection<Subscription> result = null;
        if (!subscriptionMap.containsKey(address)) {
            for (Subscription subscription : subscriptions) {
                if (subscription.getAddress().isMatch(address)) {
                    addOrInsert(address, subscription);
                }
            }
        }

        SubscriptionSet subscriptionSet = subscriptionMap.get(address);
        if (subscriptionSet != null) {
            result = subscriptionSet.getMatchSet();
        }
        return result;
    }

    private void addOrInsert(Address address, Subscription subscription) {
        SubscriptionSet set = subscriptionMap.get(address);
        if (set == null) {
            set = new SubscriptionSet();
            Set<Subscription> old = subscriptionMap.putIfAbsent(address, set);
            if (old != null) {
                set.addAll(old);
            }
        }
        set.add(subscription);
    }

    /**
     * Add to existing addresses
     *
     * @param subscription
     */
    private void addOrInsert(Subscription subscription) {

        for (Address address : subscriptionMap.keySet()) {
            if (subscription.getAddress().isMatch(address)) {
                addOrInsert(address, subscription);
            }
        }
    }

    private void clearSubscription(Subscription subscription) {
        for (Address address : subscriptionMap.keySet()) {
            if (subscription.getAddress().isMatch(address)) {
                SubscriptionSet set = subscriptionMap.get(address);
                if (set != null) {
                    set.remove(subscription);
                    if (set.isEmpty()) {
                        set = subscriptionMap.remove(address);
                        if (set != null && !set.isEmpty()) {
                            subscriptionMap.putIfAbsent(address, set);
                        }
                    }
                }
            }
        }
    }
}
