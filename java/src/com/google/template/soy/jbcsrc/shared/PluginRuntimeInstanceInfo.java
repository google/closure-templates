/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Info about a plugin runtime instance class, such as the class name and the source locations where
 * the plugin is used.
 *
 * <p>We store this info in meta-inf files and use it to verify that bindings have been provided for
 * all required plugin runtime instances. The source locations are stored so that we can provide
 * better error messages when a binding is missing, so that users can figure out where the function
 * is used / if they actually need it or should just remove the dep.
 */
@AutoValue
public abstract class PluginRuntimeInstanceInfo {
  public abstract String pluginName(); // The plugin that this instance class is needed for.

  public abstract String instanceClassName(); // The name of the required instance class.

  // Soy source locations where the plugin is used, stored as strings because we read the plugin
  // info from meta-inf files and want to easily the PluginRuntimeInstanceInfo data structure.
  public abstract ImmutableList<String> sourceLocations();

  public static Builder builder() {
    return new AutoValue_PluginRuntimeInstanceInfo.Builder();
  }

  /** Builder for {@link PluginRuntimeInstanceInfo}, with an accumulator for source locations. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPluginName(String value);

    public abstract Builder setInstanceClassName(String value);

    public abstract ImmutableList.Builder<String> sourceLocationsBuilder();

    public abstract PluginRuntimeInstanceInfo build();
  }
}
