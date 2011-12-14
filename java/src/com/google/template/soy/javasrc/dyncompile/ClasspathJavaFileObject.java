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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;


/**
 * A compiled java class file whose content is retrieved via the same {@link ClassLoader} that
 * loaded this class.
 * <p>
 * Since this is loaded from the class path, it is read-only.
 *
 */
final class ClasspathJavaFileObject implements JavaFileObject {

  /** A resource path that can be loaded via {@link ClassLoader#getResource}. */
  private final String resourcePath;


  /**
   * @param resourcePath A resource path that can be loaded via {@link ClassLoader#getResource}.
   */
  ClasspathJavaFileObject(String resourcePath) {
    this.resourcePath = resourcePath;
  }


  /**
   * Returns a resource path that can be loaded via {@link ClassLoader#getResource}.
   */
  public String getResourcePath() {
    return resourcePath;
  }


  @Override
  public URI toUri() {
    try {
      return ClasspathJavaFileObject.class.getResource(resourcePath).toURI();
    } catch (URISyntaxException ex) {
      throw new AssertionError(ex);  // Should not fail to convert a URL to a URI.
    }
  }


  @Override
  public String getName() {
    return resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
  }


  @Override
  public InputStream openInputStream() throws IOException {
    InputStream in = ClasspathJavaFileObject.class.getResourceAsStream(resourcePath);
    if (in == null) { throw new FileNotFoundException(resourcePath); }
    return in;
  }


  @Override
  public OutputStream openOutputStream() throws IOException {
    throw new IOException("Not writable");
  }


  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    throw new IOException("Binary");
  }


  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    throw new IOException("Binary");
  }


  @Override
  public Writer openWriter() throws IOException {
    throw new IOException("Not writable");
  }


  @Override
  public long getLastModified() {
    return InMemoryJavaFileObject.IN_THE_YEAR_2000_MILLIS;
  }


  @Override
  public boolean delete() {
    return false;  // Class-path is read only.
  }


  @Override
  public Kind getKind() {
    return Kind.CLASS;
  }


  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    if (kind != getKind()) {
      return false;
    }
    String name = getName();
    int dotIndex = name.indexOf('.');
    String nameWithoutExtension = dotIndex >= 0 ? name.substring(0, dotIndex) : name;
    // No need to normalize names and identifiers since this is all in-memory.
    return kind == getKind() && simpleName.equals(nameWithoutExtension);
  }


  @Override
  public NestingKind getNestingKind() {
    return null;  // null means don't know.
  }


  @Override
  public Modifier getAccessLevel() {
    return null;  // null means don't know.
  }


  @Override
  public String toString() {
    return resourcePath;
  }

}
