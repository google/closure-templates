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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A type that can produce bytecode.
 */
abstract class BytecodeProducer {

  /** Writes the bytecode to the adapter. */
  abstract void gen(GeneratorAdapter adapter);

  /**
   * Returns a human readable string for the code that this {@link BytecodeProducer} generates.
   */
  final String trace() {
    // TODO(lukes): textifier has support for custom label names by overriding appendLabel.  
    // Consider trying to make use of (using the Label.info field? adding a custom NamedLabel
    // sub type?)
    Textifier textifier = new Textifier(Opcodes.ASM5) {
      {
        // reset tab sizes.  Since we don't care about formatting class names or method signatures
        // (only code). We only need to set the tab2,tab3 and ltab settings (tab is for class 
        // members).
        this.tab = null;  // trigger an error if used.
        this.tab2 = "  ";  // tab setting for instructions
        this.tab3 = "";  // tab setting for switch cases
        this.ltab = "";  // tab setting for labels
      }
    };
    gen(new GeneratorAdapter(new TraceMethodVisitor(textifier), 0, "trace", "()V"));
    StringWriter writer = new StringWriter();
    textifier.print(new PrintWriter(writer));
    return writer.toString();  // Note textifier always adds a trailing newline
  }
}
