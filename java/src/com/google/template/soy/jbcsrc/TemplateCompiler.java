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

import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.CompiledTemplateMetadata.RENDER_METHOD;
import static com.google.template.soy.jbcsrc.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.LocalVariable.createThisVar;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.defn.TemplateParam;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles the top level {@link CompiledTemplate} class for a single template and all related
 * classes.
 */
final class TemplateCompiler {
  private static final String[] INTERFACES = { Type.getInternalName(CompiledTemplate.class) };

  private final FieldRef paramsField;
  private final CompiledTemplateMetadata template;
  private ClassWriter writer;

  TemplateCompiler(CompiledTemplateMetadata template) {
    this.template = template;
    this.paramsField = FieldRef.createFinalField(template.typeInfo(), "params", SoyRecord.class);
  }

  /**
   * Returns the list of classes needed to implement this template.
   *
   * <p>For each template, we generate:
   * <ul>
   *     <li>A {@link CompiledTemplate.Factory}
   *     <li>A {@link CompiledTemplate}
   *     <li>A SoyAbstractCachingProvider subclass for each {@link LetValueNode} and 
   *         {@link CallParamValueNode}
   *     <li>A RenderableThunk subclass for each {@link LetContentNode} and 
   *         {@link CallParamContentNode}
   * </li>
   * 
   * <p>Note:  This will <em>not</em> generate classes for other templates, only the template
   * configured in the constructor.  But it will generate classes that <em>reference</em> the 
   * classes that are generated for other templates.  It is the callers responsibility to ensure
   * that all referenced templates are generated and available in the classloader that ultimately
   * loads the returned classes.
   */
  Iterable<ClassData> compile() {
    List<ClassData> classes = new ArrayList<>();

    // first generate the factory
    // TODO(lukes): don't generate factory if the template is private?  The factories are only
    // useful to instantiate templates for calls from java.  Soy->Soy calls should invoke 
    // constructors directly.
    classes.add(new TemplateFactoryCompiler(template).compile());

    writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V1_7, 
        Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL,
        template.typeInfo().type().getInternalName(), 
        null, // not a generic type
        "java/lang/Object", // superclass
        INTERFACES);
    // TODO(lukes): this associates a file name that will ultimately appear in exceptions as well
    // as be used by debuggers to 'attach source'.  We may want to consider placing our generated
    // classes in packages such that they are in the same classpath relative location as the source
    // files.  More investigation into this needs to be done.
    writer.visitSource(
        template.node().getSourceLocation().getFileName(),
        // No JSR-45 style source maps, instead we write the line numbers in the normal locations.
        null);

    paramsField.defineField(writer);

    generateConstructor();
    generateRenderMethod();
    
    writer.visitEnd();
    classes.add(ClassData.create(template.typeInfo(), writer.toByteArray()));
    return classes;
  }

  private void generateRenderMethod() {
    Label start = new Label();
    Label end = new Label();
    LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    LocalVariable appendableVar = 
        createLocal("appendable", 1, Type.getType(AdvisingAppendable.class), start, end);
    LocalVariable contextVar = 
        createLocal("context", 2, Type.getType(RenderContext.class), start, end);

    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_PUBLIC,
        RENDER_METHOD,
        null /* no generic signature */,
        new Type[] { Type.getType(IOException.class) },
        writer);
    ga.mark(start);
    VariableSet variables = new VariableSet(template.typeInfo(), thisVar, RENDER_METHOD);
    SoyNodeCompiler nodeCompiler = new SoyNodeCompiler(
        variables,
        appendableVar,
        contextVar,
        new ExprCompiler());
    Statement statement = nodeCompiler.compile(template.node());
    statement.gen(ga);
    // TODO(lukes): add detach/reattach, all this does is hardcode it to
    // 'return RenderResult.done();'
    MethodRef.RENDER_RESULT_DONE.invoke().gen(ga);
    ga.mark(end);
    ga.returnValue();
    thisVar.tableEntry(ga);
    appendableVar.tableEntry(ga);
    contextVar.tableEntry(ga);
    variables.generateTableEntries(ga);
    ga.endMethod();
    variables.defineFields(writer);
  }

  /** 
   * Generate a public constructor that assigns our final field and checks for missing required 
   * params.
   * 
   * <p>This constructor is called by the generate factory classes.
   */
  private void generateConstructor() {
    Label start = new Label();
    Label end = new Label();
    LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    LocalVariable paramsVar = createLocal("params", 1, Type.getType(SoyRecord.class), start, end);
    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_PUBLIC, 
        CompiledTemplateMetadata.GENERATED_CONSTRUCTOR, 
        null, // no generic signature
        null, // no checked exception
        writer);
    ga.mark(start);
    // call super()
    thisVar.gen(ga);
    ga.invokeConstructor(Type.getType(Object.class), BytecodeUtils.NULLARY_INIT);

    // now we need to check that all our required parameters are present
    // TODO(lukes): this would be an obvious place to perform type checking and even aggressively
    // unpacking the SoyRecord into fields to save on repeated hash oriented lookups into the
    // SoyRecord.  Wait until we have a more fleshed out implementation before experimenting with
    // this idea.
    for (TemplateParam param : template.node().getAllParams()) {
      if (!param.isInjected() && param.isRequired()) {
        // In Tofu, a missing param is defaulted to 'null' and then if it is required and not
        // nullable, it will fail a strict type check... leading to much confusion notably on the
        // difference between optional params and required nullable params.  For now, i will enforce
        // that required means you have to pass it.
        MethodRef.RUNTIME_CHECK_REQUIRED_PARAM.invoke(
            paramsVar, constant(param.name())).gen(ga);
      }
    }
    // this.params = params;
    paramsField.putInstanceField(thisVar, paramsVar).gen(ga);
    ga.visitInsn(Opcodes.RETURN);
    ga.visitLabel(end);
    thisVar.tableEntry(ga);
    paramsVar.tableEntry(ga);
    ga.endMethod();
  }

  // TODO(lukes): support these expressions, most likely by extracting sufficient data structures 
  // such that they can be implemented directly in ExpressionCompiler.
  private static final class ExprCompiler extends ExpressionCompiler {
    @Override protected SoyExpression visitVarRefNode(VarRefNode node) {
      throw new UnsupportedOperationException();
    }

    @Override protected SoyExpression visitFieldAccessNode(FieldAccessNode node) {
      throw new UnsupportedOperationException();
    }

    @Override protected SoyExpression visitItemAccessNode(ItemAccessNode node) {
      throw new UnsupportedOperationException();
    }

    @Override protected SoyExpression visitFunctionNode(FunctionNode node) {
      throw new UnsupportedOperationException();
    }
  }
}
