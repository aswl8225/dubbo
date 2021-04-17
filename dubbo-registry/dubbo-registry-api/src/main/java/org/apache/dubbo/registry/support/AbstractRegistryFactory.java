/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * AbstractRegistryFactory. (SPI, Singleton, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.RegistryFactory
 */
public abstract class AbstractRegistryFactory implements RegistryFactory {

    // Log output
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRegistryFactory.class);

    // The lock for the acquisition process of the registry
    protected static final ReentrantLock LOCK = new ReentrantLock();

    // Registry Collection Map<RegistryAddress, Registry>
    protected static final Map<String, Registry> REGISTRIES = new HashMap<>();

    private static final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * Get all registries
     *
     * @return all registries
     */
    public static Collection<Registry> getRegistries() {
        //service-discovery-registry://113.96.131.199:8849/org.apache.dubbo.registry.RegistryService -> {ServiceDiscoveryRegistry@5778}
        return Collections.unmodifiableCollection(new LinkedList<>(REGISTRIES.values()));
    }

    public static Registry getRegistry(String key) {
        return REGISTRIES.get(key);
    }

    public static List<ServiceDiscovery> getServiceDiscoveries() {
        return AbstractRegistryFactory.getRegistries()
                .stream()
                .filter(registry -> registry instanceof ServiceDiscoveryRegistry)
                .map(registry -> (ServiceDiscoveryRegistry) registry)
                .map(ServiceDiscoveryRegistry::getServiceDiscovery)
                .collect(Collectors.toList());
    }

    /**
     * Close all created registries
     */
    public static void destroyAll() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Close all registries " + getRegistries());
        }
        // Lock up the registry shutdown process
        LOCK.lock();
        try {
            for (Registry registry : getRegistries()) {
                try {
                    registry.destroy();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
            REGISTRIES.clear();
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }

    private Registry getDefaultNopRegistryIfDestroyed() {
        if (destroyed.get()) {
            LOGGER.warn("All registry instances have been destroyed, failed to fetch any instance. " +
                    "Usually, this means no need to try to do unnecessary redundant resource clearance, all registries has been taken care of.");
            return DEFAULT_NOP_REGISTRY;
        }
        return null;
    }

    public static Registry getDefaultNopRegistryIfNotSupportServiceDiscovery() {
        return DEFAULT_NOP_REGISTRY;
    }

    /**
     * 根据url获取对应的Registry
     * @param url Registry address, is not allowed to be empty
     * @return
     */
    @Override
    public Registry getRegistry(URL url) {

        Registry defaultNopRegistry = getDefaultNopRegistryIfDestroyed();
        if (null != defaultNopRegistry) {
            return defaultNopRegistry;
        }

        //url重新设置path  在parameter中新增interface属性并删除export属性
        url = URLBuilder.from(url)
                .setPath(RegistryService.class.getName())
                .addParameter(INTERFACE_KEY, RegistryService.class.getName())
                .removeParameters(EXPORT_KEY, REFER_KEY)
                .build();
        String key = createRegistryCacheKey(url);
        // Lock the registry access process to ensure a single instance of the registry
        LOCK.lock();
        try {
            // double check
            // fix https://github.com/apache/dubbo/issues/7265.
            defaultNopRegistry = getDefaultNopRegistryIfDestroyed();
            if (null != defaultNopRegistry) {
                return defaultNopRegistry;
            }
            /**
             * 取缓存
             */
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            //create registry by spi/ioc
            /**
             * 创建url对应的Registry  服务自省【ServiceDiscoveryRegistryFactory】或依赖注册中心【nacos】
             * 创建url对应的Registry  服务自省【ServiceDiscoveryRegistryFactory】或依赖注册中心【nacos】
             * 创建url对应的Registry  服务自省【ServiceDiscoveryRegistryFactory】或依赖注册中心【nacos】
             * 服务自省：service-discovery-registry://113.96.131.199:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-nacos-provider-demo&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&metadata-type=remote&password=nacos&pid=12916&registry=nacos&registry-type=service&registry.type=service&timestamp=1604897547641&username=nacos
             * 注册中心：nacos://113.96.131.199:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-annotation-provider&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&metadata-type=remote&pid=2460&timestamp=1604897600623
             */
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            /**
             * 缓存
             */
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }

    /**
     * Create the key for the registries cache.
     * This method may be override by the sub-class.
     *
     * @param url the registration {@link URL url}
     * @return non-null
     */
    protected String createRegistryCacheKey(URL url) {
        return url.toServiceStringWithoutResolving();
    }

    protected abstract Registry createRegistry(URL url);


    private static Registry DEFAULT_NOP_REGISTRY = new Registry() {
        @Override
        public URL getUrl() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void destroy() {

        }

        @Override
        public void register(URL url) {

        }

        @Override
        public void unregister(URL url) {

        }

        @Override
        public void subscribe(URL url, NotifyListener listener) {

        }

        @Override
        public void unsubscribe(URL url, NotifyListener listener) {

        }

        @Override
        public List<URL> lookup(URL url) {
            return null;
        }
    };

    public static void removeDestroyedRegistry(Registry toRm) {
        LOCK.lock();
        try {
            REGISTRIES.entrySet().removeIf(entry -> entry.getValue().equals(toRm));
        } finally {
            LOCK.unlock();
        }
    }

    // for unit test
    public static void clearRegistryNotDestroy() {
        REGISTRIES.clear();
    }

}
