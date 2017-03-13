package io.fabric8.camel.master;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;

import io.fabric8.openshift.server.mock.OpenShiftMockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.getenv;
import static org.junit.Assert.assertTrue;

/**
 * Created by valdar on 06/07/16.
 */
public class KubernetesLockTest {

    public static final String TEST_NAMESPACE = "testNamespace";
    public static final String TEST_CONFIG_MAP_NAME = "testConfigMapName";
    public static final String TEST_ENDPOINT = "testEndpoint";

    private static final OpenShiftMockServer MOCK = new OpenShiftMockServer();

    @BeforeClass
    public static void setupClass() {
        //set expected configMap for get and watch
        ConfigMap cm = new ConfigMapBuilder().withNewMetadata().withName(TEST_CONFIG_MAP_NAME).endMetadata().build();

        MOCK.expect().get().withPath("/api/v1/namespaces/testNamespace/configmaps").andReturn(200, new ConfigMapListBuilder()
                .withNewMetadata()
                    .withResourceVersion("1")
                .endMetadata()
                .withItems(cm).build()).always();

        MOCK.expect().get().withPath("/api/v1/namespaces/testNamespace/configmaps/testConfigMapName").andReturn(200, cm).always();
        MOCK.expect().patch().withPath("/api/v1/namespaces/testNamespace/configmaps/testConfigMapName").andReturn(500, cm).once();
        MOCK.expect().patch().withPath("/api/v1/namespaces/testNamespace/configmaps/testConfigMapName").andReturn(200, cm).once();

        MOCK.expect().get().withPath("/api/v1/namespaces/testNamespace/configmaps?fieldSelector=metadata.name%3DtestConfigMapName&resourceVersion=1&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .done().always();


        String masterUrl = MOCK.getServer().url("/").toString();
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, masterUrl);
        System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, TEST_NAMESPACE);
    }

    @Test
    public void  testLcokAcquiredOrRetry() throws Exception {
        //set env variable useful during KubernetesLock construction
        getEditableEnvVariables().put("HOSTNAME", "hostname");

        KubernetesClient client = new DefaultKubernetesClient();

//XXX: ideally should be like this:
//        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).patch(cm).andThrow(new RuntimeException("Failed patching of a resource")).once();
//        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).patch(cm).andReturn(cm).times(1);
// An issue has been opened to kubernetes-client project (kubernetes-mock module): https://github.com/fabric8io/kubernetes-client/issues/458

        //set expected patch calls: first time an excetion is thrown (means someone else has acuired the lock), second time is a success
       /* MockResource<ConfigMap, MockDoneableConfigMap, Boolean> resource = mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME);
        Field delegate = BaseMockOperation.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        MixedOperation delegateResource = (MixedOperation)delegate.get(resource);
        IExpectationSetters<Object> expect = EasyMock.expect(delegateResource.patch(cm));
        expect.andThrow(new RuntimeException("Failed patching of a resource")).once();
        expect.andReturn(cm).times(1);

        NamespacedKubernetesClient client = mock.replay();
*/
        final AtomicBoolean lockAcquired = new AtomicBoolean(false);

        //create KubernetesLock passing the mock client
        KubernetesLock kl = new KubernetesLock(client, TEST_NAMESPACE, TEST_CONFIG_MAP_NAME, TEST_ENDPOINT, new Runnable() {
            @Override
            public void run() {
                lockAcquired.set(true);
            }
        });

        //running the acquire lock method
        kl.tryAcquireLock();

        //assert that lock is acquired despite the first time exception
        assertTrue(lockAcquired.get());
    }

    private static Map<String, String> getEditableEnvVariables() {
        Class<?> classOfMap = getenv().getClass();
        try {
            Field field = classOfMap.getDeclaredField("m");
            field.setAccessible(true);
            return (Map<String, String>) field.get(getenv());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access the field"
                    + " 'm' of the map System.getenv().", e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("System.getenv() is expected to"
                    + " have a field 'm' but it has not.", e);
        }
    }

}
