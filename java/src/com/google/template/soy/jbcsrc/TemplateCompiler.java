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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.soytree.SoyTreeUtils.allNodesOfType;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn.Kind;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.SoyNodeCompiler.CompiledMethodBody;
import com.google.template.soy.jbcsrc.internal.InnerClasses;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.AnnotationRef;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.RecordToPositionalCallFactory;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.TemplateType;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * Compiles the top level {@link CompiledTemplate} class for a single template and all related
 * classes.
 */
final class TemplateCompiler {
  private static final AnnotationRef<TemplateMetadata> TEMPLATE_METADATA_REF =
      AnnotationRef.forType(TemplateMetadata.class);

  private final FieldManager fields;
  private final CompiledTemplateMetadata template;
  private final TemplateNode templateNode;
  private final InnerClasses innerClasses;
  private final SoyClassWriter writer;
  private final TemplateAnalysis analysis;
  private final JavaSourceFunctionCompiler javaSourceFunctionCompiler;

  TemplateCompiler(
      TemplateNode templateNode,
      SoyClassWriter writer,
      FieldManager fields,
      InnerClasses innerClasses,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler) {
    this.template = CompiledTemplateMetadata.create(templateNode);
    this.templateNode = templateNode;
    this.writer = writer;
    this.fields = fields;
    this.innerClasses = innerClasses;
    this.analysis = TemplateAnalysis.analyze(templateNode);
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
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

    generateTemplateMethod();
    generateDelegateRenderMethod();
    generateRenderMethod();
  }

  private static final Handle METAFACTORY_HANDLE =
      MethodRef.create(
              LambdaMetafactory.class,
              "metafactory",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              MethodType.class,
              MethodHandle.class,
              MethodType.class)
          .asHandle();

  private static final Handle DELEGATE_FACTORY_HANDLE =
      MethodRef.create(
              RecordToPositionalCallFactory.class,
              "bootstrapDelegate",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              MethodHandle.class,
              String[].class)
          .asHandle();

  private static final String COMPILED_TEMPLATE_INIT_DESCRIPTOR =
      Type.getMethodDescriptor(BytecodeUtils.COMPILED_TEMPLATE_TYPE);

  private static final Type COMPILED_TEMPLATE_RENDER_DESCRIPTOR =
      Type.getMethodType(
          BytecodeUtils.RENDER_RESULT_TYPE,
          BytecodeUtils.SOY_RECORD_TYPE,
          BytecodeUtils.SOY_RECORD_TYPE,
          BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
          BytecodeUtils.RENDER_CONTEXT_TYPE);

  private void generateTemplateMethod() {
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
    Handle renderHandle = template.renderMethod().asHandle();
    Statement methodBody =
        Statement.returnExpression(
            new Expression(BytecodeUtils.COMPILED_TEMPLATE_TYPE) {
              @Override
              protected void doGen(CodeBuilder cb) {
                cb.visitInvokeDynamicInsn(
                    "render",
                    COMPILED_TEMPLATE_INIT_DESCRIPTOR,
                    METAFACTORY_HANDLE,
                    COMPILED_TEMPLATE_RENDER_DESCRIPTOR,
                    renderHandle,
                    COMPILED_TEMPLATE_RENDER_DESCRIPTOR);
              }
            });
    CodeBuilder methodWriter =
        new CodeBuilder(
            methodAccess(), template.templateMethod().method(), /* exceptions=*/ null, writer);
    generateTemplateMetadata(methodWriter);
    methodBody.writeMethodTo(methodWriter);
  }

  private int methodAccess() {
    // TODO(lukes): private templates need to have default access so they can be called by our
    // generated inner classes (e.g. a let).  Once jdk11 has landed we could workaround by declaring
    // our inner classes as 'nestmates' see https://openjdk.java.net/jeps/181
    return (templateNode.getVisibility() == Visibility.PUBLIC ? Opcodes.ACC_PUBLIC : 0)
        | Opcodes.ACC_STATIC;
  }

