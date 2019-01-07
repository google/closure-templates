/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;

/** Defines a strategy for loading plugin classes. */
public interface PluginLoader extends Closeable {
  /** The default implementation just loads directly from a single classloader. */
  public static final class Default implements PluginLoader {
    private final ClassLoader classLoader;
    private final boolean closeable;

    /**
     * Construct a loader using the given URLClassLoader, will close the loader when this object is
     * closed.
     */
    Default(URLClassLoader classLoader) {
      this.classLoader = classLoader;
      this.closeable = true;
    }

    /** Constructs a loader using the default classloader. */
    public Default() {
      this.classLoader = Default.class.getClassLoader();
      this.closeable = false;
    }

    @Override
    public Class<?> loadPlugin(String className) throws ClassNotFoundException {
      return Class.forName(className, /*initialize=*/ true, classLoader);
    }

    @Override
    public void close() throws IOException {
      if (closeable) {
        ((Closeable) classLoader).close();
      }
    }
  }

  Class<?> loadPlugin(String className) throws ClassNotFoundException;
}
