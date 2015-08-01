/*
 * Copyright 2014-2015 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.wunderboss.as.wildfly;

import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.msc.service.ServiceRegistry;
import org.projectodd.wunderboss.Options;
import org.projectodd.wunderboss.WunderBoss;
import org.projectodd.wunderboss.as.ClusterUtils;
import org.projectodd.wunderboss.as.MSCService;
import org.projectodd.wunderboss.as.ASUtils;
import org.projectodd.wunderboss.caching.InfinispanCaching;
import org.projectodd.wunderboss.caching.Encoder6;

import java.util.Map;


public class WildFlyCaching extends InfinispanCaching {

    public WildFlyCaching(String name, Options<CreateOption> options) {
        super(name, options);
        if (ASUtils.containerIsWildFly8()) {
            this.encoder = new Encoder6();
        }
    }

    public synchronized EmbeddedCacheManager manager() {
        if (this.manager == null) {
            this.manager = getWebCacheManager();
        }
        return this.manager;
    }

    protected Options<CreateOption> validate(Map<CreateOption,Object> options) {
        Options<CreateOption> result = new Options<CreateOption>(options);
        String mode = result.getString(CreateOption.MODE);
        // Default mode when in a cluster
        if (mode == null && ClusterUtils.inCluster()) {
            result.put(CreateOption.MODE, "DIST_SYNC");
        }
        return result;
    }

    private EmbeddedCacheManager getWebCacheManager() {
        ServiceRegistry serviceRegistry = (ServiceRegistry) WunderBoss.options().get("service-registry");
        return (EmbeddedCacheManager) serviceRegistry.getRequiredService(MSCService.WEB_CACHE_MANAGER).getValue();
    }

    private GlobalConfiguration getGlobalConfiguration() {
        GlobalConfigurationBuilder builder = new GlobalConfigurationBuilder();
        return builder.read(getWebCacheManager().getCacheManagerConfiguration())
            .classLoader(Thread.currentThread().getContextClassLoader())
            .transport().clusterName("wboss")
            .build();
    }
}
