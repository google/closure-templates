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

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A classloader that can compile templates on demand.
 */
final class CompilingClassLoader extends ClassLoader {
  static {
    // See http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html
    ClassLoader.registerAsParallelCapable();
  }

  // Synchronized hashmap is sufficient for our usecase since we are only calling remove(), CHM 
  // would just use more memory.
  private final Map<String, ClassData> classesByName = 
      Collections.synchronizedMap(new HashMap<String, ClassData>());

  private final CompiledTemplateRegistry registry;

  CompilingClassLoader(CompiledTemplateRegistry registry) {
    super(ClassLoader.getSystemClassLoader());
    this.registry = registry;
  }

  @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
    ClassData classDef = classesByName.get(name);
    if (classDef == null) {
      // We haven't already compiled it (and haven't already loaded it) so try to find the matching
      // template.
      classDef = compile(name);
      if (classDef == null) {
        throw new ClassNotFoundException(name);
      }
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
      // We haven't already compiled it (and haven't already loaded it) so try to find the matching
      // template.
      classDef = compile(name);
      if (classDef == null) {
        return null;
      }
    }
    return classDef.asUrl();
  }

  private ClassData compile(String name) {
    // For each template we compile there are only two 'public' classes that could be loaded prior
    // to compiling the template. The CompiledTemplate.Factory class and the CompiledTemplate itself
    boolean isFactory = name.endsWith("$" + StandardNames.FACTORY_CLASS);
    String compiledTemplateName = 
        isFactory 
            ? name.substring(0, name.length() - (StandardNames.FACTORY_CLASS.length() + 1)) 
            : name;
    CompiledTemplateMetadata meta = registry.getTemplateInfoByClassName(compiledTemplateName);
    if (meta == null) {
      return null;
    }
    ClassData clazzToLoad = null;
    for (ClassData clazz : new TemplateCompiler(registry, meta).compile()) {
      String className = clazz.type().className();
      if (className.equals(name)) {
        clazzToLoad = clazz;
      } else {
        classesByName.put(className, clazz);
      }
    }
    return clazzToLoad;
  }
}
