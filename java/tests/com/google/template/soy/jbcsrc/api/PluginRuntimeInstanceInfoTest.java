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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PluginRuntimeInstanceInfo}. */
@RunWith(JUnit4.class)
public class PluginRuntimeInstanceInfoTest {

  @Test
  public void serialize_multipleSourceLocations() throws IOException {
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/shelf/top_shelf.soy:2:4-2:11")
            .addSourceLocation("kitchen/stove/front_burner.soy:5:4-4:11")
            .addSourceLocation("kitchen/stove/front_burner.soy:7:4-4:11")
            .build();

    assertThat(
            PluginRuntimeInstanceInfo.deserialize(
                PluginRuntimeInstanceInfo.serialize(ImmutableList.of(pluginRuntimeInstanceInfo))))
        .containsExactly(pluginRuntimeInstanceInfo);
  }

  @Test
  public void serialize_oneSourceLocation() throws IOException {
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("isHotdog")
            .setInstanceClassName("com.google.template.HotdogClassifier")
            .addSourceLocation("hotdog/view/landing.soy:2:4-2:11")
            .build();

    assertThat(
            PluginRuntimeInstanceInfo.deserialize(
                PluginRuntimeInstanceInfo.serialize(ImmutableList.of(pluginRuntimeInstanceInfo))))
        .containsExactly(pluginRuntimeInstanceInfo);
  }

  @Test
  public void deserialize_emptyByteSources_returnsEmptyList() throws IOException {
    assertThat(PluginRuntimeInstanceInfo.deserialize(ByteSource.empty())).isEmpty();
  }

  @Test
  public void deserialize_multipleByteSources_mergeSourceLocations() throws IOException {
    PluginRuntimeInstanceInfo hippo =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toHippo")
            .setInstanceClassName("com.google.template.Hippo")
            .addSourceLocation("jungle/water/shallow_water.soy:5:4-4:11")
            .build();
    PluginRuntimeInstanceInfo toto =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toToto")
            .setInstanceClassName("com.google.template.Toto")
            .addSourceLocation("rain/in/africa.soy:5:4-4:11")
            .build();

    ImmutableList<PluginRuntimeInstanceInfo> actual =
        PluginRuntimeInstanceInfo.deserialize(
            ByteSource.concat(
                toByteSource(
                    hippo,
                    PluginRuntimeInstanceInfo.builder()
                        .setPluginName("toDog")
                        .setInstanceClassName("com.google.template.Dog")
                        .addSourceLocation("house/front/yard.soy:5:4-4:11")
                        .build()),
                toByteSource(
                    toto,
                    PluginRuntimeInstanceInfo.builder()
                        .setPluginName("toDog")
                        .setInstanceClassName("com.google.template.Dog")
                        .addSourceLocation("kitchen/table/begging_for_food.soy:5:4-4:11")
                        .build())));

    assertThat(actual)
        .containsExactly(
            hippo,
            toto,
            PluginRuntimeInstanceInfo.builder()
                .setPluginName("toDog")
                .setInstanceClassName("com.google.template.Dog")
                .addSourceLocation("house/front/yard.soy:5:4-4:11")
                .addSourceLocation("kitchen/table/begging_for_food.soy:5:4-4:11")
                .build());
  }

  @Test
  public void merge_combineSourceLocations() {
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo1 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/stove/front_burner.soy:5:4-4:11")
            .addSourceLocation("kitchen/stove/front_burner.soy:7:4-4:11")
            .build();
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo2 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/shelf/top_shelf.soy:2:4-2:11")
            .build();

    assertThat(
            PluginRuntimeInstanceInfo.merge(pluginRuntimeInstanceInfo1, pluginRuntimeInstanceInfo2))
        .isEqualTo(
            PluginRuntimeInstanceInfo.builder()
                .setPluginName("toTeapot")
                .setInstanceClassName("com.google.template.Teapot")
                .addSourceLocation("kitchen/shelf/top_shelf.soy:2:4-2:11")
                .addSourceLocation("kitchen/stove/front_burner.soy:5:4-4:11")
                .addSourceLocation("kitchen/stove/front_burner.soy:7:4-4:11")
                .build());
  }

  @Test
  public void merge_conflictingPluginName_isException() {
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo1 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/stove/front_burner.soy:5:4-4:11")
            .addSourceLocation("kitchen/stove/front_burner.soy:7:4-4:11")
            .build();
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo2 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toKettle")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/shelf/top_shelf.soy:2:4-2:11")
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PluginRuntimeInstanceInfo.merge(
                pluginRuntimeInstanceInfo1, pluginRuntimeInstanceInfo2));
  }

  @Test
  public void merge_conflictingInstanceClassName_isException() {
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo1 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Teapot")
            .addSourceLocation("kitchen/stove/front_burner.soy:5:4-4:11")
            .addSourceLocation("kitchen/stove/front_burner.soy:7:4-4:11")
            .build();
    PluginRuntimeInstanceInfo pluginRuntimeInstanceInfo2 =
        PluginRuntimeInstanceInfo.builder()
            .setPluginName("toTeapot")
            .setInstanceClassName("com.google.template.Kettle")
            .addSourceLocation("kitchen/shelf/top_shelf.soy:2:4-2:11")
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PluginRuntimeInstanceInfo.merge(
                pluginRuntimeInstanceInfo1, pluginRuntimeInstanceInfo2));
  }

  private static ByteSource toByteSource(PluginRuntimeInstanceInfo... plugins) {
    return PluginRuntimeInstanceInfo.serialize(Arrays.asList(plugins));
  }
}
