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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.DetachState.ExpressionDetacher;
import com.google.template.soy.jbcsrc.Expression.SimpleExpression;
import com.google.template.soy.jbcsrc.VariableSet.Scope;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;
import com.google.template.soy.types.SoyType;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
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
  private ClassVisitor writer;

  TemplateCompiler(CompiledTemplateMetadata template) {
    this.template = template;
    this.paramsField = createFinalField(template.typeInfo(), "$params", SoyRecord.class);
    this.ijField = createFinalField(template.typeInfo(), "$ij", SoyRecord.class);
    this.stateField = createField(template.typeInfo(), "$state", Type.INT_TYPE);
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
    TemplateFactoryCompiler templateFactoryCompiler = new TemplateFactoryCompiler(template);
    classes.add(templateFactoryCompiler.compile());

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

    templateFactoryCompiler.registerInnerClass(writer);
    stateField.defineField(writer);
    paramsField.defineField(writer);
    ijField.defineField(writer);
    for (FieldRef field : paramFields.values()) {
      field.defineField(writer);
    }

    generateConstructor();
    generateRenderMethod();
    
    writer.visitEnd();
    classes.add(ClassData.create(template.typeInfo(), classWriter.toByteArray()));
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
    final VariableSet variables = 
        new VariableSet(fieldNames, template.typeInfo(), thisVar, RENDER_METHOD);
    Scope rootScope = variables.enterScope();
    DetachState detachState = new DetachState(variables, thisVar, stateField);
    final Statement nodeBody = 
        new SoyNodeCompiler(
            detachState,
            variables,
            appendableVar,
            contextVar,
            new ExprCompiler(detachState, variables, thisVar, paramFields))
            .compile(template.node());
    final Statement exitScope = rootScope.exitScope();
    final Expression done = MethodRef.RENDER_RESULT_DONE.invoke();
    Statement fullMethodBody = new Statement() {
      @Override void doGen(GeneratorAdapter adapter) {
        adapter.mark(start);
        nodeBody.gen(adapter);
        exitScope.gen(adapter);
        done.gen(adapter);
        adapter.mark(end);
        adapter.returnValue();

        thisVar.tableEntry(adapter);
        appendableVar.tableEntry(adapter);
        contextVar.tableEntry(adapter);
        variables.generateTableEntries(adapter);
      }
    };
    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_PUBLIC,
        RENDER_METHOD,
        null /* no generic signature */,
        new Type[] { Type.getType(IOException.class) },
        writer);
    ga.visitCode();
    fullMethodBody.gen(ga);
    try {
      ga.endMethod();
    } catch (Throwable t) {
      // ASM fails in bizarre ways, attach a trace of the thing we tried to generate to the 
      // exception.
      throw new RuntimeException("Failed to generate method:\n" + fullMethodBody, t);
    }
    variables.defineFields(writer);
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
      @Override void doGen(GeneratorAdapter ga) {
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
    GeneratorAdapter ga = new GeneratorAdapter(
        Opcodes.ACC_PUBLIC, 
        CompiledTemplateMetadata.GENERATED_CONSTRUCTOR, 
        null, // no generic signature
        null, // no checked exception
        writer);
    ga.visitCode();
    constructorBody.gen(ga);
    try {
      ga.endMethod();
    } catch (Throwable t) {
      // ASM fails in bizarre ways, attach a trace of the thing we tried to generate to the 
      // exception.
      throw new RuntimeException("Failed to generate method:\n" + constructorBody, t);
    }
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
      @Override void doGen(GeneratorAdapter adapter) {
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

  private static final class ExprCompiler extends ExpressionCompiler {
    private final DetachState detachState;
    private final VariableSet variables;
    private final Expression thisExpr;
    private final ImmutableMap<String, FieldRef> paramFields;
    private ExpressionDetacher currentDetacher;

    ExprCompiler(
        DetachState detachState,
        VariableSet variables,
        Expression thisExpr,
        ImmutableMap<String, FieldRef> paramFields) {
      this.detachState = detachState;
      this.variables = checkNotNull(variables);
      this.thisExpr = checkNotNull(thisExpr);
      this.paramFields = checkNotNull(paramFields);
    }

    @Override public SoyExpression compile(ExprNode node) {
      final SoyExpression exec = super.compile(node);
      final ExpressionDetacher local = currentDetacher;
      if (local != null) {
        // If any compiled expressions required detaching, add the detach block.
        currentDetacher = null;  // clear
        return exec.withSource(local.makeDetachable(exec));
      }
      return exec;
    }

    @Override protected SoyExpression visitVarRefNode(VarRefNode node) {
      // Sing muse, of the various data access patterns
      // and how we don't support them yet

      VarDefn defn = node.getDefnDecl();
      switch (defn.kind()) {
        case LOCAL_VAR: {
          LocalVar local = (LocalVar) defn;
          if (local.declaringNode().getKind() == SoyNode.Kind.FOR_NODE) {
            // an index variable in a {for $index in range(...)} statement
            // These are special because they do not need any attaching/detaching logic
            return variables.getVariable(node.getName()).expr();
          }
          throw new UnsupportedOperationException("lets and foreach loops aren't supported yet");
        }
        case PARAM: {
          TemplateParam param = (TemplateParam) defn;
          if (param.declLoc() != DeclLoc.HEADER) {
            throw new RuntimeException(
                "header doc params are not supported by jbcsrc, use {@param..} instead");
          }
          return handleParam((HeaderParam) param);
        }
        case IJ_PARAM:
          throw new RuntimeException("$ij are not supported by jbcsrc, use {@inject..} instead");
        case UNDECLARED:
          throw new RuntimeException("undeclared params are not supported by jbcsrc");
        default:
          throw new AssertionError();
      }
    }

    private SoyExpression handleParam(HeaderParam param) {
      // TODO(lukes): It would be nice not to generate a detach for every param access, since after
      // the first successful 'resolve()' we know that all later ones will also resolve successfully
      // This means that we will generate a potentially large amount of dead branches/states/calls 
      // to SoyValueProvider.status().  We could eliminate these by doing some kind of definite 
      // assignment analysis to know whether or not a particular varref is _not_ the first one.  
      // This would be super awesome and would save bytecode/branches/states and technically be 
      // useful for all varrefs.  For the time being we do the naive thing and just assume that the 
      // jit can handle all the dead brances effectively.
      Expression paramExpr = 
          getDetach().resolveSoyValueProvider(paramFields.get(param.name()).accessor(thisExpr));
      SoyType type = param.type();
      // This inserts a CHECKCAST instruction (aka runtime type checking).  However, it is limited
      // since we do not have good checking for unions (or nullability)
      // TODO(lukes): Where/how should we implement type checking.  For the time being type errors
      // will show up here, and in the unboxing conversions performed during expression manipulation
      // And, presumably, in NullPointerExceptions.
      return SoyExpression.forSoyValue(type, paramExpr.cast(Type.getType(type.javaType())));
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
    
    private ExpressionDetacher getDetach() {
      ExpressionDetacher local = currentDetacher;
      return local == null ? currentDetacher = detachState.generateExpressionDetacher() : local;
    }
  }
}
