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

package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;

import java.util.List;

public class ListenerRegistryWrapper implements Registry {
    private static final Logger logger = LoggerFactory.getLogger(ListenerRegistryWrapper.class);

    private final Registry registry;
    private final List<RegistryServiceListener> listeners;


    public ListenerRegistryWrapper(Registry registry, List<RegistryServiceListener> listeners) {
        this.registry = registry;
        this.listeners = listeners;
    }

    @Override
    public URL getUrl() {
        return registry.getUrl();
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable();
    }

    @Override
    public void destroy() {
        registry.destroy();
    }

    /**
     * 注册
     * @param url  Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    @Override
    public void register(URL url) {
        try {
            /**
             * 非服务自省  FailbackRegistry
             * 服务自省    ServiceDiscoveryRegistry
             */
            registry.register(url);
        } finally {
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (RegistryServiceListener listener : listeners) {
                    if (listener != null) {
                        try {
                            listener.onRegister(url);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }

    @Override
    public void unregister(URL url) {
        try {
            registry.unregister(url);
        } finally {
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (RegistryServiceListener listener : listeners) {
                    if (listener != null) {
                        try {
                            listener.onUnregister(url);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }

    /**
     * 订阅
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    @Override
    public void subscribe(URL url, NotifyListener listener) {
        try {
            /**
             * 非服务自省   FailbackRegistry  启动服务消费者并开启提供者服务实例订阅
             * 服务自省     ServiceDiscoveryRegistry
             */
            registry.subscribe(url, listener);
        } finally {
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (RegistryServiceListener registryListener : listeners) {
                    if (registryListener != null) {
                        try {
                            registryListener.onSubscribe(url);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        try {
            registry.unsubscribe(url, listener);
        } finally {
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (RegistryServiceListener registryListener : listeners) {
                    if (registryListener != null) {
                        try {
                            registryListener.onUnsubscribe(url);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }

    @Override
    public List<URL> lookup(URL url) {
        return registry.lookup(url);
    }

    public Registry getRegistry() {
        return registry;
    }
}
