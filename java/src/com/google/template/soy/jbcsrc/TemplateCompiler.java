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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.soytree.SoyTreeUtils.allNodesOfType;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.internal.InnerMethods;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.AnnotationRef;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LambdaFactory;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PartialFileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode.CssPath;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.UndefinedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

/**
 * Compiles the top level {@link CompiledTemplate} class for a single template and all related
 * classes.
 */
final class TemplateCompiler {
  private static final AnnotationRef<TemplateMetadata> TEMPLATE_METADATA_REF =
      AnnotationRef.forType(TemplateMetadata.class);

  public static final String VARIANT_VAR_NAME = "__modifiable_variant__";

  private final FieldManager fields;
  private final CompiledTemplateMetadata template;
  private final TemplateNode templateNode;
  private final InnerMethods innerClasses;
  private final SoyClassWriter writer;
  private final TemplateAnalysis analysis;
  private final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  private final PartialFileSetMetadata fileSetMetadata;

  TemplateCompiler(
      TemplateNode templateNode,
      SoyClassWriter writer,
      FieldManager fields,
      InnerMethods innerClasses,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      PartialFileSetMetadata fileSetMetadata) {
    this.template = CompiledTemplateMetadata.create(templateNode);
    this.templateNode = templateNode;
    this.writer = writer;
    this.fields = fields;
    this.innerClasses = innerClasses;
    this.analysis = TemplateAnalysisImpl.analyze(templateNode);
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
    this.fileSetMetadata = fileSetMetadata;
  }

  /**
   * Returns the list of classes needed to implement this template.
   *
   * <p>For each template, we generate:
   *
   * <ul>
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
  void compile() {

    // TODO(lukes): change the flow of this method so these methods return method bodies and we only
    // write the methods to the writer after generating everything.
    // this should make the order of operations clearer and limit access to the writer.

    if (template.defaultModTemplateMethod().isPresent()) {
      generateTemplateMethod(template.templateMethod(), template.modifiableSelectMethod().get());
      generateTemplateMethod(template.defaultModTemplateMethod().get(), template.renderMethod());
    } else {
      generateTemplateMethod(template.templateMethod(), template.renderMethod());
    }
    generateDelegateRenderMethod();
    generateRenderMethod();
    generateModifiableSelectMethod();
  }

  /** Write the function "templateMethod", which returns a reference to "renderMethod". */
  private void generateTemplateMethod(MethodRef templateMethod, MethodRef renderMethod) {
    // use invoke dynamic to lazily allocate the template instance.
    // templates are needed for direct java->soy calls, references to template literals and
    // deltemplates, which
    // should mean that the vast majority are not needed.  We can generate a simple factory() method
    // that uses invoke dynamic to generate a factory instance as needed.  This should be just as
    // fast as our previous approach of generating an explicit factory subclass while requiring much
    // less bytecode, since most factory instances are not needed.
    // The code here is identical to:
    // public static CompiledTemplate template() {
    //  return foo::render;
    // }
    // assuming foo is the name of the template class.
    Statement methodBody =
        Statement.returnExpression(
            LambdaFactory.create(MethodRefs.COMPILED_TEMPLATE_RENDER, renderMethod).invoke());
    CodeBuilder methodWriter =
        new CodeBuilder(methodAccess(), templateMethod.method(), /* exceptions= */ null, writer);
    generateTemplateMetadata(methodWriter);
    methodBody.writeMethodTo(methodWriter);
  }

  private boolean isModifyingTemplate() {
    return templateNode instanceof TemplateBasicNode
        && ((TemplateBasicNode) templateNode).getModifiesExpr() != null;
  }

  private int methodAccess() {
    return (templateNode.getVisibility() == Visibility.PUBLIC || isModifyingTemplate()
            ? Opcodes.ACC_PUBLIC
            // TODO(b/180904763): private templates need to have default access so they can be
            // called by our other templates in the same file when we are compiling templates to
            // multiple files.
            : (NamespaceExemptions.isKnownDuplicateNamespace(
                    templateNode.getSoyFileHeaderInfo().getNamespace())
                ? /* default access */ 0
                : Opcodes.ACC_PRIVATE))
        | Opcodes.ACC_STATIC;
  }

