/**
 * Copyright 2005-2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.camel.master;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an endpoint which only becomes active when it obtains the master lock
 */
public class MasterEndpoint extends DefaultEndpoint implements DelegateEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterEndpoint.class);

    private final MasterComponent component;
    private final String singletonId;
    private final String child;
    private final Endpoint childEndpoint;
    private final KubernetesLock lock;
    private final AtomicReference<MasterConsumer> latestConsumer = new AtomicReference<>();


    public MasterEndpoint(String uri, MasterComponent component, String singletonId, String child) {
        super(uri, component);
        this.component = component;
        this.singletonId = singletonId;
        this.child = child;
        this.childEndpoint = getCamelContext().getEndpoint(child);
        this.lock = new KubernetesLock(component.getKubernetesClient(), component.getNamespace(), component.getConfigMapName(), singletonId, new Runnable() {
            @Override
            public void run() {
                onLockOwned();
            }
        });
    }

    public String getSingletonId() {
        return singletonId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return getChildEndpoint().createProducer();
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        MasterConsumer consumer = new MasterConsumer(this, processor);
        latestConsumer.set(consumer);
        lock.tryAcquireLock();
        if (lock.isOwner()) {
            consumer.onLockOwned();
        }
        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // to allow properties to be propagated to the child endpoint
        return true;
    }

    // Properties
    //-------------------------------------------------------------------------
    public MasterComponent getComponent() {
        return component;
    }

    public String getChild() {
        return child;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    Endpoint getChildEndpoint() {
        return childEndpoint;
    }

    public Endpoint getEndpoint() {
        return getChildEndpoint();
    }

    protected void onLockOwned() {
        MasterConsumer consumer = latestConsumer.get();
        if (consumer != null) {
            consumer.onLockOwned();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (lock != null) {
            lock.close();
        }
        super.doStop();
    }
}
