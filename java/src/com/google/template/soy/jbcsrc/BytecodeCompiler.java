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

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.io.ByteSink;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

/**
 * The entry point to the {@code jbcsrc} compiler.
 */
public final class BytecodeCompiler {
  private static final Logger logger = Logger.getLogger(BytecodeCompiler.class.getName());
  /**
   * Compiles all the templates in the given registry.
   *
   * @param registry All the templates to compile
   * @param developmentMode Whether or not we are in development mode.  In development mode we 
   *    compile classes lazily
   * @param reporter The error reporter
   * @return CompiledTemplates or {@code absent()} if compilation fails, in which case errors will
   *     have been reported to the error reporter.
   */
  public static Optional<CompiledTemplates> compile(
      final TemplateRegistry registry, boolean developmentMode, ErrorReporter reporter) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    ErrorReporter.Checkpoint checkpoint = reporter.checkpoint();
    checkForUnsupportedFeatures(registry, reporter);
    if (reporter.errorsSince(checkpoint)) {
      return Optional.absent();
    }
    CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
    if (developmentMode) {
      CompiledTemplates templates =
          new CompiledTemplates(
              compilerRegistry.getDelegateTemplateNames(),
              new CompilingClassLoader(compilerRegistry));
      // TODO(lukes): consider spawning a thread to load all the generated classes in the background
      return Optional.of(templates);
    }
    // TODO(lukes): once most internal users have moved to precompilation eliminate this and just
    // use the 'developmentMode' path above.  This hybrid only makes sense for production services
    // that are doing runtime compilation.  Hopefully, this will become an anomaly.
    List<ClassData> classes =
        compileTemplates(
            compilerRegistry,
            reporter,
            new CompilerListener<List<ClassData>>() {
              final List<ClassData> compiledClasses = new ArrayList<>();
              int numBytes = 0;
              int numFields = 0;
              int numDetachStates = 0;

              @Override
              public void onCompile(ClassData clazz) {
                numBytes += clazz.data().length;
                numFields += clazz.numberOfFields();
                numDetachStates += clazz.numberOfDetachStates();
                compiledClasses.add(clazz);
              }

              @Override
              public List<ClassData> getResult() {
                logger.log(
                    Level.INFO,
                    "Compilation took {0}\n"
                        + "     templates: {1}\n"
                        + "       classes: {2}\n"
                        + "         bytes: {3}\n"
                        + "        fields: {4}\n"
                        + "  detachStates: {5}",
                        new Object[] {
                            stopwatch.toString(),
                            registry.getAllTemplates().size(),
                            compiledClasses.size(),
                            numBytes,
                            numFields,
                            numDetachStates
                    });
                return compiledClasses;
              }
            });
    if (reporter.errorsSince(checkpoint)) {
      return Optional.absent();
    }
    CompiledTemplates templates =
        new CompiledTemplates(
            compilerRegistry.getDelegateTemplateNames(), new MemoryClassLoader(classes));
    stopwatch.reset().start();
    templates.loadAll(compilerRegistry.getTemplateNames());
    logger.log(Level.INFO, "Loaded all classes in {0}", stopwatch);
    return Optional.of(templates);
  }

  /**
   * Compiles all the templates in the given registry to a jar file written to the given output
   * stream.
   *
   * <p>If errors are encountered, the error reporter will be updated and we will return.  The
   * contents of any data written to the sink at that point are undefined.
   *
   * @param registry All the templates to compile
   * @param reporter The error reporter
   * @param sink The output sink to write the JAR to.
   */
  public static void compileToJar(TemplateRegistry registry, ErrorReporter reporter, ByteSink sink)
      throws IOException {
    ErrorReporter.Checkpoint checkpoint = reporter.checkpoint();
    checkForUnsupportedFeatures(registry, reporter);
    if (reporter.errorsSince(checkpoint)) {
      return;
    }
    CompiledTemplateRegistry compilerRegistry = new CompiledTemplateRegistry(registry);
    if (reporter.errorsSince(checkpoint)) {
      return;
    }
    Manifest mf = new Manifest();
    mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mf.getMainAttributes().put(new Attributes.Name("Created-By"), "soy");
    try (OutputStream stream = sink.openStream();
        JarOutputStream jarOutput = new JarOutputStream(stream, mf)) {
      compileTemplates(
          compilerRegistry,
          reporter,
          new CompilerListener<Void>() {
            @Override
            void onCompile(ClassData clazz) throws IOException {
              jarOutput.putNextEntry(new ZipEntry(clazz.type().internalName() + ".class"));
              jarOutput.write(clazz.data());
              jarOutput.closeEntry();
            }
          });
    }
  }

  private static void checkForUnsupportedFeatures(TemplateRegistry registry,
      ErrorReporter errorReporter) {
    UnsupportedFeatureReporter reporter = new UnsupportedFeatureReporter(errorReporter);
    for (TemplateNode node : registry.getAllTemplates()) {
      reporter.check(node);
    }
  }

  private abstract static class CompilerListener<T> {
    abstract void onCompile(ClassData newClass) throws Exception;

    T getResult() {
      return null;
    }
  }

  private static <T> T compileTemplates(
      CompiledTemplateRegistry registry,
      ErrorReporter errorReporter,
      CompilerListener<T> listener) {
    for (String name : registry.getTemplateNames()) {
      CompiledTemplateMetadata classInfo = registry.getTemplateInfoByTemplateName(name);
      if (classInfo.node().getParent().getSoyFileKind() != SoyFileKind.SRC) {
        continue; // only generate classes for sources
      }
      try {
        TemplateCompiler templateCompiler = new TemplateCompiler(registry, classInfo);
        for (ClassData clazz : templateCompiler.compile()) {
          if (Flags.DEBUG) {
            clazz.checkClass();
          }
          listener.onCompile(clazz);
        }
      // Report unexpected errors and keep going to try to collect more.
      } catch (UnexpectedCompilerFailureException e) {
        errorReporter.report(e.getOriginalLocation(), 
            SoyError.of("Unexpected error while compiling template: ''{0}''\nSoy Stack:\n{1}"
                + "\nCompiler Stack:{2}"), 
            name,
            e.printSoyStack(),
            Throwables.getStackTraceAsString(e));

      } catch (Throwable t) {
        errorReporter.report(
            classInfo.node().getSourceLocation(),
            SoyError.of("Unexpected error while compiling template: ''{0}''\n{1}"),
            name,
            Throwables.getStackTraceAsString(t));
      }
    }
    return listener.getResult();
  }

  private BytecodeCompiler() {}
}
