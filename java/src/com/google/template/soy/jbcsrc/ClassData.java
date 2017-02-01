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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * A simple tuple of generated class data and type information about the class.
 *
 * <p>Note: not using an @AutoValue since it copies
 */
final class ClassData {
  static ClassData create(TypeInfo type, byte[] b, int numFields, int numDetachStates) {
    return new ClassData(type, b, numFields, numDetachStates);
  }

  private final TypeInfo type;
  private final byte[] data;
  private final int numberOfFields;
  private final int numDetachStates;

  private ClassData(TypeInfo type, byte[] data, int numberOfFields, int numDetachStates) {
    this.type = checkNotNull(type);
    this.data = checkNotNull(data);
    this.numberOfFields = numberOfFields;
    this.numDetachStates = numDetachStates;
  }

  TypeInfo type() {
    return type;
  }

  /** Caution, this returns the underlying array and is mutable. */
  byte[] data() {
    return data;
  }

  int numberOfFields() {
    return numberOfFields;
  }

  int numberOfDetachStates() {
    return numDetachStates;
  }

  /**
   * Runs the {@link CheckClassAdapter} on this class in basic analysis mode.
   *
   * <p>Basic anaylsis mode can flag verification errors that don't depend on knowing complete type
   * information for the classes and methods being called. This is useful for flagging simple
   * generation mistakes (e.g. stack underflows, method return type mismatches, accessing invalid
   * locals). Additionally, the error messages are more useful than what the java verifier normally
   * presents.
   */
  void checkClass() {
    ClassNode cv = new ClassNode();
    new ClassReader(data).accept(new CheckClassAdapter(cv, true /* check data flow */), 0);
    // double check our fields while we are here.
    checkState(type.internalName().equals(cv.name));
    checkState(numberOfFields == cv.fields.size());
  }

  URL asUrl() {
    try {
      // Needs specifyStreamHandler permission.
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<URL>() {
            @Override
            public URL run() throws MalformedURLException {
              return new URL(
                  "mem",
                  "",
                  -1,
                  type.internalName() + ".class",
                  new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                      return new URLConnection(u) {
                        @Override
                        public void connect() {}

                        @Override
                        public InputStream getInputStream() {
                          return new ByteArrayInputStream(data);
                        }
                      };
                    }
                  });
            }
          });
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create stream handler for resource url", e);
    }
  }

  @Override
  public String toString() {
    StringWriter sw = new StringWriter();
    new ClassReader(data)
        .accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw)), 0);
    return sw.toString();
  }
}