  /** Writes a {@link TemplateMetadata} to the generated {@code template()} method. */
  private void generateTemplateMetadata(CodeBuilder builder) {
    ContentKind kind = Converters.toContentKind(templateNode.getContentKind());
    List<String> positionalParams =
        template.hasPositionalSignature()
            ? template.templateType().getActualParameters().stream()
                .map(Parameter::getName)
                .collect(toImmutableList())
            : ImmutableList.of();

    // using immutable sets below for deterministic ordering
    ImmutableSet<String> uniqueIjs =
        allNodesOfType(templateNode, VarRefNode.class)
            .filter(VarRefNode::isInjected)
            .map(VarRefNode::getNameWithoutLeadingDollar)
            .collect(toImmutableSet());

    ImmutableSet<String> callees =
        allNodesOfType(templateNode, TemplateLiteralNode.class)
            .map(TemplateLiteralNode::getResolvedName)
            .collect(toImmutableSet());

    ImmutableSet<String> delCallees =
        ImmutableSet.<String>builder()
            .addAll(
                allNodesOfType(templateNode, CallDelegateNode.class)
                    .map(CallDelegateNode::getDelCalleeName)
                    .collect(toImmutableSet()))
            .addAll(
                allNodesOfType(templateNode, TemplateLiteralNode.class)
                    .filter(literal -> ((TemplateType) literal.getType()).isModifiable())
                    .map(TemplateCompiler::legacyOrModifiableName)
                    .collect(toImmutableSet()))
            .build();

    TemplateMetadata.DelTemplateMetadata deltemplateMetadata;
    if (templateNode.getKind() == SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
      TemplateDelegateNode delegateNode = (TemplateDelegateNode) templateNode;
      deltemplateMetadata =
          createDelTemplateMetadata(
              nullToEmpty(delegateNode.getModName()),
              delegateNode.getDelTemplateName(),
              delegateNode.getDelTemplateVariant());
    } else if (templateNode instanceof TemplateBasicNode) {
      deltemplateMetadata = metadataForBasicNode((TemplateBasicNode) templateNode);
    } else {
      deltemplateMetadata = createDefaultDelTemplateMetadata();
    }
    Set<String> namespaces = Sets.newLinkedHashSet();
    // This ordering is critical to preserve css hierarchy.
    namespaces.addAll(templateNode.getParent().getRequiredCssNamespaces());
    templateNode.getParent().getAllRequiredCssPaths().stream()
        .map(CssPath::getNamespace)
        .filter(Objects::nonNull)
        .forEach(namespaces::add);
    namespaces.addAll(templateNode.getRequiredCssNamespaces());

    // Require paths.
    Set<String> cssPaths = Sets.newLinkedHashSet();
    templateNode.getParent().getAllRequiredCssPaths().stream()
        // Temporary, to avoid double requesting w/ the above.
        .filter(p -> p.getNamespace() == null)
        .filter(p -> p.resolvedPath().isPresent())
        .forEach(p -> cssPaths.add(p.resolvedPath().get()));

    TemplateMetadata metadata =
        createTemplateMetadata(
            kind,
            template.hasPositionalSignature(),
            positionalParams,
            namespaces,
            cssPaths,
            uniqueIjs,
            callees,
            delCallees,
            deltemplateMetadata);
    TEMPLATE_METADATA_REF.write(metadata, builder);
  }

  static String legacyOrModifiableName(TemplateLiteralNode node) {
    TemplateType templateType = (TemplateType) node.getType();
    return !templateType.getLegacyDeltemplateNamespace().isEmpty()
        ? templateType.getLegacyDeltemplateNamespace()
        : node.getResolvedName();
  }

