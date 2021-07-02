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
package com.google.template.soy.plugin;

import com.google.template.soy.plugin.java.internal.CompiledJarsPluginSignatureReader;
import java.util.List;
import java.util.function.Consumer;

/** Method checker for local development purposes. Only uses reflection to get access to methods. */
public final class ReflectiveMethodChecker implements MethodChecker {
  @Override
  public boolean hasMethod(
      String className,
      String methodName,
      String returnType,
      List<String> arguments,
      boolean inInterface,
      Consumer<String> errorReporter) {
    return CompiledJarsPluginSignatureReader.hasMatchingMethod(
        CompiledJarsPluginSignatureReader.indexReflectively(className),
        methodName,
        returnType,
        arguments);
  }
}
