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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ClassLoader} that can load classes from a configured set of {@code byte[]}s. 
 */
final class MemoryClassLoader extends ClassLoader {
  static {
    // Since we only override findClass(), we can call this method to get fine grained locking
    // support with no additional work. Our superclass will lock all calls to findClass with a per
    // class to load lock, so we will never see concurrent loads of a single class. 
    // See http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html
    ClassLoader.registerAsParallelCapable();
  }
  
  static final class Builder {
    private final Map<String, ClassData> generatedClasses = new LinkedHashMap<>();

    Builder addAll(Iterable<ClassData> classes) {
      for (ClassData item : classes) {
        add(item);
      }
      return this;
    }

    Builder add(ClassData classData) {
      ClassData prev = generatedClasses.put(classData.type().className(), classData);
      if (prev != null) {
        throw new IllegalStateException("multiple classes generated named: " + classData.type());
      }
      return this;
    }

    MemoryClassLoader build() {
      return new MemoryClassLoader(generatedClasses);
    }
  }

  /**
   * We store all the classes in this map and rely on normal classloading resolution.
   * 
   * <p>The classloader will request classes via {@link #findClass(String)} as loading proceeds.
   */
  private final ImmutableMap<String, ClassData> classesByName;

  private MemoryClassLoader(Map<String, ClassData> generatedClasses) {
    super(ClassLoader.getSystemClassLoader());
    this.classesByName = ImmutableMap.copyOf(generatedClasses);
  }

  @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
    // replace so we don't hang onto the bytes for no reason
    ClassData classDef = classesByName.get(name);
    if (classDef == null) {
      throw new ClassNotFoundException(name);
    }
    try {
      return super.defineClass(name, classDef.data(), 0, classDef.data().length);
    } catch (Throwable t) {
      // Attach additional information in a suppressed exception to make debugging easier.
      t.addSuppressed(new RuntimeException("Failed to load generated class:\n" + classDef));
      Throwables.propagateIfInstanceOf(t, ClassNotFoundException.class);
      throw Throwables.propagate(t);
    }
  }
  
  @Override protected URL findResource(final String name) {
    if (!name.endsWith(".class")) {
      return null;
    }
    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
    ClassData classDef = classesByName.get(className);
    if (classDef == null) {
      return null;
    }
    return classDef.asUrl();
  }
}
