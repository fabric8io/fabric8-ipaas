/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.zookeeper;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.openshift.api.model.TemplateBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class BuilderHelpers {
    public static void logException(Throwable e) throws Throwable {
        System.out.println("Caught: " + e);
        e.printStackTrace();
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            System.out.println("Caused by : " + cause);
            cause.printStackTrace();
        }
        throw e;
    }

    /**
     * Returns the items on the builder without any validation errors
     */
    public static List<HasMetadata> getItems(KubernetesListBuilder builder) {
        List<HasMetadata> answer = null;
        if (builder != null) {
            try {
                answer = builder.getItems();
            } catch (Throwable e) {
                // ignore any validation errors
            }
        }
        return answer != null ? new ArrayList<>() : answer;
    }

    /**
     * Returns the objects on the builder without any validation errors
     */
    public static List<HasMetadata> getObjects(TemplateBuilder builder) {
        List<HasMetadata> answer = null;
        if (builder != null) {
            try {
                answer = builder.getObjects();
            } catch (Throwable e) {
                // ignore any validation errors
            }
        }
        return answer != null ? new ArrayList<>() : answer;
    }
}
