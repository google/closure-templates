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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.SoyJarFileWriter;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.api.PluginRuntimeInstanceInfo;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.restricted.Flags;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateType;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The entry point to the {@code jbcsrc} compiler. */
public final class BytecodeCompiler {

  /**
   * Compiles all the templates in the given registry.
   *
   * @param registry All the templates to compile
   * @param reporter The error reporter
   * @return CompiledTemplates or {@code absent()} if compilation fails, in which case errors will
   *     have been reported to the error reporter.
   */
  public static Optional<CompiledTemplates> compile(
      final TemplateRegistry registry,
      final SoyFileSetNode fileSet,
      ErrorReporter reporter,
      ImmutableMap<SourceFilePath, SoyFileSupplier> filePathsToSuppliers,
      SoyTypeRegistry typeRegistry) {
    ErrorReporter.Checkpoint checkpoint = reporter.checkpoint();
    if (reporter.errorsSince(checkpoint)) {
      return Optional.empty();
    }
    CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
    CompiledTemplates templates =
        new CompiledTemplates(
            /* delTemplateNames=*/ registry.getAllTemplates().stream()
                .filter(
                    template ->
                        template.getTemplateType().getTemplateKind()
                            == TemplateType.TemplateKind.DELTEMPLATE)
                .map(TemplateMetadata::getTemplateName)
                .collect(toImmutableSet()),
            new CompilingClassLoader(
                compilerRegistry, fileSet, filePathsToSuppliers, typeRegistry));
    if (reporter.errorsSince(checkpoint)) {
      return Optional.empty();
    }
    return Optional.of(templates);
  }

  /**
   * Compiles all the templates in the given registry to a jar file written to the given output
   * stream.
   *
   * <p>If errors are encountered, the error reporter will be updated and we will return. The
   * contents of any data written to the sink at that point are undefined.
   *
   * @param registry All the templates to compile
   * @param reporter The error reporter
   * @param sink The output sink to write the JAR to.
   */
  public static void compileToJar(
      TemplateRegistry registry,
      SoyFileSetNode fileSet,
      ErrorReporter reporter,
      SoyTypeRegistry typeRegistry,
      ByteSink sink)
      throws IOException {
    ErrorReporter.Checkpoint checkpoint = reporter.checkpoint();
    if (reporter.errorsSince(checkpoint)) {
      return;
    }
    CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
    if (reporter.errorsSince(checkpoint)) {
      return;
    }
    try (final SoyJarFileWriter writer = new SoyJarFileWriter(sink.openStream())) {
      final Set<String> delTemplates = new TreeSet<>();

      // A map of plugin names -> info about the required instance class (only for plugins that
      // require a runtime class).
      Map<String, PluginRuntimeInstanceInfo.Builder> pluginInstances = new TreeMap<>();

      compileTemplates(
          compilerRegistry,
          fileSet,
          reporter,
          typeRegistry,
          new CompilerListener<Void, IOException>() {
            @Override
            void onCompile(ClassData clazz) throws IOException {
              writer.writeEntry(
                  clazz.type().internalName() + ".class", ByteSource.wrap(clazz.data()));
            }

            @Override
            void onCompileDelTemplate(String name) {
              delTemplates.add(name);
            }

            @Override
            void onFunctionCallFound(FunctionNode fnNode) {
              // For each function call, check if the plugin needs an instance class. If so, add an
              // entry to pluginInstances.
              if (fnNode.getSoyFunction() instanceof SoyJavaSourceFunction) {
                if (!pluginInstances.containsKey(fnNode.getStaticFunctionName())) {
                  Set<String> instances =
                      PluginAnalyzer.analyze(
                              (SoyJavaSourceFunction) fnNode.getSoyFunction(), fnNode.numChildren())
                          .pluginInstanceNames();
                  if (!instances.isEmpty()) {
                    // We guarantee there's either 0 or 1 instances required for the plugin because
                    // we already passed through PluginResolver, which checked this.
                    pluginInstances.put(
                        fnNode.getStaticFunctionName(),
                        PluginRuntimeInstanceInfo.builder()
                            .setPluginName(fnNode.getStaticFunctionName())
                            .setInstanceClassName(Iterables.getOnlyElement(instances)));
                  }
                }

                if (pluginInstances.containsKey(fnNode.getStaticFunctionName())) {
                  // Add the source location to the list of places the function is used.
                  pluginInstances
                      .get(fnNode.getStaticFunctionName())
                      .addSourceLocation(fnNode.getSourceLocation().toString());
                }
              }
            }
          });
      if (!delTemplates.isEmpty()) {
        String delData = Joiner.on('\n').join(delTemplates);
        writer.writeEntry(
            Names.META_INF_DELTEMPLATE_PATH, ByteSource.wrap(delData.getBytes(UTF_8)));
      }

      // If there were required plugin runtime instances, write a meta-inf file containing each
      // plugin's name, it's runtime class name, and the locations in soy where the function is
      // used.
      if (!pluginInstances.isEmpty()) {
        writer.writeEntry(
            Names.META_INF_PLUGIN_PATH,
            PluginRuntimeInstanceInfo.serialize(
                pluginInstances.values().stream()
                    .map(PluginRuntimeInstanceInfo.Builder::build)
                    .collect(Collectors.toList())));
      }
    }
  }

