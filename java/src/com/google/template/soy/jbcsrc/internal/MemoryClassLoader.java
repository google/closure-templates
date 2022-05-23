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

package com.google.template.soy.jbcsrc.internal;

import com.google.common.collect.ImmutableMap;

// TODO(lukes): move to a testonly package
/** A {@link ClassLoader} that can load classes from a configured set of {@code byte[]}s. */
public final class MemoryClassLoader extends AbstractMemoryClassLoader {
  static {
    // Since we only override findClass(), we can call this method to get fine grained locking
    // support with no additional work. Our superclass will lock all calls to findClass with a per
    // class to load lock, so we will never see concurrent loads of a single class.
    // See http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html
    ClassLoader.registerAsParallelCapable();
  }

  /**
   * We store all the classes in this map and rely on normal classloading resolution.
   *
   * <p>The classloader will request classes via {@link #findClass(String)} as loading proceeds.
   */
  private final ImmutableMap<String, ClassData> classesByName;

  public MemoryClassLoader(Iterable<ClassData> classes) {
    this.classesByName = indexByClassname(classes);
  }

  public MemoryClassLoader(ClassLoader parent, Iterable<ClassData> classes) {
    super(parent);
    this.classesByName = indexByClassname(classes);
  }

  @Override
  protected ClassData getClassData(String name) {
    return classesByName.get(name);
  }

  private static ImmutableMap<String, ClassData> indexByClassname(Iterable<ClassData> classes) {
    ImmutableMap.Builder<String, ClassData> builder = ImmutableMap.builder();
    for (ClassData classData : classes) {
      builder.put(classData.type().className(), classData);
    }
    return builder.build();
  }
}
