/**
 * Copyright (C) 2016 Red Hat, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.msg.gateway.brokers.impl;

import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;


/**
 * Watches {@link Endpoints} objects
 */
public class EndpointWatcher implements Watcher<Endpoints> {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBrokerControl.class);
    private static final Map<NamespaceName, Long> endpointsVersions = new HashMap<>();

    private final KubernetesBrokerControl kubernetesBrokerControl;
    private final String defaultNamespace;

    public EndpointWatcher(KubernetesBrokerControl kubernetesBrokerControl, String defaultNamespace) {
        this.kubernetesBrokerControl = kubernetesBrokerControl;
        this.defaultNamespace = defaultNamespace;
    }

    @Override
    public void onClose(KubernetesClientException e) {
        if (e != null) {
            LOG.warn(e.getMessage(), e);
        }
    }


    public void onInitialEndpoints(Endpoints endpoints) throws IOException {
        upsertEndpoint(endpoints);
    }

    @Override
    public void eventReceived(Watcher.Action action, Endpoints endpoint) {
        try {
            switch (action) {
                case ADDED:
                    upsertEndpoint(endpoint);
                    break;
                case DELETED:
                    deleteEndpoint(endpoint);
                    break;
                case MODIFIED:
                    modifyEndpoint(endpoint);
                    break;
            }
        } catch (IOException | InterruptedException e) {
            LOG.warn("Caught: " + e, e);
        }
    }

    private void upsertEndpoint(Endpoints endpoints) throws IOException {
        synchronized (endpointsVersions) {
            NamespaceName namespacedName = NamespaceName.create(endpoints);
            Long resourceVersion = getResourceVersion(endpoints);
            Long previousResourceVersion = endpointsVersions.get(namespacedName);

            LOG.info("upsertEndpoint: " + namespacedName);

            // lets only process this EndpointSubset if the resourceVersion is newer than the last one we processed
            if (previousResourceVersion == null || (resourceVersion != null && resourceVersion > previousResourceVersion)) {
                endpointsVersions.put(namespacedName, resourceVersion);

                try {
                    kubernetesBrokerControl.createClientForEndpoints(endpoints);
                } catch (Exception e) {
                    LOG.warn("Failed to update Endpoints " + namespacedName + ": " + e, e);
                }
            } else {
                LOG.info("Ignored out of order notification for Endpoints " + namespacedName
                        + " with resourceVersion " + resourceVersion + " when we have already processed " + previousResourceVersion);
            }
        }
    }


    private void modifyEndpoint(Endpoints endpoint) throws IOException, InterruptedException {
        upsertEndpoint(endpoint);
    }

    private void deleteEndpoint(Endpoints endpoint) throws IOException, InterruptedException {
        NamespaceName namespaceName = NamespaceName.create(endpoint);
        synchronized (endpointsVersions) {
            endpointsVersions.remove(namespaceName);
        }
    }

    public static Long getResourceVersion(HasMetadata hasMetadata) {
        ObjectMeta metadata = hasMetadata.getMetadata();
        String resourceVersionText = metadata.getResourceVersion();
        Long resourceVersion = null;
        if (resourceVersionText != null && resourceVersionText.length() > 0) {
            resourceVersion = Long.parseLong(resourceVersionText);
        }
        return resourceVersion;
    }
}
