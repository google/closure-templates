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

import com.google.auto.value.AutoValue;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A simple tuple of generated class data and type information about the class.
 */
@AutoValue abstract class ClassData {
  static ClassData create(TypeInfo type, byte[] b) {
    return new AutoValue_ClassData(type, b);
  }

  abstract TypeInfo type();
  abstract byte[] data();

  /** 
   * Runs the {@link CheckClassAdapter} on this class in basic analysis mode.
   * 
   * <p>Basic anaylsis mode can flag verification errors that don't depend on knowing complete type
   * information for the classes and methods being called.  This is useful for flagging simple
   * generation mistakes (e.g. stack underflows, method return type mismatches, accessing invalid
   * locals).  Additionally, the error messages are more useful than what the java verifier normally
   * presents. 
   */
  void checkClass() {
    new ClassReader(data()).accept(new CheckClassAdapter(new ClassNode(), true), 0);
  }

  @Override public String toString() {
    StringWriter sw = new StringWriter();
    new ClassReader(data())
        .accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw)), 0);
    return sw.toString();
  }
}
