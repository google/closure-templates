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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;


/**
 * An in-memory file that stores the result of compiling Java source to Java bytecode.
 *
 */
final class WritableInMemoryJavaFileObject extends InMemoryJavaFileObject {

  private final ByteArrayOutputStream content = new ByteArrayOutputStream();


  WritableInMemoryJavaFileObject(String path) {
    super(path);
  }


  public byte[] getByteContent() {
    return content.toByteArray();
  }


  @Override
  public Kind getKind() {
    return Kind.CLASS;
  }


  @Override
  public InputStream openInputStream() {
    // Not implementing pipes.
    return new ByteArrayInputStream(content.toByteArray());
  }


  @Override
  public Reader openReader(boolean ignoreEncodingErrors) {
    return new InputStreamReader(openInputStream(), Charsets.UTF_8);
  }


  @Override
  public OutputStream openOutputStream() {
    return content;
  }


  @Override
  public Writer openWriter() {
    return new OutputStreamWriter(content, Charsets.UTF_8);
  }

}
