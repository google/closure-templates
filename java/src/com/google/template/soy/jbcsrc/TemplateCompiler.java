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

import static com.google.template.soy.jbcsrc.StandardNames.IJ_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.PARAMS_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.STATE_FIELD;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NULLARY_INIT;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.OBJECT;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.RENDER_CONTEXT_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_RECORD_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantSanitizedContentKindAsContentKind;
import static com.google.template.soy.jbcsrc.restricted.FieldRef.createField;
import static com.google.template.soy.jbcsrc.restricted.FieldRef.createFinalField;
import static com.google.template.soy.jbcsrc.restricted.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.restricted.LocalVariable.createThisVar;
import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.SoyNodeCompiler.CompiledMethodBody;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.internal.InnerClasses;
import com.google.template.soy.jbcsrc.internal.JbcSrcNameGenerators;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.AnnotationRef;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Compiles the top level {@link CompiledTemplate} class for a single template and all related
 * classes.
 */
final class TemplateCompiler {
  private static final AnnotationRef<TemplateMetadata> TEMPLATE_METADATA_REF =
      AnnotationRef.forType(TemplateMetadata.class);
  private static final TypeInfo TEMPLATE_TYPE = TypeInfo.create(CompiledTemplate.class);

  private final CompiledTemplateRegistry registry;
  private final FieldRef paramsField;
  private final FieldRef ijField;
  private final FieldRef stateField;
  private final UniqueNameGenerator fieldNames = JbcSrcNameGenerators.forFieldNames();
  private final ImmutableMap<String, FieldRef> paramFields;
  private final CompiledTemplateMetadata template;
  private final TemplateNode templateNode;
  private final InnerClasses innerClasses;
  private final ErrorReporter reporter;
  private final SoyTypeRegistry soyTypeRegistry;
  private SoyClassWriter writer;

  TemplateCompiler(
      CompiledTemplateRegistry registry,
      CompiledTemplateMetadata template,
      TemplateNode templateNode,
      ErrorReporter reporter,
      SoyTypeRegistry soyTypeRegistry) {
    this.registry = registry;
    this.template = template;
    this.templateNode = templateNode;
    TypeInfo ownerType = template.typeInfo();
    this.paramsField = createFinalField(ownerType, PARAMS_FIELD, SoyRecord.class).asNonNull();
    this.ijField = createFinalField(ownerType, IJ_FIELD, SoyRecord.class).asNonNull();
    this.stateField = createField(ownerType, STATE_FIELD, Type.INT_TYPE);
    this.innerClasses = new InnerClasses(ownerType);
    fieldNames.claimName(PARAMS_FIELD);
    fieldNames.claimName(IJ_FIELD);
    fieldNames.claimName(STATE_FIELD);
    ImmutableMap.Builder<String, FieldRef> builder = ImmutableMap.builder();
    for (TemplateParam param : templateNode.getAllParams()) {
      String name = param.name();
      fieldNames.claimName(name);
      builder.put(name, createFinalField(ownerType, name, SoyValueProvider.class).asNonNull());
    }
    this.paramFields = builder.build();
    this.reporter = reporter;
    this.soyTypeRegistry = soyTypeRegistry;
  }

  /**
   * Returns the list of classes needed to implement this template.
   *
   * <p>For each template, we generate:
   *
   * <ul>
   *   <li>A {@link com.google.template.soy.jbcsrc.shared.CompiledTemplate.Factory}
   *   <li>A {@link CompiledTemplate}
   *   <li>A DetachableSoyValueProvider subclass for each {@link LetValueNode} and {@link
   *       CallParamValueNode}
   *   <li>A DetachableContentProvider subclass for each {@link LetContentNode} and {@link
   *       CallParamContentNode}
   *       <p>Note: This will <em>not</em> generate classes for other templates, only the template
   *       configured in the constructor. But it will generate classes that <em>reference</em> the
   *       classes that are generated for other templates. It is the callers responsibility to
   *       ensure that all referenced templates are generated and available in the classloader that
   *       ultimately loads the returned classes.
   */
  Iterable<ClassData> compile() {
    List<ClassData> classes = new ArrayList<>();

    // first generate the factory
    if (templateNode.getVisibility() != Visibility.PRIVATE) {
      // Don't generate factory if the template is private.  The factories are only
      // useful to instantiate templates for calls from java.  Soy->Soy calls should invoke
      // constructors directly.
      new TemplateFactoryCompiler(template, templateNode, innerClasses).compile();
    }

    writer =
        SoyClassWriter.builder(template.typeInfo())
            .setAccess(Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL)
            .implementing(TEMPLATE_TYPE)
            .sourceFileName(templateNode.getSourceLocation().getFileName())
            .build();
    generateTemplateMetadata();
    generateKindMethod();
    stateField.defineField(writer);
    paramsField.defineField(writer);
    ijField.defineField(writer);
    for (FieldRef field : paramFields.values()) {
      field.defineField(writer);
    }

    Statement fieldInitializers = generateRenderMethod();

    generateConstructor(fieldInitializers);

    innerClasses.registerAllInnerClasses(writer);
    writer.visitEnd();

    classes.add(writer.toClassData());
    classes.addAll(innerClasses.getInnerClassData());
    writer = null;
    return classes;
  }

