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

package com.google.template.soy.jbcsrc.internal;

import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

/**
 * Tracks a collection of inner classes and aids in name management and calling {@link
 * ClassVisitor#visitInnerClass(String, String, String, int)} to ensure that they are registered
 * correctly.
 */
public final class InnerMethods {
  private final UniqueNameGenerator methodNames = JbcSrcNameGenerators.forMethodNames();
  private final TypeInfo parent;
  private final SoyClassWriter writer;

  public InnerMethods(TypeInfo parent, SoyClassWriter writer) {
    this.parent = parent;
    this.writer = writer;
  }

  /**
   * Registers a method to be generated. returns a new method if the name in question requires
   * mangling.
   *
   * <p>All methods as generated as {@code private static} methods.
   */
  public MethodRef registerLazyClosureMethod(Method method, Statement statement) {
    String name = methodNames.generate(method.getName());
    if (!name.equals(method.getName())) {
      method = new Method(name, method.getDescriptor());
    }
    // For the method to be accessible to our invokedynamic generated subtypes they must not be
    // private
    // TODO(lukes): when jdk21 is available they could be private and our subtypes could become
    // nestmates, see LazyClosureFactory.
    statement.writeMethod(Opcodes.ACC_STATIC, method, writer);
    return MethodRef.createStaticMethod(parent, method, MethodPureness.NON_PURE);
  }
}
