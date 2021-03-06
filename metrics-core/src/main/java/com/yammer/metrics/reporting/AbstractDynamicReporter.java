/*
 * Copyright (c) 2012. Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package com.yammer.metrics.reporting;

import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.MetricsRegistryListener;

import java.util.Set;

public abstract class AbstractDynamicReporter extends AbstractReporter implements MetricsRegistryListener {

    protected AbstractDynamicReporter(Set<MetricsRegistry> registries,  String name){
        super(registries, name);

        for (MetricsRegistry metricsRegistry : metricsRegistries) {
            metricsRegistry.addListener(this);
        }
    }

    /**
     * Stops the reporter and closes any internal resources.
     */
    public void shutdown() {
        for (MetricsRegistry metricsRegistry : metricsRegistries) {
            metricsRegistry.removeListener(this);
        }
    }
}
