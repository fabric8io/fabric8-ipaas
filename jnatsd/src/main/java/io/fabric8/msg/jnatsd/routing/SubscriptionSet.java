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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubscriptionSet extends AbstractSet<Subscription> {
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean queueGroup = new AtomicBoolean();

    @Override
    public boolean add(Subscription subscription) {
        if (!subscriptions.contains(subscription)) {
            if (subscription.isQueueGroup()) {
                queueGroup.set(true);
            }
            return subscriptions.add(subscription);
        }
        return false;
    }

    @Override
    public boolean remove(Object subscription) {
        boolean result = subscriptions.remove(subscription);
        if (result && queueGroup.get()) {
            boolean flag = false;
            for (Subscription s : subscriptions) {
                if (s.isQueueGroup()) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                queueGroup.compareAndSet(true, false);
            }
        }
        return result;
    }

    public Collection<Subscription> getMatchSet() {
        if (queueGroup.get() && subscriptions.size() > 1) {
            boolean queueSubscriberAdded = false;
            List<Subscription> result = new ArrayList<>(1);
            for (Subscription s : subscriptions) {
                if (s.isQueueGroup()) {
                    if (!queueSubscriberAdded) {
                        queueSubscriberAdded = true;
                        result.add(s);
                        subscriptions.remove(s);
                        subscriptions.add(s);
                    }
                } else {
                    result.add(s);
                }
            }
            return result;
        }
        return subscriptions;
    }

    @Override
    public Iterator<Subscription> iterator() {
        return subscriptions.iterator();
    }

    @Override
    public int size() {
        return subscriptions.size();
    }

    @Override
    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    @Override
    public void clear() {
        subscriptions.clear();
    }

    @Override
    public String toString() {
        return "SubscriptionSet:queue=" + queueGroup + "{" + subscriptions + "}";
    }
}
