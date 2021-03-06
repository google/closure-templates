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

package com.google.template.soy.jbcsrc;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.jbcsrc.internal.AbstractMemoryClassLoader;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** A classloader that can compile templates on demand. */
final class CompilingClassLoader extends AbstractMemoryClassLoader {
  static {
    // See http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html
    ClassLoader.registerAsParallelCapable();
  }

  // Synchronized hashmap is sufficient for our usecase since we are only calling remove(), CHM
  // would just use more memory.
  private final Map<String, ClassData> classesByName = Collections.synchronizedMap(new HashMap<>());

  private final ImmutableMap<SourceFilePath, SoyFileSupplier> filePathsToSuppliers;
  private final ImmutableMap<String, SoyFileNode> javaClassNameToFile;
  private final SoyTypeRegistry typeRegistry;

  CompilingClassLoader(
      SoyFileSetNode fileSet,
      ImmutableMap<SourceFilePath, SoyFileSupplier> filePathsToSuppliers,
      SoyTypeRegistry typeRegistry) {
    Map<String, SoyFileNode> javaClassNameToFile = new LinkedHashMap<>();
    for (SoyFileNode file : fileSet.getChildren()) {
      // TODO(b/180904763):For the vast majority of files all templates share the same class, but
      // there are some exceptions due to this bug.  Remove this loop when that is cleaned up.
      for (TemplateNode template : file.getTemplates()) {
        javaClassNameToFile.put(
            Names.javaClassNameFromSoyTemplateName(template.getTemplateName()), file);
      }
    }
    this.javaClassNameToFile = ImmutableMap.copyOf(javaClassNameToFile);
    this.typeRegistry = typeRegistry;
    this.filePathsToSuppliers = filePathsToSuppliers;
  }

  @Override
  protected ClassData getClassData(String name) {
    // Remove because ClassLoader itself maintains a cache so we don't need it after loading
    ClassData classDef = classesByName.remove(name);
    if (classDef != null) {
      return classDef;
    }
    // We haven't already compiled it (and haven't already loaded it) so try to find the matching
    // template.

    // For each template we compile there is only one 'public' class that could be loaded prior
    // to compiling the template, CompiledTemplate itself.
    if (!name.startsWith(Names.CLASS_PREFIX)) {
      return null;
    }
    SoyFileNode node = javaClassNameToFile.get(name);
    if (node == null) {
      return null;
    }
    ClassData clazzToLoad = null;
    ErrorReporter reporter = ErrorReporter.create(filePathsToSuppliers);
    for (ClassData clazz :
        new SoyFileCompiler(node, new JavaSourceFunctionCompiler(typeRegistry, reporter))
            .compile()) {
      String className = clazz.type().className();
      if (className.equals(name)) {
        clazzToLoad = clazz;
      } else {
        classesByName.put(className, clazz);
      }
    }
    if (reporter.hasErrors()) {
      // if we are reporting errors we should report warnings at the same time.
      Iterable<SoyError> errors = Iterables.concat(reporter.getErrors(), reporter.getWarnings());
      throw new SoyCompilationException(errors);
    }
    return clazzToLoad;
  }
}