  private void generateKindMethod() {
    Statement.returnExpression(
            constantSanitizedContentKindAsContentKind(templateNode.getContentKind()))
        .writeMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, template.kindMethod().method(), writer);
  }

  /** Writes a {@link TemplateMetadata} to the generated class. */
  private void generateTemplateMetadata() {
    SanitizedContentKind contentKind = templateNode.getContentKind();
    String kind = contentKind == null ? "" : contentKind.name();

    // using linked hash sets below for determinism
    Set<String> uniqueIjs = new LinkedHashSet<>();
    for (VarRefNode var : getAllNodesOfType(templateNode, VarRefNode.class)) {
      if (var.isInjected()) {
        uniqueIjs.add(var.getName());
      }
    }

    Set<String> callees = new LinkedHashSet<>();
    for (CallBasicNode call : getAllNodesOfType(templateNode, CallBasicNode.class)) {
      callees.add(call.getCalleeName());
    }

    Set<String> delCallees = new LinkedHashSet<>();
    for (CallDelegateNode call : getAllNodesOfType(templateNode, CallDelegateNode.class)) {
      delCallees.add(call.getDelCalleeName());
    }

    TemplateMetadata.DelTemplateMetadata deltemplateMetadata;
    if (templateNode.getKind() == SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
      TemplateDelegateNode delegateNode = (TemplateDelegateNode) templateNode;
      deltemplateMetadata =
          createDelTemplateMetadata(
              delegateNode.getDelPackageName() == null ? "" : delegateNode.getDelPackageName(),
              delegateNode.getDelTemplateName(),
              delegateNode.getDelTemplateVariant());
    } else {
      deltemplateMetadata = createDefaultDelTemplateMetadata();
    }
    TemplateMetadata metadata =
        createTemplateMetadata(kind, uniqueIjs, callees, delCallees, deltemplateMetadata);
    TEMPLATE_METADATA_REF.write(metadata, writer);
  }

  @AutoAnnotation
  static TemplateMetadata createTemplateMetadata(
      String contentKind,
      Set<String> injectedParams,
      Set<String> callees,
      Set<String> delCallees,
      TemplateMetadata.DelTemplateMetadata deltemplateMetadata) {
    return new AutoAnnotation_TemplateCompiler_createTemplateMetadata(
        contentKind, injectedParams, callees, delCallees, deltemplateMetadata);
  }

  @AutoAnnotation
  static TemplateMetadata.DelTemplateMetadata createDefaultDelTemplateMetadata() {
    return new AutoAnnotation_TemplateCompiler_createDefaultDelTemplateMetadata();
  }

  @AutoAnnotation
  static TemplateMetadata.DelTemplateMetadata createDelTemplateMetadata(
      String delPackage, String name, String variant) {
    return new AutoAnnotation_TemplateCompiler_createDelTemplateMetadata(delPackage, name, variant);
  }

  private Statement generateRenderMethod() {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    final LocalVariable appendableVar =
        createLocal("appendable", 1, LOGGING_ADVISING_APPENDABLE_TYPE, start, end).asNonNullable();
    final LocalVariable contextVar =
        createLocal("context", 2, RENDER_CONTEXT_TYPE, start, end).asNonNullable();
    final TemplateVariableManager variableSet =
        new TemplateVariableManager(
            fieldNames, template.typeInfo(), thisVar, template.renderMethod().method());
    ImmutableMap<TemplateStateVar, SoyExpression> stateInitializers = ImmutableMap.of();
    if (templateNode instanceof TemplateElementNode) {
      stateInitializers =
          generateStateInitializers((TemplateElementNode) templateNode, variableSet);
    }
    TemplateVariables variables =
        new TemplateVariables(
            variableSet, thisVar, stateInitializers, new RenderContextExpression(contextVar));
    List<TemplateStateVar> stateVars = ImmutableList.of();
    if (templateNode instanceof TemplateElementNode) {
      stateVars = ((TemplateElementNode) templateNode).getStateVars();
    }
    final CompiledMethodBody methodBody =
        SoyNodeCompiler.create(
                registry,
                innerClasses,
                stateField,
                thisVar,
                AppendableExpression.forLocal(appendableVar),
                variableSet,
                variables,
                reporter,
                soyTypeRegistry,
                stateVars)
            .compile(templateNode);
    final Statement returnDone = Statement.returnExpression(MethodRef.RENDER_RESULT_DONE.invoke());
    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        methodBody.body().gen(adapter);
        adapter.mark(end);
        returnDone.gen(adapter);

        thisVar.tableEntry(adapter);
        appendableVar.tableEntry(adapter);
        contextVar.tableEntry(adapter);
        variableSet.generateTableEntries(adapter);
      }
    }.writeIOExceptionMethod(Opcodes.ACC_PUBLIC, template.renderMethod().method(), writer);
    writer.setNumDetachStates(methodBody.numberOfDetachStates());
    variableSet.defineStaticFields(writer);
    return variableSet.defineFields(writer);
  }

  private ImmutableMap<TemplateStateVar, SoyExpression> generateStateInitializers(
      TemplateElementNode node, TemplateVariableManager varManager) {
    BasicExpressionCompiler constantCompiler =
        ExpressionCompiler.createConstantCompiler(varManager, reporter, soyTypeRegistry);
    ImmutableMap.Builder<TemplateStateVar, SoyExpression> builder = ImmutableMap.builder();
    for (TemplateStateVar state : node.getStateVars()) {
      SoyExpression stateValue;
      if (state.defaultValue().getType() == NullType.getInstance()) {
        // a special case for null to avoid poor handling elsewhere in the compiler.
        stateValue =
            SoyExpression.forSoyValue(
                state.type(),
                BytecodeUtils.constantNull(
                    SoyRuntimeType.getBoxedType(state.type()).runtimeType()));
      } else {
        stateValue = constantCompiler.compile(state.defaultValue());
      }
      if (!stateValue.isCheap()) {
        // these fields are package private so that lazy closures can access them directly.
        FieldRef ref =
            varManager.addPackagePrivateStaticField(
                state.name(), stateValue.resultType(), stateValue);
        stateValue = stateValue.withSource(ref.accessor());
      }
      builder.put(state, stateValue);
    }
    return builder.build();
  }

  /**
   * Generate a public constructor that assigns our final field and checks for missing required
   * params.
   *
   * <p>This constructor is called by the generate factory classes.
   *
   * @param fieldInitializers additional statements to initialize fields (other than params)
   */
  private void generateConstructor(Statement fieldInitializers) {
    final Label start = new Label();
    final Label end = new Label();
    final LocalVariable thisVar = createThisVar(template.typeInfo(), start, end);
    final LocalVariable paramsVar = createLocal("params", 1, SOY_RECORD_TYPE, start, end);
    final LocalVariable ijVar = createLocal("ij", 2, SOY_RECORD_TYPE, start, end);
    final List<Statement> assignments = new ArrayList<>();
    assignments.add(fieldInitializers); // for other fields needed by the compiler.
    assignments.add(paramsField.putInstanceField(thisVar, paramsVar));
    assignments.add(ijField.putInstanceField(thisVar, ijVar));
    for (TemplateParam param : templateNode.getAllParams()) {
      Expression paramProvider = getParam(paramsVar, ijVar, param);
      assignments.add(paramFields.get(param.name()).putInstanceField(thisVar, paramProvider));
    }
    Statement constructorBody =
        new Statement() {
          @Override
          protected void doGen(CodeBuilder ga) {
            ga.mark(start);
            // call super()
            thisVar.gen(ga);
            ga.invokeConstructor(OBJECT.type(), NULLARY_INIT);
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
    constructorBody.writeMethod(Opcodes.ACC_PUBLIC, template.constructor().method(), writer);
  }

  /**
   * Returns an expression that fetches the given param from the params record or the ij record and
   * enforces the {@link TemplateParam#isRequired()} flag, throwing SoyDataException if a required
   * parameter is missing.
   */
  private static Expression getParam(
      LocalVariable paramsVar, LocalVariable ijVar, TemplateParam param) {
    Expression fieldName = BytecodeUtils.constant(param.name());
    Expression record = param.isInjected() ? ijVar : paramsVar;
    // NOTE: for compatibility with Tofu and jssrc we do not check for missing required parameters
    // here instead they will just turn into null.  Existing templates depend on this.
    return MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(record, fieldName);
  }

  private final class TemplateVariables implements TemplateParameterLookup {
    private final TemplateVariableManager variableSet;
    private final Expression thisRef;
    private final RenderContextExpression renderContext;
    private final ImmutableMap<TemplateStateVar, SoyExpression> stateVars;

    TemplateVariables(
        TemplateVariableManager variableSet,
        Expression thisRef,
        ImmutableMap<TemplateStateVar, SoyExpression> stateVars,
        RenderContextExpression renderContext) {
      this.variableSet = variableSet;
      this.thisRef = thisRef;
      this.renderContext = renderContext;
      this.stateVars = stateVars;
    }

    @Override
    public Expression getParam(TemplateParam param) {
      return paramFields.get(param.name()).accessor(thisRef);
    }

    @Override
    public SoyExpression getState(TemplateStateVar stateVar) {
      return stateVars.get(stateVar);
    }

    @Override
    public Expression getLocal(LocalVar local) {
      return variableSet.getVariable(local.name()).local();
    }

    @Override
    public Expression getLocal(SyntheticVarName varName) {
      return variableSet.getVariable(varName).local();
    }

    @Override
    public JbcSrcPluginContext getPluginContext() {
      return renderContext;
    }

    @Override
    public RenderContextExpression getRenderContext() {
      return renderContext;
    }

    @Override
    public Expression getParamsRecord() {
      return paramsField.accessor(thisRef);
    }

    @Override
    public Expression getIjRecord() {
      return ijField.accessor(thisRef);
    }
  }
}
