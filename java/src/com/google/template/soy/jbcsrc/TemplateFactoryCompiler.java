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

import static com.google.template.soy.jbcsrc.BytecodeUtils.SOY_RECORD_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.defineDefaultConstructor;
import static com.google.template.soy.jbcsrc.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.LocalVariable.createThisVar;
import static com.google.template.soy.jbcsrc.StandardNames.FACTORY_CLASS;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

/**
 * Generates {@link com.google.template.soy.jbcsrc.shared.CompiledTemplate.Factory} implementations.
 *
 * <p>Each factory is incredibly simple, essentially we are generating this class:
 *
 * <pre>{@code
 * public final class FooFactory implements CompiledTemplate.Factory {
 *   public CompiledTemplate create(SoyRecord params) {
 *     return new Foo(params);
 *   }
 * }
 * }</pre>
 *
 * <p>Where the only thing that differs is the name of the template being constructed.
 */
final class TemplateFactoryCompiler {
  private static final TypeInfo FACTORY_TYPE = TypeInfo.create(CompiledTemplate.Factory.class);

  private static final int FACTORY_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;

  private static final Method CREATE_METHOD;

  static {
    try {
      CREATE_METHOD =
          Method.getMethod(
              CompiledTemplate.Factory.class.getDeclaredMethod(
                  "create", SoyRecord.class, SoyRecord.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new AssertionError(e);
    }
  }

  private final CompiledTemplateMetadata template;
  private final InnerClasses innerClasses;

  TemplateFactoryCompiler(CompiledTemplateMetadata currentClass, InnerClasses innerClasses) {
    this.template = currentClass;
    this.innerClasses = innerClasses;
  }

  /** Compiles the factory. */
  void compile() {
    TypeInfo factoryType = innerClasses.registerInnerClass(FACTORY_CLASS, FACTORY_ACCESS);
    SoyClassWriter cw =
        SoyClassWriter.builder(factoryType)
            .implementing(FACTORY_TYPE)
            .setAccess(FACTORY_ACCESS)
            .sourceFileName(template.node().getSourceLocation().getFileName())
            .build();
    innerClasses.registerAsInnerClass(cw, factoryType);

    generateStaticInitializer(cw);
    defineDefaultConstructor(cw, factoryType);
    generateCreateMethod(cw, factoryType);
    cw.visitEnd();
    innerClasses.add(cw.toClassData());
  }

  /**
   * Generates a static initializer that references the CompiledTemplate class to force eager
   * classloading (and thus verification errors). For example,
   *
   * <pre>{@code
   * static {
   *   Class<?> clz = GeneratedTemplateClass.class;
   * }
   * }</pre>
   */
  private void generateStaticInitializer(ClassVisitor cv) {
    if (Flags.DEBUG) {
      new Statement() {
        @Override
        void doGen(CodeBuilder adapter) {
          adapter.pushType(template.typeInfo().type());
          adapter.visitVarInsn(Opcodes.ASTORE, 0);
          adapter.returnValue();
        }
      }.writeMethod(Opcodes.ACC_STATIC, BytecodeUtils.CLASS_INIT, cv);
    }
  }

  /**
   * Writes the {@link CompiledTemplate.Factory#create} method, which directly delegates to the
   * constructor of the {@link #template}.
   */
  private void generateCreateMethod(ClassVisitor cv, TypeInfo factoryType) {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(factoryType, start, end);
    final LocalVariable paramsVar = createLocal("params", 1, SOY_RECORD_TYPE, start, end);
    final LocalVariable ijVar = createLocal("ij", 2, SOY_RECORD_TYPE, start, end);
    final Statement returnTemplate =
        Statement.returnExpression(template.constructor().construct(paramsVar, ijVar));
    new Statement() {
      @Override
      void doGen(CodeBuilder ga) {
        ga.mark(start);
        returnTemplate.gen(ga);
        ga.mark(end);
        thisVar.tableEntry(ga);
        paramsVar.tableEntry(ga);
        ijVar.tableEntry(ga);
      }
    }.writeMethod(Opcodes.ACC_PUBLIC, CREATE_METHOD, cv);
  }
}
