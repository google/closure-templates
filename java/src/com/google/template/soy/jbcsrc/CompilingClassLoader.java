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
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorFormatter;
import com.google.template.soy.error.ErrorFormatterImpl;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.jbcsrc.internal.AbstractMemoryClassLoader;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.soytree.PartialFileSetMetadata;
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
  // Synchronized hashmap is sufficient for our usecase since we are only calling remove(), CHM
  // would just use more memory.
  private final Map<String, ClassData> classesByName = Collections.synchronizedMap(new HashMap<>());

  private final ErrorFormatter errorFormatter;
  private final ImmutableMap<String, SoyFileNode> javaClassNameToFile;
  private final SoyTypeRegistry typeRegistry;
  private final PartialFileSetMetadata fileSetMetadata;

  CompilingClassLoader(
      SoyFileSetNode fileSet,
      ImmutableMap<SourceLogicalPath, SoyFileSupplier> filePathsToSuppliers,
      SoyTypeRegistry typeRegistry,
      PartialFileSetMetadata fileSetMetadata) {
    Map<String, SoyFileNode> javaClassNameToFile = new LinkedHashMap<>();
    for (SoyFileNode file : fileSet.getChildren()) {
      if (NamespaceExemptions.isKnownDuplicateNamespace(file.getNamespace())) {
        // TODO(b/180904763):For the vast majority of files all templates share the same class, but
        // there are some exceptions due to this bug.  Remove this loop when that is cleaned up.
        for (TemplateNode template : file.getTemplates()) {
          javaClassNameToFile.put(
              Names.javaClassNameFromSoyTemplateName(template.getTemplateName()), file);
        }
      } else {
        javaClassNameToFile.put(Names.javaClassNameFromSoyNamespace(file.getNamespace()), file);
      }
    }
    this.errorFormatter = ErrorFormatterImpl.create().withSources(filePathsToSuppliers);
    this.javaClassNameToFile = ImmutableMap.copyOf(javaClassNameToFile);
    this.typeRegistry = typeRegistry;
    this.fileSetMetadata = fileSetMetadata;
  }

  @Override
  protected ClassData getClassData(String name) {
    if (!Names.isGenerated(name)) {
      // this means we couldn't possibly compile it.
      return null;
    }
    // Remove because ClassLoader itself maintains a cache so we don't need it after loading
    ClassData classDef = classesByName.remove(name);
    if (classDef != null) {
      return classDef;
    }
    // We haven't already compiled it (and haven't already loaded it) so try to find the matching
    // template.

    // For each template we compile there is only one 'public' class that could be loaded prior
    // to compiling the template, CompiledTemplate itself.
    SoyFileNode node = javaClassNameToFile.get(name);
    if (node == null) {
      // typo in template name?
      return null;
    }
    ClassData clazzToLoad = null;
    ErrorReporter reporter = ErrorReporter.create();
    for (ClassData clazz :
        new SoyFileCompiler(
                node, new JavaSourceFunctionCompiler(typeRegistry, reporter), fileSetMetadata)
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
      throw new SoyCompilationException(errors, errorFormatter);
    }
    return clazzToLoad;
  }
}
