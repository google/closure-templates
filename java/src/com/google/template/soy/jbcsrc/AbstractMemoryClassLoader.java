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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import javax.annotation.Nullable;

/**
 * Base class to share code between our custom memory based classloader implementations.
 */
abstract class AbstractMemoryClassLoader extends ClassLoader {
  private static final ProtectionDomain DEFAULT_PROTECTION_DOMAIN;

  static {
    ClassLoader.registerAsParallelCapable();

    DEFAULT_PROTECTION_DOMAIN =
        AccessController.doPrivileged(
            new PrivilegedAction<ProtectionDomain>() {
              @Override
              public ProtectionDomain run() {
                return MemoryClassLoader.class.getProtectionDomain();
              }
            });
  }

  AbstractMemoryClassLoader() {
    // We want our loaded classes to be a child classloader of ours to make sure they have access
    // to the same classes that we do.
    super(AbstractMemoryClassLoader.class.getClassLoader());
  }

  /** Returns a data object for a class with the given name or {@code null} if it doesn't exist. */
  @Nullable
  abstract ClassData getClassData(String name);

  @Override
  protected final Class<?> findClass(String name) throws ClassNotFoundException {
    ClassData classDef = getClassData(name);
    if (classDef == null) {
      throw new ClassNotFoundException(name);
    }
    try {
      return super.defineClass(
          name, classDef.data(), 0, classDef.data().length, DEFAULT_PROTECTION_DOMAIN);
    } catch (Throwable t) {
      // Attach additional information in a suppressed exception to make debugging easier.
      t.addSuppressed(new RuntimeException("Failed to load generated class:\n" + classDef));
      Throwables.propagateIfInstanceOf(t, ClassNotFoundException.class);
      throw Throwables.propagate(t);
    }
  }

  @Override
  protected final URL findResource(final String name) {
    if (!name.endsWith(".class")) {
      return null;
    }
    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
    ClassData classDef = getClassData(className);
    if (classDef == null) {
      return null;
    }
    return classDef.asUrl();
  }
}