  /**
   * Writes the source files out to a {@code -src.jar}. This places the soy files at the same
   * classpath relative location as their generated classes. Ultimately this can be used by
   * debuggers for source level debugging.
   *
   * <p>It is a little weird that the relative locations of the generated classes are not identical
   * to the input source files. This is due to the disconnect between java packages and soy
   * namespaces. We should consider using the soy namespace directly as a java package in the
   * future.
   *
   * @param soyFileSet All the templates in the current compilation unit
   * @param files The source files by file path
   * @param sink The source to write the jar file
   */
  public static void writeSrcJar(
      SoyFileSetNode soyFileSet, ImmutableMap<SourceFilePath, SoyFileSupplier> files, ByteSink sink)
      throws IOException {
    try (SoyJarFileWriter writer = new SoyJarFileWriter(sink.openStream())) {
      for (SoyFileNode file : soyFileSet.getChildren()) {
        String namespace = file.getNamespace();
        String fileName = file.getFileName();
        writer.writeEntry(
            Names.javaFileName(namespace, fileName),
            files.get(file.getFilePath()).asCharSource().asByteSource(UTF_8));
      }
    }
  }

  private abstract static class CompilerListener<T, E extends Throwable> {
    /** Callback for for class data that was generated. */
    abstract void onCompile(ClassData newClass) throws E;

    /**
     * Callback to notify a deltemplate was compiled.
     *
     * @param name The full name as would be returned by SoyTemplateInfo.getName()
     */
    void onCompileDelTemplate(String name) {}

    /**
     * Callback to notify a template (not a deltemplate) was compiled.
     *
     * @param name The full name as would be returned by SoyTemplateInfo.getName()
     */
    void onCompileTemplate(String name) {}

    /**
     * Callback to notify that a function call was found.
     *
     * @param function The function call node.
     */
    void onFunctionCallFound(FunctionNode function) {}

    T getResult() {
      return null;
    }
  }

  private static <T, E extends Throwable> T compileTemplates(
      CompiledTemplateRegistry registry,
      SoyFileSetNode fileSet,
      ErrorReporter errorReporter,
      SoyTypeRegistry typeRegistry,
      CompilerListener<T, E> listener)
      throws E {
    for (SoyFileNode file : fileSet.getChildren()) {
      for (TemplateNode template : file.getTemplates()) {
        CompiledTemplateMetadata classInfo = registry.getTemplateInfo(template);
        TemplateCompiler templateCompiler =
            new TemplateCompiler(registry, classInfo, template, errorReporter, typeRegistry);
        for (ClassData clazz : templateCompiler.compile()) {
          if (Flags.DEBUG) {
            clazz.checkClass();
          }
          listener.onCompile(clazz);
        }
        if (template instanceof TemplateDelegateNode) {
          listener.onCompileDelTemplate(template.getTemplateName());
        } else {
          listener.onCompileTemplate(template.getTemplateName());
        }

        /** For each function call in the template, trigger the function call listener. */
        for (FunctionNode fnNode : SoyTreeUtils.getAllNodesOfType(template, FunctionNode.class)) {
          listener.onFunctionCallFound(fnNode);
        }
      }
    }
    return listener.getResult();
  }

  private BytecodeCompiler() {}
}