  /** Writes a {@link TemplateMetadata} to the generated {@code template()} method. */
  private void generateTemplateMetadata(CodeBuilder builder) {
    ContentKind kind =
        Converters.contentKindfromSanitizedContentKind(templateNode.getContentKind());

    // using linked hash sets below for determinism
    Set<String> uniqueIjs =
        allNodesOfType(templateNode, VarRefNode.class)
            .filter(VarRefNode::isInjected)
            .map(VarRefNode::getNameWithoutLeadingDollar)
            .collect(toImmutableSet());

    Set<String> callees =
        allNodesOfType(templateNode, TemplateLiteralNode.class)
            .map(TemplateLiteralNode::getResolvedName)
            .collect(toImmutableSet());

    Set<String> delCallees =
        allNodesOfType(templateNode, CallDelegateNode.class)
            .map(CallDelegateNode::getDelCalleeName)
            .collect(toImmutableSet());

    TemplateMetadata.DelTemplateMetadata deltemplateMetadata;
    if (templateNode.getKind() == SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {
      TemplateDelegateNode delegateNode = (TemplateDelegateNode) templateNode;
      deltemplateMetadata =
          createDelTemplateMetadata(
              nullToEmpty(delegateNode.getDelPackageName()),
              delegateNode.getDelTemplateName(),
              delegateNode.getDelTemplateVariant());
    } else {
      deltemplateMetadata = createDefaultDelTemplateMetadata();
    }
    Set<String> namespaces = Sets.newLinkedHashSet();
    // This ordering is critical to preserve css hierarchy.
    namespaces.addAll(templateNode.getParent().getRequiredCssNamespaces());
    namespaces.addAll(templateNode.getRequiredCssNamespaces());
    TemplateMetadata metadata =
        createTemplateMetadata(
            kind, namespaces, uniqueIjs, callees, delCallees, deltemplateMetadata);
    TEMPLATE_METADATA_REF.write(metadata, builder);
  }

  @AutoAnnotation
  static TemplateMetadata createTemplateMetadata(
      ContentKind contentKind,
      Set<String> requiredCssNames,
      Set<String> injectedParams,
      Set<String> callees,
      Set<String> delCallees,
      TemplateMetadata.DelTemplateMetadata deltemplateMetadata) {
    return new AutoAnnotation_TemplateCompiler_createTemplateMetadata(
        contentKind, requiredCssNames, injectedParams, callees, delCallees, deltemplateMetadata);
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
    Handle renderHandle = template.positionalRenderMethod().get().asHandle();
    Statement.returnExpression(
            new Expression(BytecodeUtils.COMPILED_TEMPLATE_TYPE) {
              @Override
              protected void doGen(CodeBuilder cb) {
                cb.loadArgs();
                cb.visitInvokeDynamicInsn(
                    "delegate",
                    COMPILED_TEMPLATE_RENDER_DESCRIPTOR.getDescriptor(),
                    DELEGATE_FACTORY_HANDLE,
                    Stream.concat(
                            Stream.of(renderHandle),
                            template.templateType().getActualParameters().stream()
                                .map(TemplateType.Parameter::getName))
                        .toArray(Object[]::new));
              }
            })
        .writeMethod(methodAccess(), template.renderMethod().method(), writer);
  }

  private void generateRenderMethod() {
    BasicExpressionCompiler constantCompiler =
        ExpressionCompiler.createConstantCompiler(
            analysis,
            new SimpleLocalVariableManager(
                template.typeInfo().type(), BytecodeUtils.CLASS_INIT, /* isStatic=*/ true),
            javaSourceFunctionCompiler);
    final Label start = new Label();
    final Label end = new Label();
    ImmutableList.Builder<String> paramNames = ImmutableList.builder();
    if (template.hasPositionalSignature()) {
      paramNames.addAll(
          template.templateType().getActualParameters().stream()
              .map(p -> p.getName())
              .collect(toList()));
    } else {
      paramNames.add(StandardNames.PARAMS);
    }
    paramNames.add(StandardNames.IJ);
    paramNames.add(StandardNames.APPENDABLE);
    paramNames.add(StandardNames.RENDER_CONTEXT);
    Method method = template.positionalRenderMethod().orElse(template.renderMethod()).method();
    final TemplateVariableManager variableSet =
        new TemplateVariableManager(
            template.typeInfo().type(), method, paramNames.build(), start, end, /*isStatic=*/ true);
    Optional<Expression> paramsVar =
        template.hasPositionalSignature()
            ? Optional.empty()
            : Optional.of(variableSet.getVariable(StandardNames.PARAMS));
    Expression ijVar = variableSet.getVariable(StandardNames.IJ);
    TemplateVariables variables =
        new TemplateVariables(
            variableSet,
            paramsVar,
            ijVar,
            new RenderContextExpression(variableSet.getVariable(StandardNames.RENDER_CONTEXT)));
    AppendableExpression appendable =
        AppendableExpression.forExpression(
            variableSet.getVariable(StandardNames.APPENDABLE).asNonNullable());
    SoyNodeCompiler nodeCompiler =
        SoyNodeCompiler.create(
            analysis,
            innerClasses,
            appendable,
            variableSet,
            variables,
            fields,
            constantCompiler,
            javaSourceFunctionCompiler);
    // Allocate local variables for all declared parameters.
    // NOTE: we initialize the parameters prior to where the jump table is initialized, this means
    // that all variables will be re-initialized ever time we re-enter the template.
    // TODO(lukes): move into SoyNodeCompiler.compile?  The fact that we construct SoyNodeCompiler
    // and then pull stuff out of it to initialize params is awkward
    TemplateVariableManager.Scope templateScope = variableSet.enterScope();
    List<Statement> paramInitStatements = new ArrayList<>();
    for (TemplateParam param : templateNode.getAllParams()) {
      SoyExpression defaultValue =
          param.hasDefault()
              ? getDefaultValue(param, nodeCompiler.exprCompiler, constantCompiler)
              : null;
      Expression initialValue;
      LocalVariable localVariable;
      if (param.isInjected()) {
        initialValue = getFieldProviderOrDefault(param.name(), ijVar, defaultValue);
        localVariable = templateScope.createNamedLocal(param.name(), initialValue.resultType());
        paramInitStatements.add(
            localVariable.store(initialValue).labelStart(localVariable.start()));
      } else if (paramsVar.isPresent()) {
        initialValue = getFieldProviderOrDefault(param.name(), paramsVar.get(), defaultValue);
        localVariable = templateScope.createNamedLocal(param.name(), initialValue.resultType());
        paramInitStatements.add(
            localVariable.store(initialValue).labelStart(localVariable.start()));
      } else {
        localVariable = (LocalVariable) variableSet.getVariable(param.name());
        paramInitStatements.add(
            localVariable.store(
                defaultValue == null
                    ? MethodRef.RUNTIME_PARAM.invoke(localVariable)
                    : MethodRef.RUNTIME_PARAM_OR_DEFAULT.invoke(
                        localVariable, defaultValue.box())));
      }
    }
    final CompiledMethodBody methodBody =
        SoyNodeCompiler.create(
                analysis,
                innerClasses,
                appendable,
                variableSet,
                variables,
                fields,
                constantCompiler,
                javaSourceFunctionCompiler)
            .compile(
                templateNode,
                /* prefix= */ ExtraCodeCompiler.NO_OP,
                /* suffix= */ ExtraCodeCompiler.NO_OP);
    final Statement exitTemplateScope = templateScope.exitScope();
    final Statement returnDone = Statement.returnExpression(MethodRef.RENDER_RESULT_DONE.invoke());
    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        for (Statement paramInitStatement : paramInitStatements) {
          paramInitStatement.gen(adapter);
        }
        methodBody.body().gen(adapter);
        exitTemplateScope.gen(adapter);
        adapter.mark(end);
        returnDone.gen(adapter);

        variableSet.generateTableEntries(adapter);
      }
    }.writeIOExceptionMethod(methodAccess(), method, writer);
    writer.setNumDetachStates(methodBody.numberOfDetachStates());
  }

  // TODO(lukes): it seems like this should actually compile params to SoyValueProvider instances
  private SoyExpression getDefaultValue(
      TemplateHeaderVarDefn headerVar,
      ExpressionCompiler expressionCompiler,
      BasicExpressionCompiler constantCompiler) {
    ExprRootNode defaultValueNode = headerVar.defaultValue();
    if (defaultValueNode.getType() == NullType.getInstance()) {
      // a special case for null to avoid poor handling elsewhere in the compiler.
      // TODO(lukes): should this just 'return null'?
      return SoyExpression.forSoyValue(
          headerVar.type(),
          BytecodeUtils.constantNull(SoyRuntimeType.getBoxedType(headerVar.type()).runtimeType()));
    } else {
      if (ExpressionCompiler.canCompileToConstant(defaultValueNode)) {
        SoyExpression defaultValue = constantCompiler.compile(defaultValueNode);
        if (!defaultValue.isCheap()) {
          FieldRef ref;
          if (headerVar.kind() == Kind.STATE) {
            // State fields are package private so that lazy closures can access them directly.
            ref = fields.addPackagePrivateStaticField(headerVar.name(), defaultValue);
          } else {
            ref = fields.addStaticField("default$" + headerVar.name(), defaultValue);
          }
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
    // here instead they will just turn into null.  Existing templates depend on this.
    if (defaultValue == null) {
      return MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(record, BytecodeUtils.constant(name));
    } else {
      return MethodRef.RUNTIME_GET_FIELD_PROVIDER_DEFAULT.invoke(
          record, BytecodeUtils.constant(name), defaultValue.box());
    }
  }

  private static final class TemplateVariables implements TemplateParameterLookup {
    private final TemplateVariableManager variableSet;
    private final Optional<? extends Expression> paramsRecord;
    private final Expression ijRecord;
    private final RenderContextExpression renderContext;

    TemplateVariables(
        TemplateVariableManager variableSet,
        Optional<? extends Expression> paramsRecord,
        Expression ijRecord,
        RenderContextExpression renderContext) {
      this.variableSet = variableSet;
      this.paramsRecord = paramsRecord;
      this.ijRecord = ijRecord;
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
    public Expression getIjRecord() {
      return ijRecord;
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
