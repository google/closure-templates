/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.jbcsrc.api;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.template.soy.jbcsrc.shared.Names;
import java.io.IOException;
import java.util.Collections;

/** Parser for the Soy plugins META_INF file. */
public class SoyPluginRuntimeInstanceMetaParser {

  /**
   * Parses any and all Soy plugin META_INF files created during Soy template compilation to return
   * the {@link PluginRuntimeInstanceInfo} of every Soy plugin transitively linked into the Jar the
   * parser is called from.
   */
  public static ImmutableList<PluginRuntimeInstanceInfo> parseFromMetaInf() {
    try {
      return PluginRuntimeInstanceInfo.deserialize(
          Collections.list(
                  SoyPluginRuntimeInstanceMetaParser.class
                      .getClassLoader()
                      .getResources(Names.META_INF_PLUGIN_PATH))
              .stream()
              .map(Resources::asByteSource)
              .reduce(ByteSource.empty(), ByteSource::concat));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read Soy plugin META_INF file.", e);
    }
  }

  private SoyPluginRuntimeInstanceMetaParser() {}
}
