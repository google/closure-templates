/*
 * Copyright 2021 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeProducer;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.FunctionType.Parameter;
import com.google.template.soy.types.SoyType;
import java.util.Optional;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Compiles byte code for {@link ConstNode}s. */
public final class ExternCompiler {

  private final ExternNode extern;
  private final SoyClassWriter writer;

  ExternCompiler(ExternNode extern, SoyClassWriter writer) {
    this.extern = extern;
    this.writer = writer;
  }

  static SoyRuntimeType getConstantRuntimeType(SoyType type) {
    return SoyRuntimeType.getUnboxedType(type).orElseGet(() -> SoyRuntimeType.getBoxedType(type));
  }

  static Method getExternMethod(String symbol, FunctionType type) {
    ImmutableList<Parameter> params = type.getParameters();
    Type[] args = new Type[params.size()];
    for (int i = 0; i < params.size(); i++) {
      args[i] = getConstantRuntimeType(params.get(i).getType()).runtimeType();
    }
    return new Method(symbol, getConstantRuntimeType(type.getReturnType()).runtimeType(), args);
  }

  public void compile() {
    Method method = getExternMethod(extern.getIdentifier().identifier(), extern.getType());
    Optional<JavaImplNode> java = extern.getJavaImpl();

    Label start = new Label();
    Label end = new Label();

    BytecodeProducer body;
    if (java.isPresent()) {
      Type rt = method.getReturnType();
      if (Type.BOOLEAN_TYPE.equals(rt)) {
        body = BytecodeUtils.constant(false);
      } else if (Type.INT_TYPE.equals(rt)) {
        body = BytecodeUtils.constant(0);
      } else if (Type.LONG_TYPE.equals(rt)) {
        body = BytecodeUtils.constant(0L);
      } else if (Type.FLOAT_TYPE.equals(rt) || Type.DOUBLE_TYPE.equals(rt)) {
        body = BytecodeUtils.constant(0D);
      } else {
        body = BytecodeUtils.constantNull(rt);
      }
    } else {
      body = Statement.throwExpression(MethodRef.NO_EXTERN_JAVA_IMPL.invoke());
    }

    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        body.gen(adapter);
        adapter.mark(end);
        adapter.returnValue();
      }
    }.writeMethod(methodAccess(), method, writer);
  }

  private int methodAccess() {
    // Same issue as TemplateCompiler#methodAccess
    return (extern.isExported() ? Opcodes.ACC_PUBLIC : 0) | Opcodes.ACC_STATIC;
  }
}
