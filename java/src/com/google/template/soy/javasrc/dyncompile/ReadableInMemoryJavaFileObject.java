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

import com.google.common.base.Charsets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;


/**
 * A java "file" generated from Soy source.
 *
 */
final class ReadableInMemoryJavaFileObject extends InMemoryJavaFileObject {

  private final String content;


  ReadableInMemoryJavaFileObject(String path, String content) {
    super(path);
    this.content = content;
  }


  @Override
  public Kind getKind() {
    return Kind.SOURCE;
  }


  @Override
  public InputStream openInputStream() {
    return new ByteArrayInputStream(("\uFEFF" + content).getBytes(Charsets.UTF_8));
  }


  @Override
  public Reader openReader(boolean ignoreEncodingErrors) {
    return new StringReader(content);
  }


  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return content;
  }


  @Override
  public OutputStream openOutputStream() throws IOException {
    throw new IOException(getPath() + " not writable");
  }


  @Override
  public Writer openWriter() throws IOException {
    throw new IOException(getPath() + " not writable");
  }

}
