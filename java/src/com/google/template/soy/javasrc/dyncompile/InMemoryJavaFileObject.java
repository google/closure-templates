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

import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;


/**
 * A java file object whose content is stored in-memory.
 *
 * @author Mike Samuel
 */
abstract class InMemoryJavaFileObject implements JavaFileObject {

  /** A modified date to use for fake files to avoid using 0 which might trigger corner cases. */
  static final long IN_THE_YEAR_2000_MILLIS = 31557600000L;


  /** A file path. */
  private final String path;


  InMemoryJavaFileObject(String path) {
    this.path = path;
  }


  /** A path used for this in-memory file. */
  public String getPath() {
    return path;
  }


  @Override
  public URI toUri() {
    // We make up our own protocol.
    // The JavaCompiler gets handles to files using JavaFileManager.listFiles
    // and doesn't try to resolve these URIs.
    try {
      return new URI("memory", null, null, 0, path, null, null);
    } catch (URISyntaxException ex) {
      Throwables.propagate(ex);
      return null;
    }
  }


  @Override
  public String getName() {
    return path.substring(path.lastIndexOf('/') + 1);
  }


  @Override
  public long getLastModified() {
    return IN_THE_YEAR_2000_MILLIS;
  }


  @Override
  public boolean delete() {
    // Deletion of in-memory files always fails.
    return false;
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
    return null;  // This is asking for a hint.  Null means don't know.
  }


  @Override
  public Modifier getAccessLevel() {
    return null;  // This is asking for a hint.  Null means don't know.
  }


  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    return CharStreams.toString(openReader(ignoreEncodingErrors));
  }


  @Override
  public String toString() {
    return path;  // For ease of debugging.
  }

}
