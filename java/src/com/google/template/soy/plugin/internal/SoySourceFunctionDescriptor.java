/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.plugin.internal;

import com.google.auto.value.AutoValue;
import com.google.template.soy.plugin.restricted.SoySourceFunction;

/** Encapsulates a SoySourceFunction and information about what Soy plugin defined it. */
@AutoValue
public abstract class SoySourceFunctionDescriptor {

  private static final String UNKNOWN_PLUGIN = "//unknown";
  private static final String INTERNAL_PLUGIN = "//internal";

  public static SoySourceFunctionDescriptor create(
      String pluginTarget, SoySourceFunction soySourceFunction) {
    return new AutoValue_SoySourceFunctionDescriptor(pluginTarget, soySourceFunction);
  }

  public static SoySourceFunctionDescriptor createUnknownPlugin(
      SoySourceFunction soySourceFunction) {
    return new AutoValue_SoySourceFunctionDescriptor(UNKNOWN_PLUGIN, soySourceFunction);
  }

  public static SoySourceFunctionDescriptor createInternal(SoySourceFunction soySourceFunction) {
    return new AutoValue_SoySourceFunctionDescriptor(INTERNAL_PLUGIN, soySourceFunction);
  }

  /** The build target defining the plugin, e.g. //path/pkg:plugins. */
  public abstract String pluginTarget();

  public abstract SoySourceFunction soySourceFunction();

  public boolean isKnownPlugin() {
    return !pluginTarget().equals(UNKNOWN_PLUGIN);
  }

  public boolean isInternal() {
    return pluginTarget().equals(INTERNAL_PLUGIN);
  }
}
