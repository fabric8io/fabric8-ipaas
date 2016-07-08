package io.fabric8.camel.master;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ClientMixedOperation;
import io.fabric8.kubernetes.client.mock.BaseMockOperation;
import io.fabric8.kubernetes.client.mock.KubernetesMockClient;
import io.fabric8.kubernetes.client.mock.MockResource;
import io.fabric8.kubernetes.client.mock.impl.donable.MockDoneableConfigMap;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.easymock.EasyMock.anyObject;
import static org.junit.Assert.assertTrue;

/**
 * Created by valdar on 06/07/16.
 */
public class KubernetesLockTest {

    public static final String TEST_NAMESPACE = "testNamespace";
    public static final String TEST_CONFIG_MAP_NAME = "testConfigMapName";
    public static final String TEST_ENDPOINT = "testEndpoint";
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void  testLcokAcquiredOrRetry() throws Exception {
        //set env variable useful during KubernetesLock construction
        environmentVariables.set("HOSTNAME", "hostname");

        //create Kubernetes mock client
        KubernetesMockClient mock = new KubernetesMockClient();

        ConfigMap cm = new ConfigMapBuilder().withNewMetadata().withName(TEST_CONFIG_MAP_NAME).endMetadata().build();

        //set expected configMap for get and watch
        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).get().andReturn( cm ).anyTimes();
        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).watch( anyObject(Watcher.class) )
                .andReturn(new Watch() {
                    @Override
                    public void close() {

                    }
                }).anyTimes();

//XXX: ideally should be like this:
//        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).patch(cm).andThrow(new RuntimeException("Failed patching of a resource")).once();
//        mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME).patch(cm).andReturn(cm).times(1);
// An issue has been opened to kubernetes-client project (kubernetes-mock module): https://github.com/fabric8io/kubernetes-client/issues/458

        //set expected patch calls: first time an excetion is thrown (means someone else has acuired the lock), second time is a success
        MockResource<ConfigMap, MockDoneableConfigMap, Boolean> resource = mock.configMaps().inNamespace(TEST_NAMESPACE).withName(TEST_CONFIG_MAP_NAME);
        Field delegate = BaseMockOperation.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        ClientMixedOperation delegateResource = (ClientMixedOperation)delegate.get(resource);
        IExpectationSetters<Object> expect = EasyMock.expect(delegateResource.patch(cm));
        expect.andThrow(new RuntimeException("Failed patching of a resource")).once();
        expect.andReturn(cm).times(1);

        NamespacedKubernetesClient client = mock.replay();

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

}
