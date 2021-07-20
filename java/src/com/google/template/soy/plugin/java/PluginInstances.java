/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.plugin.java;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.function.Supplier;

/** Simple wrapper around the map of plugin instance suppliers. */
public final class PluginInstances {

  public static PluginInstances empty() {
    return new PluginInstances(ImmutableMap.of());
  }

  public static PluginInstances of(Map<String, ? extends Supplier<Object>> pluginInstances) {
    return new PluginInstances(pluginInstances);
  }

  private final ImmutableMap<String, ? extends Supplier<Object>> pluginInstances;

  private PluginInstances(Map<String, ? extends Supplier<Object>> pluginInstances) {
    this.pluginInstances = ImmutableMap.copyOf(pluginInstances);
  }

  public Supplier<Object> get(String name) {
    return pluginInstances.get(name);
  }

  public ImmutableSet<String> keys() {
    return pluginInstances.keySet();
  }

  public PluginInstances combine(Map<String, ? extends Supplier<Object>> morePlugins) {
    if (morePlugins.isEmpty()) {
      return this;
    }
    return new PluginInstances(
        ImmutableMap.<String, Supplier<Object>>builder()
            .putAll(pluginInstances)
            .putAll(morePlugins)
            .build());
  }
}
