package io.fabric8.zookeeper;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpecBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.generator.annotation.KubernetesModelProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kubernetes ZooKeeper Ensemble compile time customizer
 * Reads the size of the ensemble from System properties and creates equal number of ensemble replicationControllers and services.
 */
@KubernetesModelProcessor
public class ZooKeeperEnsembleCustomizer {

    private static final String TEMPLATE_NAME_PROP = "template.name";
    private static final String ENSEMBLE_SIZE_PROP = "ensemble.size";
    private static final String VOLUME_TYPE_PROP = "volume.type";

    private static final String SERVER_ID_NAME = "SERVER_ID";
    private static final String DATA_VOLUME_NAME = "data";
    private static final String HOST_PATH_PREFIX = "/opt/zookeeper/data";

    private static final String DEFAULT_TEMPLATE = "zookeeper";
    private static final String DEFAULT_ENSEMBLE_SIZE = "3";
    private static final String DEFAULT_VOLUME_TYPE = VolumeProvider.EMPTY_DIR.name();

    private static final String SERVER_ID = "serverId";
    private static final String LOADBALANCER_TYPE = "LoadBalancer";
    private static final String CLIENT = "client";

    private static String TEMPLATE_NAME = System.getProperty(TEMPLATE_NAME_PROP, DEFAULT_TEMPLATE);
    private static int ENSEMBLE_SIZE = Integer.parseInt(System.getProperty(ENSEMBLE_SIZE_PROP, DEFAULT_ENSEMBLE_SIZE));
    private static VolumeProvider VOLUME_PROVIDER = VolumeProvider.valueOf(System.getProperty(VOLUME_TYPE_PROP, DEFAULT_VOLUME_TYPE));

    public void on(KubernetesListBuilder builder) {
        Service service = null;
        ReplicationController controller = null;
        List<HasMetadata> items = builder.getItems();
        List<HasMetadata> newItems = new ArrayList<>();

        for (HasMetadata item : items) {
            if (TEMPLATE_NAME.equals(item.getMetadata().getName())) {
                if (item.getKind().equals(ReplicationController.class.getSimpleName())) {
                    controller = (ReplicationController) item;
                    continue;
                } else if (item.getKind().equals(Service.class.getSimpleName())) {
                    service = (Service) item;
                    continue;
                }
            }
            newItems.add(item);
        }

        if (service != null && controller != null) {
            for (int i = 1; i <= ENSEMBLE_SIZE; i++) {
                //Add a customized controller
                newItems.add(new ReplicationControllerBuilder(controller)
                        .editMetadata()
                        .withName(DEFAULT_TEMPLATE + "-" + i)
                        .endMetadata()
                        .accept(new MetadataCustormizer(i))
                        .accept(new ReplicationControllerSpecCustomizer(i))
                        .accept(new VolumeCustomizer(i))
                        .accept(new ContainerCommandCustomizer(i))
                        .build());

                //Add a customized service
                if (ENSEMBLE_SIZE > 1) {
                    newItems.add(new ServiceBuilder(service)
                            .editMetadata()
                            .withName(DEFAULT_TEMPLATE + "-" + i)
                            .endMetadata()
                            .accept(new ServiceSpecCustormizer(i))
                            .build());
                }
            }
        }

        newItems.add(new ServiceBuilder(service)
                .editSpec()
                .withType(LOADBALANCER_TYPE)
                .withClusterIP(null)
                .withPorts(new ServicePortBuilder()
                        .withName(CLIENT)
                        .withPort(2181)
                        .withNewTargetPort(2181)
                        .build())
                .endSpec()
                .build());


        builder.removeFromReplicationControllerItems(controller);
        builder.removeFromServiceItems(service);
        builder.withItems(newItems);
    }

    private static class MetadataCustormizer implements Visitor<ObjectMetaBuilder> {
        private final int serverId;

        private MetadataCustormizer(int serverId) {
            this.serverId = serverId;
        }

        @Override
        public void visit(ObjectMetaBuilder builder) {
            builder.addToLabels(SERVER_ID, String.valueOf(serverId));
        }
    }


    private static class ContainerCommandCustomizer implements Visitor<ContainerBuilder> {

        private final int serverId;

        private ContainerCommandCustomizer(int serverId) {
            this.serverId = serverId;
        }

        @Override
        public void visit(ContainerBuilder builder) {
            builder.addNewEnv()
                    .withName(SERVER_ID_NAME).withValue(String.valueOf(serverId))
            .endEnv();

        }
    }

    private static class ReplicationControllerSpecCustomizer implements Visitor<ReplicationControllerSpecBuilder> {
        private final int serverId;

        private ReplicationControllerSpecCustomizer(int serverId) {
            this.serverId = serverId;
        }

        @Override
        public void visit(ReplicationControllerSpecBuilder builder) {
            builder.addToSelector(SERVER_ID, String.valueOf(serverId));
        }
    }


    private static class VolumeCustomizer implements Visitor<PodSpecBuilder> {

        private final int serverId;

        private VolumeCustomizer(int serverId) {
            this.serverId = serverId;
        }

        @Override
        public void visit(PodSpecBuilder builder) {
            builder.addToVolumes(VOLUME_PROVIDER.create(DATA_VOLUME_NAME, serverId));
        }
    }

    private static class ServiceSpecCustormizer implements Visitor<ServiceSpecBuilder> {
        private final int serverId;

        private ServiceSpecCustormizer(int serverId) {
            this.serverId = serverId;
        }

        @Override
        public void visit(ServiceSpecBuilder builder) {
            builder.addToSelector(SERVER_ID, String.valueOf(serverId));
        }
    }
}
