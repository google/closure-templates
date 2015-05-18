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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;

/**
 * A collection of contextual rendering data.  Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  private final SoyCssRenamingMap cssRenamingMap;
  private final SoyIdRenamingMap xidRenamingMap;
  private final ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap;
  private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
  private final SoyValueConverter converter;

  public RenderContext(SoyCssRenamingMap cssRenamingMap, SoyIdRenamingMap xidRenamingMap,
      ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap,
      ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
      SoyValueConverter converter) {
    this.cssRenamingMap = checkNotNull(cssRenamingMap);
    this.xidRenamingMap = checkNotNull(xidRenamingMap);
    this.soyJavaFunctionsMap = checkNotNull(soyJavaFunctionsMap);
    this.soyJavaDirectivesMap = checkNotNull(soyJavaDirectivesMap);
    this.converter = converter;
  }

  public String renameCssSelector(String selector) {
    String string = cssRenamingMap.get(selector);
    return string == null ? selector : string;
  }

  public String renameXid(String id) {
    String string = xidRenamingMap.get(id);
    return string == null ? id + "_" : string;
  }

  public SoyJavaFunction getFunction(String name) {
    SoyJavaFunction fn = soyJavaFunctionsMap.get(name);
    if (fn == null) {
      throw new IllegalStateException("Failed to find Soy function with name '" + name + "'");
    }
    return fn;
  }

  public SoyJavaPrintDirective getPrintDirective(String name) {
    SoyJavaPrintDirective printDirective = soyJavaDirectivesMap.get(name);
    if (printDirective == null) {
      throw new IllegalStateException(
          "Failed to find Soy ptint directive with name '" + name + "'");
    }
    return printDirective;
  }
}
