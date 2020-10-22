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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
  private static final char FIELD_DELIMITER = '|';
  private static final Splitter FIELD_SPLITTER = Splitter.on(FIELD_DELIMITER);
  private static final char SOURCE_LOCATION_DELIMITER = ',';
  private static final Joiner SOURCE_LOCATION_JOINER = Joiner.on(SOURCE_LOCATION_DELIMITER);
  private static final Splitter SOURCE_LOCATION_SPLITTER = Splitter.on(SOURCE_LOCATION_DELIMITER);

  /** The name of the Soy plugin. */
  public abstract String pluginName();

  /** The class name of the plugin runtime instance. */
  public abstract String instanceClassName();

  /**
   * Soy source locations where the plugin is used, stored as strings because we read the plugin
   * info from meta-inf files and want to easily the PluginRuntimeInstanceInfo data structure.
   */
  public abstract ImmutableSortedSet<String> sourceLocations();

  public static ByteSource serialize(Iterable<PluginRuntimeInstanceInfo> plugins) {
    StringBuilder builder = new StringBuilder();
    for (PluginRuntimeInstanceInfo plugin : plugins) {
      builder.append(plugin.toMetaInfEntry());
    }
    return CharSource.wrap(builder.toString()).asByteSource(UTF_8);
  }

  static ImmutableList<PluginRuntimeInstanceInfo> deserialize(ByteSource byteSource)
      throws IOException {
    Map<String, PluginRuntimeInstanceInfo> pluginNameToClassNameMap = new LinkedHashMap<>();
    try (InputStream inputStream = byteSource.openStream()) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo = fromMetaInfEntry(line);
        pluginNameToClassNameMap.merge(
            pluginRuntimeInstanceInfo.pluginName(),
            pluginRuntimeInstanceInfo,
            PluginRuntimeInstanceInfo::merge);
      }
    }
    return ImmutableList.copyOf(pluginNameToClassNameMap.values());
  }

  /**
   * Converts this {@code PluginRuntimeInstanceInfo} into an entry written to the Soy plugins
   * META_INF file.
   */
  private String toMetaInfEntry() {
    return pluginName()
        + FIELD_DELIMITER
        + instanceClassName()
        + FIELD_DELIMITER
        + SOURCE_LOCATION_JOINER.join(sourceLocations())
        + "\n";
  }

  /**
   * Converts a entry in the Soy plugins META_INF file back into a {@code
   * PluginRuntimeInstanceInfo}. This is the inverse of {@link #toMetaInfEntry()}.
   */
  private static PluginRuntimeInstanceInfo fromMetaInfEntry(String metaInfEntry) {
    List<String> fields = FIELD_SPLITTER.splitToList(metaInfEntry.trim());
    checkState(
        fields.size() == 3,
        "Entry [%s] is expected to have exactly three fields but found %s fields (%s).",
        metaInfEntry,
        fields.size(),
        fields.toArray());
    return builder()
        .setPluginName(fields.get(0))
        .setInstanceClassName(fields.get(1))
        .addAllSourceLocations(SOURCE_LOCATION_SPLITTER.splitToList(fields.get(2)))
        .build();
  }

  /**
   * Merges two {@code PluginRuntimeInstanceInfo} objects with the same plugin name and instance
   * class name into a single {@code PluginRuntimeInstanceInfo}.
   */
  @VisibleForTesting
  static PluginRuntimeInstanceInfo merge(
      PluginRuntimeInstanceInfo original, PluginRuntimeInstanceInfo other) {
    checkArgument(original.pluginName().equals(other.pluginName()));
    checkArgument(original.instanceClassName().equals(other.instanceClassName()));
    return builder()
        .setPluginName(original.pluginName())
        .setInstanceClassName(original.instanceClassName())
        .addAllSourceLocations(original.sourceLocations())
        .addAllSourceLocations(other.sourceLocations())
        .build();
  }

  public static Builder builder() {
    return new AutoValue_PluginRuntimeInstanceInfo.Builder();
  }

  /** Builder for {@link PluginRuntimeInstanceInfo}, with an accumulator for source locations. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setPluginName(String value);

    public abstract Builder setInstanceClassName(String value);

    /**
     * Builder for source locations the in the Soy template the Soy plugin is used. Note, we are
     * using an {@link ImmutableSortedSet} to ensure the contents of META_INF file is always
     * deterministic.
     */
    abstract ImmutableSortedSet.Builder<String> sourceLocationsBuilder();

    public Builder addAllSourceLocations(Iterable<String> sourceLocations) {
      sourceLocationsBuilder().addAll(sourceLocations);
      return this;
    }

    public Builder addSourceLocation(String sourceLocation) {
      sourceLocationsBuilder().add(sourceLocation);
      return this;
    }

    public abstract PluginRuntimeInstanceInfo build();
  }
}
