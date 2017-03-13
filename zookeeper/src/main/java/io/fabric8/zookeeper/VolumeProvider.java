package io.fabric8.zookeeper;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

public enum VolumeProvider {

    HOST_PATH {
        @Override
        public Volume create(String name, int serverId) {
            return new VolumeBuilder()
                    .withName(name)
                    .withNewHostPath(HOST_PATH_PREFIX + "-" + serverId)
                    .build();
        }
    },
    EMPTY_DIR {
        @Override
        public Volume create(String name, int serverId) {
            return new VolumeBuilder()
                    .withName(name)
                    .withNewEmptyDir(DEFAULT_MEDIUM)
                    .build();
        }

    },
    FLOCKER {
        @Override
        public Volume create(String name, int serverId) {
            return new VolumeBuilder()
                    .withName(name)
                    .withNewFlocker(name, FLOCKER_PREFIX + "-" + serverId)
                    .build();
        }
    };

    public abstract Volume create(String name, int serverId);

    private static final String HOST_PATH_PREFIX = "/opt/zookeeper/data";
    private static final String FLOCKER_PREFIX = "zookeeper-dataset";
    private static final String DEFAULT_MEDIUM = "Memory";
}
