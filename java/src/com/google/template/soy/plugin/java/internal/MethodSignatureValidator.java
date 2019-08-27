/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.plugin.java.internal;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer.PluginMetadata;
import com.google.template.soy.plugin.java.internal.PluginSignatureReader.ReadMethodData;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import java.io.File;
import java.util.List;

/**
 * Validates that method signatures exist in the runtime jars. This is separate from the normal java
 * plugin validator so that the jbcsrc flow can validate other aspects of the plugin without needing
 * to require reading runtime jars. Plugin method refs are only validated from the
 * SoyPluginValidator entry-point of UberSoyCompiler.
 */
public class MethodSignatureValidator {

  private final ErrorReporter errorReporter;
  private final CompiledJarsPluginSignatureReader sigReader;

  public MethodSignatureValidator(List<File> pluginRuntimeJars, ErrorReporter errorReporter) {
    this.sigReader = new CompiledJarsPluginSignatureReader(pluginRuntimeJars);
    this.errorReporter = errorReporter;
  }

  public void validate(
      String fnName,
      SoyJavaSourceFunction fn,
      SourceLocation sourceLocation,
      boolean includeTriggeredInTemplateMsg) {
    ValidatorErrorReporter reporter =
        new ValidatorErrorReporter(
            errorReporter, fnName, fn.getClass(), sourceLocation, includeTriggeredInTemplateMsg);
    PluginMetadata metadata = PluginAnalyzer.analyze(fn);
    for (MethodSignature ref : metadata.instanceMethodSignatures()) {
      validateMethodSignature(/* expectedInstance= */ true, ref, reporter);
    }
    for (MethodSignature ref : metadata.staticMethodSignatures()) {
      validateMethodSignature(/* expectedInstance= */ false, ref, reporter);
    }
  }

  /** Validates that the method signature (as read from jars) matches the expected signature. */
  private void validateMethodSignature(
      boolean expectedInstance, MethodSignature expectedMethod, ValidatorErrorReporter reporter) {
    ReadMethodData readMethod = sigReader.findMethod(expectedMethod);
    if (readMethod == null) {
      reporter.invalidPluginMethod(expectedMethod);
    } else if (expectedInstance != readMethod.instanceMethod()) {
      reporter.staticMismatch(expectedMethod, expectedInstance);
    } else if (!readMethod.returnType().equals(expectedMethod.returnType().getName())) {
      reporter.wrongPluginMethodReturnType(readMethod.returnType(), expectedMethod);
    } else if (readMethod.classIsInterface() != expectedMethod.inInterface()) {
      reporter.interfaceMismatch(expectedMethod);
    }
  }
}
