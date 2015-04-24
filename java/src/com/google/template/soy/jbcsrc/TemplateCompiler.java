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

import static com.google.template.soy.jbcsrc.CompiledTemplateMetadata.GENERATED_CONSTRUCTOR;
import static com.google.template.soy.jbcsrc.CompiledTemplateMetadata.RENDER_METHOD;
import static com.google.template.soy.jbcsrc.FieldRef.createField;
import static com.google.template.soy.jbcsrc.FieldRef.createFinalField;
import static com.google.template.soy.jbcsrc.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.LocalVariable.createThisVar;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.Expression.SimpleExpression;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

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
  private final FieldRef ijField;
  private final FieldRef stateField;
  private final UniqueNameGenerator fieldNames = UniqueNameGenerator.forFieldNames();
  private final ImmutableMap<String, FieldRef> paramFields;
  private final CompiledTemplateMetadata template;
  private final InnerClasses innerClasses;
  private ClassVisitor writer;

  TemplateCompiler(CompiledTemplateMetadata template) {
    this.template = template;
    this.paramsField = createFinalField(template.typeInfo(), "$params", SoyRecord.class);
    this.ijField = createFinalField(template.typeInfo(), "$ij", SoyRecord.class);
    this.stateField = createField(template.typeInfo(), "$state", Type.INT_TYPE);
    this.innerClasses = new InnerClasses(template.typeInfo());
    fieldNames.claimName("$params");
    fieldNames.claimName("$ij");
    fieldNames.claimName("$state");
    ImmutableMap.Builder<String, FieldRef> builder = ImmutableMap.builder();
    for (TemplateParam param : template.node().getAllParams()) {
      String name = param.name();
      fieldNames.claimName(name);
      builder.put(name, createFinalField(template.typeInfo(), name, SoyValueProvider.class));
    }
    this.paramFields = builder.build();
  }

  /**
   * Returns the list of classes needed to implement this template.
   *
   * <p>For each template, we generate:
   * <ul>
   *     <li>A {@link com.google.template.soy.jbcsrc.api.CompiledTemplate.Factory}
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
    new TemplateFactoryCompiler(template, innerClasses).compile();

    ClassWriter classWriter = new ClassWriter(COMPUTE_FRAMES | COMPUTE_MAXS);
    writer = new CheckClassAdapter(classWriter, false);
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
    
    stateField.defineField(writer);
    paramsField.defineField(writer);
    ijField.defineField(writer);
    for (FieldRef field : paramFields.values()) {
      field.defineField(writer);
    }

    generateConstructor();
    generateRenderMethod();
    
    innerClasses.registerAllInnerClasses(writer);
    writer.visitEnd();

    classes.add(ClassData.create(template.typeInfo(), classWriter.toByteArray()));
    classes.addAll(innerClasses.getInnerClassData());
    return classes;
  }

  private void generateRenderMethod() {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    final LocalVariable appendableVar = 
        createLocal("appendable", 1, Type.getType(AdvisingAppendable.class), start, end);
    final LocalVariable contextVar = 
        createLocal("context", 2, Type.getType(RenderContext.class), start, end);
    final VariableSet variableSet = 
        new VariableSet(fieldNames, template.typeInfo(), thisVar, RENDER_METHOD);
    TemplateBasicNode node = template.node();
    TemplateVariables variables = 
        new TemplateVariables(variableSet, thisVar, contextVar, paramFields);
    final Statement methodBody =
        SoyNodeCompiler.create(
            innerClasses,
            stateField,
            thisVar,
            appendableVar,
            variableSet,
            variables).compile(node);
    final Statement returnDone = Statement.returnExpression(MethodRef.RENDER_RESULT_DONE.invoke());
    Statement fullMethodBody = new Statement() {
      @Override void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        methodBody.gen(adapter);
        adapter.mark(end);
        returnDone.gen(adapter);

        thisVar.tableEntry(adapter);
        appendableVar.tableEntry(adapter);
        contextVar.tableEntry(adapter);
        variableSet.generateTableEntries(adapter);
      }
    };
    fullMethodBody.writeMethod(Opcodes.ACC_PUBLIC, RENDER_METHOD, IOException.class, writer);
    variableSet.defineFields(writer);
  }

  /** 
   * Generate a public constructor that assigns our final field and checks for missing required 
   * params.
   * 
   * <p>This constructor is called by the generate factory classes.
   */
  private void generateConstructor() {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    final LocalVariable paramsVar = 
        createLocal("params", 1, Type.getType(SoyRecord.class), start, end);
    final LocalVariable ijVar = createLocal("ij", 2, Type.getType(SoyRecord.class), start, end);
    final List<Statement> assignments = new ArrayList<>();
    assignments.add(paramsField.putInstanceField(thisVar, paramsVar));
    assignments.add(ijField.putInstanceField(thisVar, ijVar));
    for (final TemplateParam param : template.node().getAllParams()) {
      Expression paramProvider = getAndCheckParam(paramsVar, ijVar, param);
      assignments.add(paramFields.get(param.name()).putInstanceField(thisVar, paramProvider));
    }
    Statement constructorBody = new Statement() {
      @Override void doGen(CodeBuilder ga) {
        ga.mark(start);
        // call super()
        thisVar.gen(ga);
        ga.invokeConstructor(Type.getType(Object.class), BytecodeUtils.NULLARY_INIT);
        for (Statement assignment : assignments) {
          assignment.gen(ga);
        }
        ga.visitInsn(Opcodes.RETURN);
        ga.visitLabel(end);
        thisVar.tableEntry(ga);
        paramsVar.tableEntry(ga);
        ijVar.tableEntry(ga);
      }
    };
    constructorBody.writeMethod(Opcodes.ACC_PUBLIC, GENERATED_CONSTRUCTOR, writer);
  }

  /**
   * Returns an expression that fetches the given param from the params record or the ij record and
   * enforces the {@link TemplateParam#isRequired()} flag, throwing SoyDataException if a required
   * parameter is missing. 
   */
  private Expression getAndCheckParam(final LocalVariable paramsVar, final LocalVariable ijVar,
      final TemplateParam param) {
    Expression record = param.isInjected() ? ijVar : paramsVar;
    final Expression provider = MethodRef.SOY_RECORD_GET_FIELD_PROVIDER
        .invoke(record, BytecodeUtils.constant(param.name()));
    final Expression nullData = FieldRef.NULL_DATA_INSTANCE.accessor();
    return new SimpleExpression(Type.getType(SoyValueProvider.class), false) {
      @Override void doGen(CodeBuilder adapter) {
        provider.gen(adapter);
        adapter.dup();
        Label nonNull = new Label();
        adapter.ifNonNull(nonNull);
        if (param.isRequired()) {
          adapter.throwException(Type.getType(SoyDataException.class), 
              "Required " + (param.isInjected() ? "@inject" : "@param") + ": '" 
                  + param.name() + "' is undefined.");
        } else {
          // non required params default to null
          adapter.pop();  // pop the extra copy of provider that we dup()'d above
          nullData.gen(adapter);
        }
        adapter.mark(nonNull);
        // At the end there should be a single SoyValueProvider on the stack.
      }
    };
  }

  private static final class TemplateVariables implements VariableLookup {
    private final VariableSet variableSet;
    private final Expression thisRef;
    private final Expression renderContext;
    private final ImmutableMap<String, FieldRef> paramFields;

    TemplateVariables(VariableSet variableSet, Expression thisRef, Expression renderContext,
        ImmutableMap<String, FieldRef> paramFields) {
      this.variableSet = variableSet;
      this.thisRef = thisRef;
      this.renderContext = renderContext;
      this.paramFields = paramFields;
    }

    @Override public Expression getParam(TemplateParam param) {
      return paramFields.get(param.name()).accessor(thisRef);
    }

    @Override public Expression getLocal(LocalVar local) {
      return variableSet.getVariable(local.name()).local();
    }

    @Override public Expression getLocal(SyntheticVarName varName) {
      return variableSet.getVariable(varName).local();
    }

    @Override public Expression getRenderContext() {
      return renderContext;
    }
  }
}
