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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * An expression has a {@link #type} and can {@link #gen generate} code to evaluate the expression.
 * 
 * <p>Expressions should be side effect free and also should not <em>consume</em> stack items.
 */
abstract class Expression {
  /**
   * Checks that the given expressions are compatible with the given types.
   */
  static void checkTypes(ImmutableList<Type> types, Expression ...exprs) {
    checkArgument(exprs.length == types.size(), 
        "Supplied the wrong number of parameters. Expected %s, got %s",
        types.size(),
        exprs.length);
    for (int i = 0; i < exprs.length; i++) {
      exprs[i].checkType(types.get(i), "Parameter %s", i);
    }
  }

  /** 
   * Generate code to evaluate the expression.
   *   
   * <p>The generated code satisfies the invariant that the top of the runtime stack will contain a
   * value with this {@link #type} immediately after evaluation of the code. 
   */
  abstract void gen(GeneratorAdapter adapter);
  
  /** The type of the expression. */
  abstract Type type();

  /** 
   * A constant expression is one that does not reference any variables. It may contain an 
   * arbitrarily large amount of logic.
   */
  boolean isConstant() {
    return false;
  }

  final void checkType(Type expected) {
    checkType(expected, "");
  }

  final void checkType(Type expected, String fmt, Object ...args) {
    if (type().equals(expected)) {
      return;
    }
    if (expected.getSort() == type().getSort() && expected.getSort() == Type.OBJECT) {
      // for class types we really need to know type hierarchy information to test for 
      // whether actualType is assignable to expectedType.
      // This test is mostly optimistic so we just assume that they match. The verifier will tell
      // us ultimately if we screw up.
      // TODO(lukes): see if we can do something better here,  special case the SoyValue 
      // hierarchy?
      return;
    }
    String message = String.format(
        "Type mismatch. Expected %s, got %s.",
        expected,
        type());
    if (!fmt.isEmpty()) {
      message = String.format(fmt, args) + ". " + message;
    }
    throw new IllegalArgumentException(message);
  }

  /**
   * Returns a human readable string for the code that this expression generates.
   */
  final String traceExpression() {
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
    return writer.toString();  // N.B. adds a trailing newline
  }

  @Override public String toString() {
    return name() + "<" + type() + ">:\n" + traceExpression();
  }

  /**
   * A simple name for the expression, used as part of {@link #toString()}.
   */
  String name() {
    if (isConstant()) {
      return "ConstantExpression";
    }
    String simpleName = this.getClass().getSimpleName();
    return simpleName.isEmpty() ? "Expression" : simpleName;
  }
}
