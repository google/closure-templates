/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.javasrc.dyncompile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;


/**
 * A java file manager that uses in-memory "files" to store Soy inputs and the classes
 * compiled from them, resolves dependencies via the current {@link ClassLoader}'s
 * resource loading methods, and delegates boot classpath lookup to another file manager.
 *
 * @author Mike Samuel
 */
final class DynamicCompilerJavaFileManager implements JavaFileManager {

  /** Java generated from Soy inputs. */
  private final List<ReadableInMemoryJavaFileObject> inputFiles = Lists.newArrayList();

  /** Classes compiled from inputs.  These are not on the classpath, so are not read by this. */
  private final List<WritableInMemoryJavaFileObject> outputFiles = Lists.newArrayList();

  /** Used to resolve the boot class path.  Usually created by the javax.tools package. */
  private final StandardJavaFileManager standardFileManager;


  /**
   * @param standardFileManager Used to resolve the boot class path.
   *     Usually created by the javax.tools package.
   */
  DynamicCompilerJavaFileManager(StandardJavaFileManager standardFileManager) {
    this.standardFileManager = standardFileManager;
  }


  /**
   * Returns Java files generated from Soy inputs.
   * @see #addInput
   */
  List<ReadableInMemoryJavaFileObject> getInputFiles() {
    return ImmutableList.copyOf(inputFiles);
  }


  /**
   * Returns classes compiled from inputs.
   */
  List<WritableInMemoryJavaFileObject> getOutputFiles() {
    return ImmutableList.copyOf(outputFiles);
  }


  /**
   * Registers an input Java source file.
   */
  void addInput(ReadableInMemoryJavaFileObject inputFile) {
    inputFiles.add(inputFile);
  }


  @Override
  public int isSupportedOption(String option) {
    return -1;  // Not supported.
  }


  @Override
  public ClassLoader getClassLoader(Location location) {
    return null;  // We don't need plugins like annotation processors.
  }


  @Override
  public Iterable<JavaFileObject> list(
      Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse)
      throws IOException {
    ImmutableList.Builder<JavaFileObject> results = ImmutableList.builder();

    // The source path contains only our inputs.
    if (StandardLocation.SOURCE_PATH.equals(location) &&
        kinds.contains(JavaFileObject.Kind.SOURCE) &&
        (SoyToJavaDynamicCompiler.PACKAGE_FOR_COMPILED_SOY.equals(packageName) ||
         (recurse &&
          SoyToJavaDynamicCompiler.PACKAGE_FOR_COMPILED_SOY.startsWith(packageName + ".")))) {
      results.addAll(inputFiles);
    }

    // Resolve dependencies by using the current ClassLoader.
    if (StandardLocation.CLASS_PATH.equals(location) &&
        kinds.contains(JavaFileObject.Kind.CLASS)) {
      for (String classResourcePath : ClasspathUtils.getClassResourcePaths(packageName, recurse)) {
        results.add(new ClasspathJavaFileObject("/" + classResourcePath));
      }
    }

    // Delegate the boot classpath to the file manager we got from the javac wrapper.
    if (StandardLocation.PLATFORM_CLASS_PATH.equals(location)) {
      results.addAll(standardFileManager.list(location, packageName, kinds, recurse));
    }

    return results.build();
  }


  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    // {/src,/out,}/my/pkg/Clazz.* -> my.pkg.Clazz
    String path;
    if (file instanceof InMemoryJavaFileObject) {
      path = ((InMemoryJavaFileObject) file).getPath();
      if (path.startsWith("/src/") || path.startsWith("/out/")) {
        path = path.substring(5);
      }
    } else if (file instanceof ClasspathJavaFileObject) {
      path = ((ClasspathJavaFileObject) file).getResourcePath();
    } else {
      return standardFileManager.inferBinaryName(location, file);
    }
    // Remove extension.
    int dotIndex = path.lastIndexOf('.');
    if (dotIndex >= 0) {
      path = path.substring(0, dotIndex);
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    return path.replace('/', '.');
  }


  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    return a == b || a.toUri().equals(b.toUri());
  }


  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    return false;
  }


  @Override
  public boolean hasLocation(Location location) {
    return StandardLocation.CLASS_OUTPUT.equals(location)  // outputFiles
        || StandardLocation.SOURCE_PATH.equals(location)  // inputFiles
        || StandardLocation.CLASS_PATH.equals(location);  // via ClassLoader
  }


  @Override
  public JavaFileObject getJavaFileForInput(
      Location location, String className, JavaFileObject.Kind kind)
      throws IOException {
    // Only annotation processors need this and we're not running any over compiled Soy.
    throw new IOException();
  }


  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location, String className, JavaFileObject.Kind kind, FileObject sibling)
      throws IOException {
    if (!StandardLocation.CLASS_OUTPUT.equals(location)) {
      throw new FileNotFoundException();
    }
    String outputPath = "/out/" + className.replace('.', '/') + ".class";
    for (InMemoryJavaFileObject aJavaOutputFile : outputFiles) {
      if (aJavaOutputFile.getPath().equals(outputPath)) {
        throw new IOException("Overwriting output file " + outputPath);
      }
    }
    WritableInMemoryJavaFileObject javaFileForOutput =
        new WritableInMemoryJavaFileObject(outputPath);
    outputFiles.add(javaFileForOutput);
    return javaFileForOutput;
  }


  @Override
  public FileObject getFileForInput(Location location, String packageName, String relativeName)
      throws IOException {
    // Moot from super-interface.  JavaCompiler uses getJavaFileForInput instead.
    throw new IOException();
  }


  @Override
  public FileObject getFileForOutput(
      Location location, String packageName, String relativeName, FileObject sibling)
      throws IOException {
    // Moot from super-interface.  JavaCompiler uses getJavaFileForOutput instead.
    throw new IOException();
  }


  @Override
  public void flush() throws IOException {
    standardFileManager.flush();
  }


  @Override
  public void close() throws IOException {
    standardFileManager.close();
  }

}
