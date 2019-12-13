/*
 * Copyright 2018 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

/** Constructs {@link SoySauce} implementations. */
public final class SoySauceBuilder {
  private ImmutableList<SoyFunction> userFunctions = ImmutableList.of();
  private ImmutableList<SoyPrintDirective> userDirectives = ImmutableList.of();
  private ImmutableMap<String, Supplier<Object>> userPluginInstances = ImmutableMap.of();
  private SoyScopedData scopedData;
  private ClassLoader loader;

  public SoySauceBuilder() {}

  /**
   * Sets the plugin instance factories, to be used when constructing the SoySauce.
   *
   * <p>These are used to supply the runtime instances needed by SoyJavaSourceFunction
   * implementations which use the {@code callInstanceMethod} API.
   */
  public SoySauceBuilder withPluginInstances(Map<String, Supplier<Object>> pluginInstances) {
    this.userPluginInstances = ImmutableMap.copyOf(pluginInstances);
    return this;
  }

  /**
   * Sets the {@link ClassLoader}, to be used when loading generated soy classes.
   *
   * <p>In most cases there is no need to use this method, and a default classloader (the one that
   * loaded this class) will be used. The only use case is when generated templates can not be
   * located through the standard java binary classpath, so a special classloader can be set to
   * allow the soy framework to find generated classes.
   */
  public SoySauceBuilder withClassLoader(ClassLoader loader) {
    this.loader = loader;
    return this;
  }

  /** Sets the user functions. */
  SoySauceBuilder withFunctions(Iterable<? extends SoyFunction> userFunctions) {
    this.userFunctions = InternalPlugins.filterDuplicateFunctions(userFunctions);
    return this;
  }

  /**
   * Sets user directives. Not exposed externally because internal directives should be enough, and
   * additional functionality can be built as SoySourceFunctions.
   */
  SoySauceBuilder withDirectives(Iterable<? extends SoyPrintDirective> userDirectives) {
    this.userDirectives = InternalPlugins.filterDuplicateDirectives(userDirectives);
    return this;
  }

  /** Sets the scope. Only useful with PrecompiledSoyModule, which has a pre-built scope. */
  SoySauceBuilder withScope(SoyScopedData scope) {
    this.scopedData = scope;
    return this;
  }

  /** Creates a SoySauce. */
  public SoySauce build() {
    if (scopedData == null) {
      scopedData = new SoySimpleScope();
    }
    if (loader == null) {
      loader = SoySauceBuilder.class.getClassLoader();
    }
    return new SoySauceImpl(
        new CompiledTemplates(readDelTemplatesFromMetaInf(loader), loader),
        scopedData.enterable(),
        userFunctions, // We don't need internal functions because they only matter at compile time
        ImmutableList.<SoyPrintDirective>builder()
            // but internal directives are still required at render time.
            // in order to handle escaping logging function invocations.
            .addAll(InternalPlugins.internalDirectives(scopedData))
            .addAll(userDirectives)
            .build(),
        userPluginInstances);
  }

  /** Walks all resources with the META_INF_DELTEMPLATE_PATH and collects the deltemplates. */
  private static ImmutableSet<String> readDelTemplatesFromMetaInf(ClassLoader loader) {
    try {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      Enumeration<URL> resources = loader.getResources(Names.META_INF_DELTEMPLATE_PATH);
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        try (InputStream in = url.openStream()) {
          BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
          for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            builder.add(line);
          }
        }
      }
      return builder.build();
    } catch (IOException iox) {
      throw new RuntimeException("Unable to read deltemplate listing", iox);
    }
  }
}