  TemplateMetadata.DelTemplateMetadata metadataForBasicNode(TemplateBasicNode templateBasicNode) {
    if (templateBasicNode.isModifiable()) {
      return createDelTemplateMetadata(
          nullToEmpty(templateBasicNode.getModName()),
          modifiableImplsMapKey(templateBasicNode),
          templateBasicNode.getDelTemplateVariant());
    } else if (templateBasicNode.getModifiesExpr() != null) {
      return createDelTemplateMetadata(
          nullToEmpty(templateBasicNode.getModName()),
          legacyOrModifiableName(
              (TemplateLiteralNode) templateBasicNode.getModifiesExpr().getRoot()),
          templateBasicNode.getDelTemplateVariant());
    }
    return createDefaultDelTemplateMetadata();
  }

  private static String modifiableImplsMapKey(TemplateBasicNode templateBasicNode) {
    return !templateBasicNode.getLegacyDeltemplateNamespace().isEmpty()
        ? templateBasicNode.getLegacyDeltemplateNamespace()
        : templateBasicNode.getTemplateName();
  }

  @AutoAnnotation
  static TemplateMetadata createTemplateMetadata(
      ContentKind contentKind,
      boolean hasPositionalSignature,
      List<String> positionalParams,
      Set<String> requiredCssNames,
      Set<String> requiredCssPaths,
      Set<String> injectedParams,
      Set<String> callees,
      Set<String> delCallees,
      TemplateMetadata.DelTemplateMetadata deltemplateMetadata) {
    return new AutoAnnotation_TemplateCompiler_createTemplateMetadata(
        contentKind,
        hasPositionalSignature,
        positionalParams,
        requiredCssNames,
        requiredCssPaths,
        injectedParams,
        callees,
        delCallees,
        deltemplateMetadata);
  }

  @AutoAnnotation
  static TemplateMetadata.DelTemplateMetadata createDefaultDelTemplateMetadata() {
    return new AutoAnnotation_TemplateCompiler_createDefaultDelTemplateMetadata();
  }

  @AutoAnnotation
  static TemplateMetadata.DelTemplateMetadata createDelTemplateMetadata(
      String modName, String name, String variant) {
    return new AutoAnnotation_TemplateCompiler_createDelTemplateMetadata(modName, name, variant);
  }

  /**
   * Generate a static method that has the same signature as CompiledTemplate.render if we are
   * generating a render method with a positional signature.
   *
   * <p>Generally this method will be rarely called, it will only be used if
   *
   * <ul>
   *   <li>This template is used as a template literal
   *   <li>This template is called directly by {@code SoySauceImpl}
   *   <li>This template is called with a {@code data=...} expression
   * </ul>
   *
   * The first two are generally rare and will not likely become common and the latter is
   * increasingly discouraged. So ideally, we would not generate these methods. Some possible ideas
   *
   * <ul>
   *   <li>use {@code invokedynamic} to fully construct {@code CompiledTemplate} classes. Then we
   *       could avoid the {@code template()} and {@code render()} methods completely. This is a bit
   *       complex but would likely be a code size win.
   *   <li>Switch some of these usecases to use the {@code MethodHandle} API directly. This may or
   *       may not actually work (I am not sure if it is safe to pass method handles around).
   * </ul>
   */
  private void generateDelegateRenderMethod() {
    if (!template.hasPositionalSignature()) {
      return;
    }
    MethodRef renderMethod = template.positionalRenderMethod().get();
    Label start = new Label();
    Label end = new Label();
    LocalVariable data =
        LocalVariable.createLocal(
            StandardNames.PARAMS, 0, BytecodeUtils.PARAM_STORE_TYPE, start, end);
    LocalVariable output =
        LocalVariable.createLocal(
            StandardNames.APPENDABLE,
            1,
            BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
            start,
            end);
    LocalVariable context =
        LocalVariable.createLocal(
            StandardNames.RENDER_CONTEXT, 2, BytecodeUtils.RENDER_CONTEXT_TYPE, start, end);
    List<Expression> renderMethodArgs = new ArrayList<>();
    for (var param : template.templateType().getActualParameters()) {
      renderMethodArgs.add(
          data.invoke(
              MethodRefs.PARAM_STORE_GET_PARAMETER,
              BytecodeUtils.constantRecordProperty(param.getName())));
    }
    renderMethodArgs.add(output);
    renderMethodArgs.add(context);
    Expression invokePositional = renderMethod.invoke(renderMethodArgs);
    Statement methodBody =
        new Statement() {
          @Override
          protected void doGen(CodeBuilder cb) {
            cb.mark(start);
            invokePositional.gen(cb);
            cb.returnValue();
            cb.mark(end);
            // output debugging data for our parameters
            data.tableEntry(cb);
            output.tableEntry(cb);
            context.tableEntry(cb);
          }
        };
    methodBody.writeMethod(methodAccess(), template.renderMethod().method(), writer);
  }

