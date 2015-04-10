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

import static com.google.template.soy.jbcsrc.BytecodeUtils.defineDefaultConstructor;
import static com.google.template.soy.jbcsrc.CompiledTemplateMetadata.GENERATED_CONSTRUCTOR;
import static com.google.template.soy.jbcsrc.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.LocalVariable.createThisVar;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Generates {@link com.google.template.soy.jbcsrc.api.CompiledTemplate.Factory} implementations.
 *
 * <p>Each factory is incredibly simple, essentially we are generating this class: <pre>{@code
 *   public final class FooFactory implements CompiledTemplate.Factory {
 *     public CompiledTemplate create(SoyRecord params) {
 *       return new Foo(params);
 *     }
 *   }}</pre>
 *
 * <p>Where the only thing that differs is the name of the template being constructed.
 */
final class TemplateFactoryCompiler {
  private static final String[] INTERFACES =
      { Type.getInternalName(CompiledTemplate.Factory.class) };

  private static final Method CREATE_METHOD;
  static {
    try {
      CREATE_METHOD = Method.getMethod(
          CompiledTemplate.Factory.class.getDeclaredMethod(
              "create", SoyRecord.class, SoyRecord.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new AssertionError(e);
    }
  }

  private final CompiledTemplateMetadata template;

  TemplateFactoryCompiler(CompiledTemplateMetadata currentClass) {
    this.template = currentClass;
  }

  /** Compiles the factory. */
  ClassData compile() {
    ClassWriter cw = new ClassWriter(COMPUTE_FRAMES | COMPUTE_MAXS);

    cw.visit(Opcodes.V1_7,
        Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL,
        template.factory().type().getInternalName(),
        null, // not a generic type
        Type.getInternalName(Object.class), // super class
        INTERFACES);

    generateStaticInitializer(cw);
    defineDefaultConstructor(cw, template.factory());
    generateCreateMethod(cw);
    cw.visitEnd();
    byte[] byteArray = cw.toByteArray();
    return ClassData.create(template.factory(), byteArray);
  }

  /**
   * Generates a static inializer that references the CompiledTemplate class to force eager
   * classloading (and thus verification errors). For example, <pre>{@code
   *   static {
   *     Class<?> clz = GeneratedTemplateClass.class;
   *   }}</pre>
   *
   * <p>TODO(lukes): this is useful for now since it will trigger verification errors during
   * compilation (since we load all factories). But we should consider deleting it when the compiler
   * is more mature since it is likely that servers ship dead templates and there is no point
   * loading them.
   */
  private void generateStaticInitializer(ClassWriter cw) {
    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_STATIC,
        BytecodeUtils.CLASS_INIT,
        null /* no generic signature */,
        null /* no checked exceptions */,
        cw);
    ga.push(template.typeInfo().type());
    ga.visitVarInsn(Opcodes.ASTORE, 0);
    ga.returnValue();
    ga.endMethod();
  }

  /**
   * Writes the {@link CompiledTemplate.Factory#create} method, which directly delegates to the
   * constructor of the {@link #template}.
   */
  private void generateCreateMethod(ClassWriter cw) {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(template.factory(), start, end);
    final LocalVariable paramsVar = 
        createLocal("params", 1, Type.getType(SoyRecord.class), start, end);
    final LocalVariable ijVar = createLocal("ij", 2, Type.getType(SoyRecord.class), start, end);
    Statement constructorBody = new Statement() {
      @Override void doGen(GeneratorAdapter ga) {
        ga.mark(start);
        ga.newInstance(template.typeInfo().type());
        ga.dup();
        paramsVar.gen(ga);
        ijVar.gen(ga);
        ga.invokeConstructor(template.typeInfo().type(), GENERATED_CONSTRUCTOR);
        ga.returnValue();
        ga.mark(end);
        thisVar.tableEntry(ga);
        paramsVar.tableEntry(ga);
        ijVar.tableEntry(ga);
      }
    };
    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_PUBLIC,
        CREATE_METHOD,
        null /* no generic signature */,
        null /* no checked exceptions */,
        cw);
    constructorBody.gen(ga);
    ga.endMethod();
  }
}
