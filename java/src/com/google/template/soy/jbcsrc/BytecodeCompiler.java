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
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSink;
import com.google.common.io.CharStreams;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

/** The entry point to the {@code jbcsrc} compiler. */
public final class BytecodeCompiler {

  private static final Logger logger = Logger.getLogger(BytecodeCompiler.class.getName());

  private static final SoyErrorKind UNEXPECTED_COMPILER_FAILURE =
      SoyErrorKind.of(
          "Unexpected error while compiling template: ''{0}''\n"
              + "Soy Stack:\n{1}\n"
              + "Compiler Stack:\n{2}",
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(
          "Unexpected error while compiling template: ''{0}''\n{1}", StyleAllowance.NO_PUNCTUATION);

  /**
   * Compiles all the templates in the given registry.
   *
   * @param registry All the templates to compile
   * @param developmentMode Whether or not we are in development mode. In development mode we
   *     compile classes lazily
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
   * <p>If errors are encountered, the error reporter will be updated and we will return. The
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
    try (OutputStream stream = sink.openStream();
        JarOutputStream jarOutput = new DeterministicJarOutputStream(stream, getJarManifest())) {
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
   * @param registry All the templates in the current compilation unit
   * @param files The source files by file path
   * @param sink The source to write the jar file
   */
  public static void writeSrcJar(
      TemplateRegistry registry, ImmutableMap<String, SoyFileSupplier> files, ByteSink sink)
      throws IOException {
    Set<SoyFileNode> seenFiles = new HashSet<>();
    try (OutputStream stream = sink.openStream();
        JarOutputStream jarOutput = new DeterministicJarOutputStream(stream, getJarManifest())) {
      for (TemplateNode template : registry.getAllTemplates()) {
        SoyFileNode file = template.getParent();
        if (file.getSoyFileKind() == SoyFileKind.SRC && seenFiles.add(file)) {
          String namespace = file.getNamespace();
          String fileName = file.getFileName();
          jarOutput.putNextEntry(new ZipEntry(Names.javaFileName(namespace, fileName)));
          copyFileToOutput(files.get(file.getFilePath()), jarOutput);
          jarOutput.closeEntry();
        }
      }
    }
  }

  private static final class DeterministicJarOutputStream extends JarOutputStream {
    DeterministicJarOutputStream(OutputStream outputStream, Manifest manifest) throws IOException {
      super(outputStream, manifest);
    }

    @Override
    public void putNextEntry(ZipEntry ze) throws IOException {
      ze.setTime(0); // set an explicit timestamp to zero so we generate deterministic outputs
      super.putNextEntry(ze);
    }
  }

  /** Copies the file to the output stream */
  private static void copyFileToOutput(SoyFileSupplier from, OutputStream to) throws IOException {
    // 'from' contains a Reader which allows streaming reads of characters and 'to' is an
    // OutputStream which allows for streaming writes of bytes.  This disconnect means we need to do
    // some character encoding.  The classic way to do this is to use OutputStreamWriter to wrap the
    // outputStream and apply an encoder.  This introduces some wierdness because OutputStreamWriter
    // can hold on to a few bytes to deal with unmatched surrogate pairs.  So we would need to
    // close/flush it inorder to not corrupt the files.  This is undesirable since the output is
    // actually a JarOutputStream and we are writing multiple files (we would over flush).  So
    // instead we do the naive thing and read the whole file as a string, convert the whole string
    // to a byte array and then write the whole byte array.
    //
    // The real fix is to avoid the Reader and add methods to SoyFileSupplier to give us a
    // ByteSource then we can avoid the error prone decode/encode dance.
    String file;
    try (Reader contents = from.open()) {
      file = CharStreams.toString(contents);
    }
    to.write(file.getBytes(StandardCharsets.UTF_8));
  }

  /** Returns a simple jar manifest. */
  private static Manifest getJarManifest() {
    Manifest mf = new Manifest();
    mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mf.getMainAttributes().put(new Attributes.Name("Created-By"), "soy");
    return mf;
  }

  private static void checkForUnsupportedFeatures(
      TemplateRegistry registry, ErrorReporter errorReporter) {
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
        errorReporter.report(
            e.getOriginalLocation(),
            UNEXPECTED_COMPILER_FAILURE,
            name,
            e.printSoyStack(),
            Throwables.getStackTraceAsString(e));

      } catch (Throwable t) {
        errorReporter.report(
            classInfo.node().getSourceLocation(),
            UNEXPECTED_ERROR,
            name,
            Throwables.getStackTraceAsString(t));
      }
    }
    return listener.getResult();
  }

  private BytecodeCompiler() {}
}