  private void generateRenderMethod() {
    BasicExpressionCompiler constantCompiler =
        ExpressionCompiler.createConstantCompiler(
            templateNode,
            analysis,
            new SimpleLocalVariableManager(template.typeInfo().type(), /* isStatic= */ true),
            javaSourceFunctionCompiler,
            fileSetMetadata);
    Label start = new Label();
    Label end = new Label();
    ImmutableList.Builder<String> paramNames = ImmutableList.builder();
    if (template.hasPositionalSignature()) {
      paramNames.addAll(
          template.templateType().getActualParameters().stream()
              .map(Parameter::getName)
              .collect(toImmutableList()));
    } else {
      paramNames.add(StandardNames.PARAMS);
    }
    paramNames.add(StandardNames.APPENDABLE).add(StandardNames.RENDER_CONTEXT);
    Method method = template.positionalRenderMethod().orElse(template.renderMethod()).method();
    TemplateVariableManager variableSet =
        new TemplateVariableManager(
            template.typeInfo().type(),
            method.getArgumentTypes(),
            paramNames.build(),
            start,
            end,
            /* isStatic= */ true);
    Optional<Expression> paramsVar =
        template.hasPositionalSignature()
            ? Optional.empty()
            : Optional.of(variableSet.getVariable(StandardNames.PARAMS));

    var renderContext =
        new RenderContextExpression(variableSet.getVariable(StandardNames.RENDER_CONTEXT));
    TemplateVariables variables = new TemplateVariables(variableSet, paramsVar, renderContext);
    AppendableExpression appendable =
        AppendableExpression.forExpression(
            variableSet.getVariable(StandardNames.APPENDABLE).asNonJavaNullable());
    SoyNodeCompiler nodeCompiler =
        SoyNodeCompiler.create(
            templateNode,
            template.typeInfo(),
            analysis,
            innerClasses,
            appendable,
            variableSet,
            variables,
            fields,
            constantCompiler,
            javaSourceFunctionCompiler,
            fileSetMetadata);
    // Allocate local variables for all _used_ parameters.  Unused params issue a warning but
    // especially in the case of element templates things may be just unused server side.
    // NOTE: we initialize the parameters prior to where the jump table is initialized, this means
    // that all variables will be re-initialized ever time we re-enter the template.
    // TODO(lukes): move into SoyNodeCompiler.compile?  The fact that we construct SoyNodeCompiler
    // and then pull stuff out of it to initialize params is awkward
    TemplateVariableManager.Scope templateScope = variableSet.enterScope();
    List<Statement> paramInitStatements = new ArrayList<>();
    var referencedParams = getReferencedParams(templateNode);
    for (TemplateParam param : templateNode.getAllParams()) {
      if (!referencedParams.contains(param)) {
        continue;
      }
      SoyExpression defaultValue =
          param.hasDefault()
              ? getDefaultValue(param, nodeCompiler.exprCompiler, constantCompiler)
              : null;
      Expression initialValue;
      LocalVariable localVariable;
      // When pulling params out of ParamStores we need to allocate locals for them
      if (param.isInjected()) {
        initialValue = renderContext.getInjectedValue(param.name(), defaultValue);
        localVariable = templateScope.createNamedLocal(param.name(), initialValue.resultType());
        paramInitStatements.add(localVariable.initialize(initialValue));
      } else if (paramsVar.isPresent()) {
        initialValue = getFieldProviderOrDefault(param.name(), paramsVar.get(), defaultValue);
        localVariable = templateScope.createNamedLocal(param.name(), initialValue.resultType());
        paramInitStatements.add(localVariable.initialize(initialValue));
      } else {
        // positional parameters just need defaults to be managed
        if (defaultValue != null) {
          localVariable = (LocalVariable) variableSet.getVariable(param.name());
          paramInitStatements.add(
              localVariable.store(
                  MethodRefs.RUNTIME_PARAM_OR_DEFAULT.invoke(localVariable, defaultValue.box())));
        }
      }
    }
    Statement methodBody =
        nodeCompiler.compile(
            templateNode,
            /* prefix= */ ExtraCodeCompiler.NO_OP,
            /* suffix= */ ExtraCodeCompiler.NO_OP);
    Statement exitTemplateScope = templateScope.exitScope();
    Statement returnDone = Statement.returnExpression(MethodRefs.RENDER_RESULT_DONE.invoke());
    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        for (Statement paramInitStatement : paramInitStatements) {
          paramInitStatement.gen(adapter);
        }
        methodBody.gen(adapter);
        exitTemplateScope.gen(adapter);
        adapter.mark(end);
        returnDone.gen(adapter);

        variableSet.generateTableEntries(adapter);
      }
    }.writeIOExceptionMethod(methodAccess(), method, writer);
  }

  /**
   * Returns the TemplateParams that are referenced in the template.
   *
   * <p>This is simply all the params that are referenced by variables or have defaults and are
   * passed by {@code data="all"} callsites.
   */
  private static Set<TemplateParam> getReferencedParams(TemplateNode templateNode) {
    Set<TemplateParam> referencedVars =
        SoyTreeUtils.allNodesOfType(templateNode, VarRefNode.class)
            .map(v -> v.getDefnDecl())
            .filter(d -> d instanceof TemplateParam)
            .map(d -> (TemplateParam) d)
            .collect(toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));
    if (referencedVars.size() == templateNode.getHeaderParams().size()) {
      return referencedVars;
    }

    for (CallNode call : SoyTreeUtils.getAllNodesOfType(templateNode, CallNode.class)) {
      if (call.isPassingAllData()) {
        ImmutableSet<String> explicitCallParams =
            call.getChildren().stream().map(c -> c.getKey().identifier()).collect(toImmutableSet());
        for (TemplateParam param : templateNode.getParams()) {
          if (param.hasDefault() && !explicitCallParams.contains(param.name())) {
            referencedVars.add(param);
          }
        }
      }
    }
    return referencedVars;
  }

  // TODO(lukes): it seems like this should actually compile params to SoyValueProvider instances
  private SoyExpression getDefaultValue(
      TemplateHeaderVarDefn headerVar,
      ExpressionCompiler expressionCompiler,
      BasicExpressionCompiler constantCompiler) {
    ExprRootNode defaultValueNode = headerVar.defaultValue();
    if (defaultValueNode.getType() == NullType.getInstance()) {
      // a special case for null to avoid poor handling elsewhere in the compiler.
      return SoyExpression.SOY_NULL;
    } else if (defaultValueNode.getType() == UndefinedType.getInstance()) {
      return SoyExpression.SOY_UNDEFINED;
    } else {
      if (ExpressionCompiler.canCompileToConstant(templateNode, defaultValueNode)) {
        SoyExpression defaultValue =
            constantCompiler.compile(defaultValueNode).box().toMaybeConstant();
        if (!defaultValue.isCheap()) {
          FieldRef ref = fields.addStaticField("default$" + headerVar.name(), defaultValue);
          defaultValue = defaultValue.withSource(ref.accessor());
        }
        return defaultValue;
      } else {
        Optional<SoyExpression> defaultExpression =
            expressionCompiler.compileWithNoDetaches(defaultValueNode);
        checkState(
            defaultExpression.isPresent(), "Default expression unexpectedly required detachment");
        return defaultExpression.get().box();
      }
    }
  }

  private static Expression getFieldProviderOrDefault(
      String name, Expression record, @Nullable SoyExpression defaultValue) {
    // NOTE: for compatibility with Tofu and jssrc we do not check for missing required parameters
    // here instead they will just turn into UndefinedData.  Existing templates depend on this.
    if (defaultValue == null) {
      return MethodRefs.PARAM_STORE_GET_PARAMETER.invoke(
          record, BytecodeUtils.constantRecordProperty(name));
    } else {
      return MethodRefs.PARAM_STORE_GET_PARAMETER_DEFAULT.invoke(
          record, BytecodeUtils.constantRecordProperty(name), defaultValue.box());
    }
  }

  /**
   * Generates the main method that selects the implementation out of the map. Generates code like:
   *
   * <pre>{@code
   * public static RenderResult fooTemplate(
   *     SoyRecord params,
   *     SoyRecord ijData,
   *     LoggingAdvisingAppendable appendable,
   *     RenderContext renderContext) {
   *   return renderContext
   *       .getDelTemplate(
   *           "fooTemplate",
   *           params.getFieldProvider("__modifiable_variant__", "").resolve().coerceToString())
   *       .render(params, ijData, appendable, renderContext);
   * }
   * }</pre>
   */
  private void generateModifiableSelectMethod() {
    if (template.modifiableSelectMethod().isEmpty()) {
      return;
    }
    Method method = template.renderMethod().method();
    Label start = new Label();
    Label end = new Label();

    ImmutableList.Builder<String> paramNames = ImmutableList.builder();
    paramNames
        .add(StandardNames.PARAMS)
        .add(StandardNames.APPENDABLE)
        .add(StandardNames.RENDER_CONTEXT);

    TemplateVariableManager variableSet =
        new TemplateVariableManager(
            template.typeInfo().type(),
            method.getArgumentTypes(),
            paramNames.build(),
            start,
            end,
            /* isStatic= */ true);
    Expression paramsVar = variableSet.getVariable(StandardNames.PARAMS);
    Expression appendableVar = variableSet.getVariable(StandardNames.APPENDABLE);
    RenderContextExpression context =
        new RenderContextExpression(variableSet.getVariable(StandardNames.RENDER_CONTEXT));

    TemplateBasicNode templateBasicNode = (TemplateBasicNode) templateNode;
    Expression renderExpression =
        context.renderModifiable(
            modifiableImplsMapKey(templateBasicNode), paramsVar, appendableVar);

    Statement returnExpression = Statement.returnExpression(renderExpression);

    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        returnExpression.gen(adapter);
        adapter.mark(end);
        variableSet.generateTableEntries(adapter);
      }
    }.writeIOExceptionMethod(
        methodAccess(), template.modifiableSelectMethod().get().method(), writer);
  }

  private static final class TemplateVariables implements TemplateParameterLookup {
    private final TemplateVariableManager variableSet;
    private final Optional<? extends Expression> paramsRecord;
    private final RenderContextExpression renderContext;

    TemplateVariables(
        TemplateVariableManager variableSet,
        Optional<? extends Expression> paramsRecord,
        RenderContextExpression renderContext) {
      this.variableSet = variableSet;
      this.paramsRecord = paramsRecord;
      this.renderContext = renderContext;
    }

    @Override
    public Expression getParam(TemplateParam param) {
      return variableSet.getVariable(param.name());
    }

    @Override
    public Expression getParamsRecord() {
      return paramsRecord.get();
    }

    @Override
    public Expression getLocal(AbstractLocalVarDefn<?> local) {
      return variableSet.getVariable(local.name());
    }

    @Override
    public Expression getLocal(SyntheticVarName varName) {
      return variableSet.getVariable(varName);
    }

    @Override
    public RenderContextExpression getRenderContext() {
      return renderContext;
    }
  }
}
