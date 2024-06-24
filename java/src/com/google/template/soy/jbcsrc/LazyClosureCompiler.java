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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantSanitizedContentKindAsContentKind;
import static com.google.template.soy.soytree.SoyTreeUtils.isDescendantOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOfRange;

import com.google.auto.value.AutoValue;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProvider;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProviderProvider;
import com.google.template.soy.jbcsrc.shared.DetachableProviderFactory;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * A compiler for lazy closures.
 *
 * <p>Certain Soy operations trigger lazy execution, in particular {@code {let ...}} and {@code
 * {param ...}} statements. This laziness allows Soy rendering to both limit the amount of temporary
 * buffers that must be used as well as to delay evaluating expressions until the results are needed
 * (expression evaluation may trigger detaches).
 *
 * <p>There are 2 kinds of lazy execution:
 *
 * <ul>
 *   <li>Lazy expression evaluation. Triggered by {@link LetValueNode} or {@link
 *       CallParamValueNode}. For each of these we will generate a subtype of {@link
 *       DetachableSoyValueProvider}.
 *   <li>Lazy content evaluation. Triggered by {@link LetContentNode} or {@link
 *       CallParamContentNode}. For each of these we will generate a subtype of {@link
 *       DetachableContentProvider} and appropriately wrap it around a {@link SanitizedContent} or
 *       {@link StringData} value.
 * </ul>
 *
 * <p>Each of these lazy statements execute in the context of their parents and have access to all
 * the local variables and parameters of their parent templates at the point of their definition. To
 * implement this, the child will be passed references to all needed data explicitly at the point of
 * definition. To do this we will identify all the data that will be referenced by the closure and
 * pass it as explicit constructor parameters and will store them in fields. So that, for a template
 * like:
 *
 * <pre>{@code
 * {template foo}
 *   {{@literal @}param a : int}
 *   {let b : $a  + 1 /}
 *   {$b}
 * {/template}
 * }</pre>
 *
 * <p>The compiled result will look something like:
 *
 * <pre>{@code
 * ...
 * SoyValueProvider b = (SoyValueProvider) b(true, context, params.getFieldProvider("a"));
 * b.renderAndResult(out);
 * ...
 *
 * private static Object $b(boolean optimistic, RenderContext context, SoyValueProvider a) {
 *    RenderResult result = a.status();
 *    if (!result.isDone()) {
 *
 *      return result;
 *    }
 *    return result.getSoyValue();
 *    return RenderResult.done();
 * }
 * }</pre>
 */
final class LazyClosureCompiler {
  @AutoValue
  abstract static class LazyClosure {
    static LazyClosure create(
        String name,
        Expression soyValueProvider,
        boolean isTrivial,
        boolean requiresDetachLogicToResolve) {
      soyValueProvider.checkAssignableTo(SOY_VALUE_PROVIDER_TYPE);

      return new AutoValue_LazyClosureCompiler_LazyClosure(
          name, soyValueProvider, isTrivial, requiresDetachLogicToResolve);
    }

    abstract String name();

    abstract Expression soyValueProvider();

    abstract boolean isTrivial();

    abstract boolean requiresDetachLogicToResolve();
  }

  private static final Type DETACHABLE_SOY_VALUE_PROVIDER_TYPE =
      Type.getType(DetachableSoyValueProvider.class);
  private static final Type DETACHABLE_SOY_VALUE_PROVIDER_PROVIDER_TYPE =
      Type.getType(DetachableSoyValueProviderProvider.class);
  private static final Handle BOOTSTRAP_DETACHABLE_SOY_VALUE_PROVIDER =
      MethodRef.createPure(
              DetachableProviderFactory.class,
              "bootstrapDetachableSoyValueProvider",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class)
          .asHandle();

  private static final Handle BOOTSTRAP_DETACHABLE_SOY_VALUE_PROVIDER_PROVIDER =
      MethodRef.createPure(
              DetachableProviderFactory.class,
              "bootstrapDetachableSoyValueProviderProvider",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class)
          .asHandle();

  private static final Handle BOOTSTRAP_LAZY_CONTENT_PROVIDER =
      MethodRef.createPure(
              DetachableProviderFactory.class,
              "bootstrapDetachableContentProvider",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class)
          .asHandle();

  private static final MethodRef CATCH_AS_PROVIDER =
      MethodRef.createPure(RenderContext.class, "catchAsProvider", Throwable.class);

  private final SoyNodeCompiler parent;

  LazyClosureCompiler(SoyNodeCompiler parent) {
    this.parent = parent;
  }

  /** Returns true if evaluating the RenderUnitNode will require generating detach logic. */
  private boolean requiresDetachLogic(RenderUnitNode root) {
    checkState(!(root instanceof TemplateNode)); // this logic isn't correct for templates
    return SoyTreeUtils.allNodes(
            root,
            n -> n instanceof ExprNode ? VisitDirective.SKIP_CHILDREN : VisitDirective.CONTINUE)
        .anyMatch(
            node -> {
              if (node instanceof ExprNode) {
                return ExpressionCompiler.requiresDetach(parent.analysis, (ExprNode) node);
              }
              // Call nodes always require detach logic.  This is important for encapsulation
              // reasons.  A template may occasionally require no detach states, but we don't know
              // that statically because it isn't part of the signature.  We could potentially
              // break encapsulation for private templates.
              if (node instanceof CallNode) {
                return true;
              }
              return false;
            });
  }

  LazyClosure compileLazyExpression(SoyNode declaringNode, String varName, ExprRootNode exprNode) {
    String name = getMethodName(declaringNode);
    if (ExpressionCompiler.canCompileToConstant(declaringNode, exprNode)) {
      SoyExpression expression = parent.constantCompiler.compile(exprNode).box().toMaybeConstant();
      Expression value =
          BytecodeUtils.getSoleValue(expression.resultType())
              .orElseGet(
                  () ->
                      expression.isConstant()
                          ? expression
                          : parent.fields.addStaticField(name, expression).accessor());
      return LazyClosure.create(
          varName, value, /* isTrivial= */ true, /* requiresDetachLogicToResolve= */ false);
    }
    Optional<Expression> asSoyValueProvider =
        parent.expressionToSoyValueProviderCompiler.compileAvoidingDetaches(exprNode);
    if (asSoyValueProvider.isPresent()) {
      Expression svp = asSoyValueProvider.get();
      return LazyClosure.create(
          varName,
          svp,
          /* isTrivial= */ exprNode.getRoot().getKind() == ExprNode.Kind.VAR_REF_NODE,
          // It is possible that even if we could compile to a SoyValueProvider while avoiding
          // detaches, that it might still require a detach operation to resolve the expression.
          /* requiresDetachLogicToResolve= */ ExpressionCompiler.requiresDetach(
              parent.analysis, exprNode));
    }

    Optional<Expression> asSoyValueProviderProvider =
        new CompilationUnit(declaringNode)
            .compileExpressionToSoyValueProviderIfUseful(name, exprNode);

    if (asSoyValueProviderProvider.isPresent()) {
      return LazyClosure.create(
          varName,
          asSoyValueProviderProvider.get(),
          /* isTrivial= */ false,
          // this must be true because we already failed when trying to compileAvoidingDetaches.
          /* requiresDetachLogicToResolve= */ true);
    }

    Expression expr = new CompilationUnit(declaringNode).compileExpression(name, exprNode);

    return LazyClosure.create(
        varName,
        expr,
        /* isTrivial= */ false,
        // this must be true, otherwise one of our earlier cases would have triggered.
        /* requiresDetachLogicToResolve= */ true);
  }

  LazyClosure compileLazyContent(RenderUnitNode renderUnit, String varName) {
    return compileLazyContent(
        Optional.empty(), renderUnit, varName, ExtraCodeCompiler.NO_OP, ExtraCodeCompiler.NO_OP);
  }

  LazyClosure compileLazyContent(
      String customPrefix,
      RenderUnitNode renderUnit,
      String varName,
      ExtraCodeCompiler prefix,
      ExtraCodeCompiler suffix) {
    return compileLazyContent(Optional.of(customPrefix), renderUnit, varName, prefix, suffix);
  }

  private LazyClosure compileLazyContent(
      Optional<String> customPrefix,
      RenderUnitNode renderUnit,
      String varName,
      ExtraCodeCompiler prefix,
      ExtraCodeCompiler suffix) {
    String name = getMethodName(customPrefix, renderUnit);

    // Attempt to compile the whole thing to a string if possible.  The presence of a non-trivial
    // ExtraCodeCompiler means that it isn't just textual.
    Optional<Expression> asRawText =
        prefix == ExtraCodeCompiler.NO_OP && suffix == ExtraCodeCompiler.NO_OP
            ? asRawTextOnly(renderUnit)
            : Optional.empty();
    if (asRawText.isPresent()) {
      return LazyClosure.create(
          varName,
          asRawText.get(),
          /* isTrivial= */ true,
          /* requiresDetachLogicToResolve= */ false);
    }
    if (!prefix.requiresDetachLogic(parent.analysis)
        && !suffix.requiresDetachLogic(parent.analysis)
        && !requiresDetachLogic(renderUnit)) {
      // We can evaluate this inline by writing everything into a new buffer!
      // because there are no detaches
      return LazyClosure.create(
          varName,
          renderIntoBuffer(renderUnit, prefix, suffix),
          /* isTrivial= */ false,
          /* requiresDetachLogicToResolve= */ false);
    }
    Expression expr =
        new CompilationUnit(renderUnit).compileRenderable(name, renderUnit, prefix, suffix);

    return LazyClosure.create(
        varName,
        expr,
        /* isTrivial= */ false,
        // because our check above failed, this must be true
        /* requiresDetachLogicToResolve= */ true);
  }

  private Expression renderIntoBuffer(
      RenderUnitNode renderUnitNode, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    TemplateVariableManager.Scope scope = parent.variables.enterScope();
    LocalVariable variable =
        scope
            .createTemporary(
                StandardNames.BUFFER, MethodRefs.LOGGING_ADVISING_APPENDABLE_BUFFERING.returnType())
            .asNonJavaNullable();
    Statement initBuffer =
        variable.initialize(
            AppendableExpression.forExpression(
                MethodRefs.LOGGING_ADVISING_APPENDABLE_BUFFERING.invoke(
                    BytecodeUtils.constantSanitizedContentKindAsContentKind(
                        renderUnitNode.getContentKind()))));
    Statement populateBuffer =
        parent
            .compilerWithNewAppendable(AppendableExpression.forExpression(variable))
            .compileWithoutDetaches(renderUnitNode, prefix, suffix);

    return Statement.concat(initBuffer, populateBuffer, scope.exitScope())
        .then(variable.invoke(getUnpackBufferingAppendableMethod(renderUnitNode.getContentKind())));
  }

  private static MethodRef getUnpackBufferingAppendableMethod(SanitizedContentKind kind) {
    if (kind == SanitizedContentKind.TEXT) {
      return MethodRefs.BUFFERING_APPENDABLE_GET_AS_STRING_DATA;
    } else {
      return MethodRefs.BUFFERING_APPENDABLE_GET_AS_SANITIZED_CONTENT;
    }
  }

  /**
   * Returns an SoyValueProvider expression for the given RenderUnitNode if it is composed of only
   * raw text.
   */
  private Optional<Expression> asRawTextOnly(RenderUnitNode renderUnit) {
    StringBuilder builder = null;
    List<SoyNode> children = new ArrayList<>(renderUnit.getChildren());
    for (int i = 0; i < children.size(); i++) {
      SoyNode child = children.get(i);
      if (child instanceof MsgHtmlTagNode) {
        // by the time MsgHtmlTagNodes hit the code generator the HtmlTagNode instances they wrap
        // have been desugared into RawTextNodes (and other things).
        children.addAll(i + 1, ((MsgHtmlTagNode) child).getChildren());
        continue;
      }
      if (child instanceof RawTextNode) {
        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(((RawTextNode) child).getRawText());
      } else {
        return Optional.empty();
      }
    }

    Expression value = constant(builder == null ? "" : builder.toString());
    SanitizedContentKind kind = renderUnit.getContentKind();
    if (kind == SanitizedContentKind.TEXT) {
      value = MethodRefs.STRING_DATA_FOR_VALUE.invoke(value);
    } else {
      value =
          MethodRefs.ORDAIN_AS_SAFE.invoke(value, constantSanitizedContentKindAsContentKind(kind));
    }
    return Optional.of(value.toConstantExpression());
  }

  /** Returns the name to use for the method generated for the given node. */
  private static String getMethodName(SoyNode declaringNode) {
    return getMethodName(Optional.empty(), declaringNode);
  }

  /**
   * Returns the name to use for the method generated for the given node. The prefix can be used to
   * customize the otherwise implict prefix applied to the first node.
   */
  private static String getMethodName(Optional<String> customPrefix, SoyNode declaringNode) {
    checkArgument(
        declaringNode.getKind() == SoyNode.Kind.CALL_PARAM_VALUE_NODE
            || declaringNode.getKind() == SoyNode.Kind.CALL_PARAM_CONTENT_NODE
            || declaringNode.getKind() == SoyNode.Kind.LET_VALUE_NODE
            || declaringNode.getKind() == SoyNode.Kind.LET_CONTENT_NODE);
    String name = null;
    while (declaringNode != null) {
      String part = null;
      switch (declaringNode.getKind()) {
        case LET_VALUE_NODE:
        case LET_CONTENT_NODE:
          part = customPrefix.orElse("let") + '_' + ((LetNode) declaringNode).getVarName();
          break;
        case CALL_PARAM_CONTENT_NODE:
        case CALL_PARAM_VALUE_NODE:
          part =
              customPrefix.orElse("param")
                  + '_'
                  + ((CallParamNode) declaringNode).getKey().identifier();
          break;
        case TEMPLATE_BASIC_NODE:
        case TEMPLATE_ELEMENT_NODE:
        case TEMPLATE_DELEGATE_NODE:
          part =
              Names.renderMethodNameFromSoyTemplateName(
                  ((TemplateNode) declaringNode).getTemplateName());
          break;
        default:
          break;
      }
      if (part != null) {
        name = name == null ? part : part + '$' + name;
      }
      declaringNode = declaringNode.getParent();
      customPrefix = Optional.empty(); // prefix only applied on the first iteration
    }
    return checkNotNull(name);
  }

  /** Constructs and registers a new method for one of our closures. */
  private MethodRef createClosureMethod(
      String methodName,
      LazyClosureParameterLookup lookup,
      TemplateVariableManager variableSet,
      Statement methodBody,
      boolean hasOptimisticParameter,
      Type returnType) {
    List<Type> paramTypes = new ArrayList<>();
    List<String> paramNames = new ArrayList<>();
    int slot = 0;
    if (hasOptimisticParameter) {
      paramTypes.add(Type.BOOLEAN_TYPE);
      paramNames.add(StandardNames.OPTIMISTIC);
      slot += Type.BOOLEAN_TYPE.getSize();
    }
    for (var capture : lookup.getCaptures()) {
      var captureType = capture.childExpression.resultType();
      paramTypes.add(captureType);
      paramNames.add(capture.paramName);
      capture.setLocal(slot);
      slot += captureType.getSize();
    }
    Method method = new Method(methodName, returnType, paramTypes.toArray(new Type[0]));
    // Now that we have generated the full method we know the names and types of all parameters
    // update the parameters
    variableSet.updateParameterTypes(method.getArgumentTypes(), paramNames);
    return parent.innerMethods.registerLazyClosureMethod(method, methodBody);
  }

  private static ExpressionDetacher.BasicDetacher createOptimisticDetacher(
      Handle bootstrapHandle, Type providerSubclassType) {
    Statement optimisticDetacher =
        new Statement() {
          @Override
          protected void doGen(CodeBuilder cb) {
            cb.visitVarInsn(Opcodes.ILOAD, 0); // load the optimistic parameter
            Label end = new Label();
            cb.ifZCmp(Opcodes.IFEQ, end); // if (optimistic) {
            var methodParameters = cb.getArgumentTypes();
            // Load all the arguments except the first one (the optimistic
            // parameter)
            var argTypes = copyOfRange(methodParameters, 1, methodParameters.length);
            int index = Type.BOOLEAN_TYPE.getSize();
            for (var argType : argTypes) {
              cb.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), index);
              index += argType.getSize();
            }
            cb.visitInvokeDynamicInsn(
                cb.getThisMethodName(),
                Type.getMethodDescriptor(providerSubclassType, argTypes),
                bootstrapHandle);
            cb.returnValue();
            cb.mark(end);
          }
        };
    return new ExpressionDetacher.BasicDetacher(Suppliers.ofInstance(optimisticDetacher));
  }

  interface Handler extends AutoCloseable {
    @Override
    public void close();
  }

  // TODO(b/289390227): remove the try/catch once we are sure it is safe.
  static Handler catchAsThrowingSoyValueProvider(
      CodeBuilder cb, Expression renderContext, boolean isOptimistic) {
    Label tryStart = new Label();
    Label tryEnd = new Label();

    Label handlerLabel = new Label();
    cb.visitTryCatchBlock(
        tryStart, tryEnd, handlerLabel, BytecodeUtils.THROWABLE_TYPE.getInternalName());
    cb.mark(tryStart);
    return () -> {
      cb.mark(tryEnd);
      cb.mark(handlerLabel);
      if (isOptimistic) {
        cb.visitVarInsn(Opcodes.ILOAD, 0); // load the optimistic parameter
        Label end = new Label();
        cb.ifZCmp(Opcodes.IFNE, end); // if (!optimistic) {
        cb.throwException(); // propagate the exception
        cb.mark(end); // }
      }
      // In the catch block the exception is at the top of the stack
      renderContext.gen(cb);
      cb.swap(); // swap the exception and the render context so we can call the method.
      CATCH_AS_PROVIDER.invokeUnchecked(cb);
      cb.returnValue();
    };
  }

  /** A simple object to aid in generating code for a single node. */
  private final class CompilationUnit {
    final SoyNode node;

    CompilationUnit(SoyNode node) {
      this.node = node;
    }

    Expression compileExpression(String methodName, ExprNode exprNode) {
      Label start = new Label();
      Label end = new Label();
      TemplateVariableManager variableSet =
          new TemplateVariableManager(
              parent.typeInfo.type(),
              new Type[0],
              /* parameterNames= */ ImmutableList.of(),
              start,
              end,
              /* isStatic= */ true);
      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(
              this, parent.parameterLookup, variableSet, Optional.empty());
      var detacher =
          createOptimisticDetacher(
              BOOTSTRAP_DETACHABLE_SOY_VALUE_PROVIDER, DETACHABLE_SOY_VALUE_PROVIDER_TYPE);
      SoyExpression compile =
          ExpressionCompiler.createBasicCompiler(
                  node,
                  parent.analysis,
                  lookup,
                  variableSet,
                  parent.javaSourceFunctionCompiler,
                  parent.fileSetMetadata,
                  detacher)
              .compile(exprNode);
      Expression expression = compile.box();
      boolean hasDetaches = detacher.hasDetaches();
      final Statement returnSvp = Statement.returnExpression(expression);
      Expression renderContextExpression = lookup.getRenderContext();
      Statement methodBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.mark(start);
              try (Handler handler =
                  catchAsThrowingSoyValueProvider(
                      cb, renderContextExpression, /* isOptimistic= */ hasDetaches)) {
                returnSvp.gen(cb);
              }
              cb.mark(end);
              variableSet.generateTableEntries(cb);
            }
          };

      MethodRef method =
          createClosureMethod(
              methodName,
              lookup,
              variableSet,
              methodBody,
              /* hasOptimisticParameter= */ hasDetaches,
              hasDetaches ? BytecodeUtils.OBJECT.type() : BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);

      return generateEagerCall(method, /* isOptimistic= */ hasDetaches, lookup);
    }

    Optional<Expression> compileExpressionToSoyValueProviderIfUseful(
        String methodName, ExprNode exprNode) {
      Label start = new Label();
      Label end = new Label();
      TemplateVariableManager variableSet =
          new TemplateVariableManager(
              /* owner= */ parent.typeInfo.type(),
              new Type[0],
              /* parameterNames= */ ImmutableList.of(),
              start,
              end,
              /* isStatic= */ true);

      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(
              this, parent.parameterLookup, variableSet, Optional.empty());
      var detacher =
          createOptimisticDetacher(
              BOOTSTRAP_DETACHABLE_SOY_VALUE_PROVIDER_PROVIDER,
              DETACHABLE_SOY_VALUE_PROVIDER_PROVIDER_TYPE);

      ExpressionCompiler expressionCompiler =
          ExpressionCompiler.create(
              node,
              parent.analysis,
              lookup,
              variableSet,
              parent.javaSourceFunctionCompiler,
              parent.fileSetMetadata);
      Optional<Expression> expr =
          ExpressionToSoyValueProviderCompiler.create(parent.analysis, expressionCompiler, lookup)
              .compileToSoyValueProviderIfUsefulToPreserveStreaming(exprNode, detacher);

      if (expr.isEmpty()) {
        return Optional.empty();
      }

      boolean hasDetaches = detacher.hasDetaches();
      Expression renderContextExpression = lookup.getRenderContext();
      Expression expression = expr.get();
      final Statement returnSvp = Statement.returnExpression(expression);
      Statement methodBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.mark(start);
              try (Handler handler =
                  catchAsThrowingSoyValueProvider(
                      cb, renderContextExpression, /* isOptimistic= */ hasDetaches)) {
                returnSvp.gen(cb);
              }
              cb.mark(end);
              variableSet.generateTableEntries(cb);
            }
          };
      MethodRef method =
          createClosureMethod(
              methodName,
              lookup,
              variableSet,
              methodBody,
              /* hasOptimisticParameter= */ hasDetaches,
              hasDetaches ? BytecodeUtils.OBJECT.type() : BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
      return Optional.of(generateEagerCall(method, /* isOptimistic= */ hasDetaches, lookup));
    }

    /**
     * Returns true if it is possible to eagerly evaluate a `RenderUnitNode`.
     *
     * <p>Currently, due to certain Soy externs and plugins related to content deferral (e.g.
     * `flushLogsAndRender`) it is not possible to optimistically evaluate any html or attributes
     * content. By making html deferral a built-in feature we should be able to remove this
     * restriction.
     *
     * <p>See b/343267009 for more details.
     */
    private boolean canEagerlyRender(RenderUnitNode renderUnit) {
      switch (renderUnit.getContentKind()) {
        case HTML:
        case ATTRIBUTES:
        case HTML_ELEMENT:
          return false;
        case TRUSTED_RESOURCE_URI:
        case TEXT:
        case CSS:
        case JS:
        case URI:
          return true;
      }
      throw new AssertionError("unreachable");
    }

    Expression compileRenderable(
        String methodName,
        RenderUnitNode renderUnit,
        ExtraCodeCompiler prefix,
        ExtraCodeCompiler suffix) {

      Label start = new Label();
      Label end = new Label();
      TemplateVariableManager variableSet =
          new TemplateVariableManager(
              /* owner= */ null,
              new Type[0],
              /* parameterNames= */ ImmutableList.of(),
              start,
              end,
              /* isStatic= */ true);

      // The stackFrame and appendable parameters come last, but we don't know what values will be
      // captured yet so we need to defer computing its slot.
      LocalVariable stackFrameParameter =
          LocalVariable.createLocal(
              StandardNames.STACK_FRAME, 0, BytecodeUtils.STACK_FRAME_TYPE, start, end);
      LocalVariable appendableParameter =
          LocalVariable.createLocal(
                  StandardNames.APPENDABLE,
                  0,
                  // It will either be a buffering or a multiplexing appendable, but the latter is a
                  // subtype so this is accurate
                  BytecodeUtils.BUFFERING_APPENDABLE_TYPE,
                  start,
                  end)
              .asNonJavaNullable();
      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(
              this, parent.parameterLookup, variableSet, Optional.of(stackFrameParameter));

      SoyNodeCompiler soyNodeCompiler =
          parent.compilerForChildNode(
              node, variableSet, lookup, AppendableExpression.forExpression(appendableParameter));
      Statement nodeBody = soyNodeCompiler.compile(renderUnit, prefix, suffix);
      boolean isEager = !soyNodeCompiler.detachState.hasDetaches() && canEagerlyRender(renderUnit);

      // We only use to lazy rendering if the content type requires it.  See TODOs on
      // canEagerlyRender
      boolean useLazyRendering = !canEagerlyRender(renderUnit);
      Statement fullMethodBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.mark(start);
              nodeBody.gen(cb);
              cb.mark(end);
              if (isEager) {
                // We can simplify the 'eager' case slightly by just returning the appendable
                // directly.
                appendableParameter.gen(cb);
              } else {
                cb.pushNull(); // return the 'done' stackframe
              }
              cb.returnValue();
              variableSet.generateTableEntries(cb);
            }
          };
      List<Type> paramTypes = new ArrayList<>();
      List<String> paramNames = new ArrayList<>();
      int slot = 0;
      paramTypes.add(stackFrameParameter.resultType());
      paramNames.add(stackFrameParameter.variableName());
      stackFrameParameter.shiftIndex(slot);
      slot += stackFrameParameter.resultType().getSize();
      for (var capture : lookup.getCaptures()) {
        var captureType = capture.childExpression.resultType();
        paramTypes.add(captureType);
        paramNames.add(capture.paramName);
        capture.setLocal(slot);
        slot += captureType.getSize();
      }
      paramTypes.add(
          isEager
              ? BytecodeUtils.BUFFERING_APPENDABLE_TYPE
              : BytecodeUtils.MULTIPLEXING_APPENDABLE_TYPE);
      paramNames.add(appendableParameter.variableName());
      appendableParameter.shiftIndex(slot);
      var paramTypesArray = paramTypes.toArray(new Type[0]);
      // Now that we have generated the full method we know the names and types of all parameters
      // update the parameters
      variableSet.updateParameterTypes(paramTypesArray, paramNames);
      MethodRef method =
          parent.innerMethods.registerLazyClosureMethod(
              new Method(
                  methodName,
                  isEager
                      ? BytecodeUtils.BUFFERING_APPENDABLE_TYPE
                      : BytecodeUtils.STACK_FRAME_TYPE,
                  paramTypesArray),
              fullMethodBody);
      if (useLazyRendering) {
        return generateLazyCallToRenderable(
            renderUnit.getContentKind(), method.method().getName(), lookup);
      }
      return generateEagerCallToRenderable(
          renderUnit.getContentKind(), method, /* isOptimistic= */ !isEager, lookup);
    }

    /** Generates the initial call to a lazy expression function */
    private Expression generateLazyCallToRenderable(
        SanitizedContentKind kind, String implMethodName, LazyClosureParameterLookup lookup) {
      List<Expression> args = new ArrayList<>();
      args.add(BytecodeUtils.constantSanitizedContentKindAsContentKind(kind));
      for (ParentCapture capture : lookup.getCaptures()) {
        args.add(capture.parentExpression);
      }
      return new Expression(
          BytecodeUtils.SOY_VALUE_PROVIDER_TYPE, Features.of(Feature.NON_JAVA_NULLABLE)) {
        @Override
        protected void doGen(CodeBuilder adapter) {
          for (Expression arg : args) {
            arg.gen(adapter);
          }
          Type callSiteType =
              Type.getMethodType(
                  BytecodeUtils.SOY_VALUE_PROVIDER_TYPE,
                  args.stream().map(Expression::resultType).toArray(Type[]::new));
          adapter.visitInvokeDynamicInsn(
              /* name= */ implMethodName,
              /* descriptor= */ callSiteType.getDescriptor(),
              BOOTSTRAP_LAZY_CONTENT_PROVIDER);
        }
      };
    }

    /** Generates the initial call to a lazy expression function */
    private Expression generateEagerCall(
        MethodRef implMethodName, boolean isOptimistic, LazyClosureParameterLookup lookup) {
      List<Expression> args = new ArrayList<>();
      if (isOptimistic) {
        args.add(BytecodeUtils.constant(true)); // optimistic=true
      }
      for (ParentCapture capture : lookup.getCaptures()) {
        args.add(capture.parentExpression);
      }
      var result = implMethodName.invoke(args);
      if (isOptimistic) {
        return result.checkedCast(BytecodeUtils.SOY_VALUE_PROVIDER_TYPE);
      }
      checkArgument(
          BytecodeUtils.isPossiblyAssignableFrom(
              BytecodeUtils.SOY_VALUE_PROVIDER_TYPE, result.resultType()));
      return result;
    }

    private Expression accessParam(int slot, Type type, Features features) {
      return new Expression(type, features.plus(Feature.CHEAP)) {
        @Override
        protected void doGen(CodeBuilder adapter) {
          adapter.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
        }
      };
    }

    /** Generates a call to eagerly evaluate a 'renderable' function. */
    private Expression generateEagerCallToRenderable(
        SanitizedContentKind kind,
        MethodRef implMethod,
        boolean isOptimistic,
        LazyClosureParameterLookup lookup) {
      // In this case we can just call the method directly and do something like this:
      // var buffer = LoggingAdvisingAppendable.buffering();
      // implMethod(...args, buffer);
      // buffer.getAsSoyValue();
      //
      // However, for compatibility with lazy rendering we need to be able to catch exceptions and
      // capture in a ThrowingSoyValueProvider.  The problem with just adding a try catch block is
      // that all exceptions always pop the full stack!  This means if our caller generates the
      // returned expression at a non-zero stack depth (like what MsgCompiler does), then we will
      // generate bad code.  So instead for optimistic or eager evaluation we need to generate a
      // helper method to hold the try-catch.  Luckily the helper method is trivial.
      // static SoyValueProvider implMethodHelper(...args) {
      //   try {
      //     return implMethod(...args, LoggingAdvisingAppendable.buffering())).getAsSoyValue();
      //   } catch (Throwable t) {
      //     return ThrowingSoyValueProvider.create(t);
      //   }
      // }
      // TODO(b/289390227): remove the try...catch and just inline the call to the implMethod
      Expression buffer =
          (isOptimistic
                  ? MethodRefs.MULTIPLEXING_APPENDABLE
                  : MethodRefs.LOGGING_ADVISING_APPENDABLE_BUFFERING)
              .invoke(BytecodeUtils.constantSanitizedContentKindAsContentKind(kind));
      List<Expression> args = new ArrayList<>();
      List<Expression> parameters = new ArrayList<>();
      int slot = 0;
      Expression renderContext = null;
      for (ParentCapture capture : lookup.getCaptures()) {
        args.add(capture.parentExpression);
        var type = capture.childExpression.resultType();
        var param = accessParam(slot, type, capture.parentExpression.features());
        parameters.add(param);
        if (type.equals(BytecodeUtils.RENDER_CONTEXT_TYPE)) {
          renderContext = param;
        }
        slot += type.getSize();
      }
      if (renderContext == null) {
        args.add(parent.parameterLookup.getRenderContext());
        int renderContextSlot = slot;
        slot += BytecodeUtils.RENDER_CONTEXT_TYPE.getSize();
        renderContext =
            accessParam(
                renderContextSlot,
                BytecodeUtils.RENDER_CONTEXT_TYPE,
                Features.of(Feature.NON_JAVA_NULLABLE, Feature.CHEAP, Feature.NON_SOY_NULLISH));
      }
      Expression renderContextFinal = renderContext;
      int bufferSlot = slot;
      Statement helperMethodBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder cb) {
              try (Handler handler =
                  catchAsThrowingSoyValueProvider(
                      cb, renderContextFinal, /* isOptimistic= */ false)) {
                cb.pushNull(); // push the stack frame.
                for (var param : parameters) {
                  param.gen(cb);
                }
                buffer.gen(cb);
                if (isOptimistic) {
                  cb.visitVarInsn(Opcodes.ASTORE, bufferSlot);
                  cb.visitVarInsn(Opcodes.ALOAD, bufferSlot);
                  implMethod.invokeUnchecked(cb);
                  cb.dup();
                  Label done = new Label();
                  cb.ifNull(done);
                  // If we are not complete we need to capture the partial progress into a new
                  // DetachableContentProvider subclass
                  for (var param : parameters) {
                    param.gen(cb);
                  }
                  cb.visitVarInsn(Opcodes.ALOAD, bufferSlot);
                  cb.visitInvokeDynamicInsn(
                      implMethod.method().getName(),
                      Type.getMethodDescriptor(
                          BytecodeUtils.SOY_VALUE_PROVIDER_TYPE,
                          implMethod.method().getArgumentTypes()),
                      BOOTSTRAP_LAZY_CONTENT_PROVIDER);
                  cb.returnValue();
                  cb.mark(done);
                  cb.pop(); // pop the extra null stackframe
                  cb.visitVarInsn(Opcodes.ALOAD, bufferSlot);
                } else {
                  implMethod.invokeUnchecked(cb);
                }
                getUnpackBufferingAppendableMethod(kind).invokeUnchecked(cb);
                cb.returnValue();
              }
            }
          };
      Method helperMethod =
          new Method(
              implMethod.method().getName()
                  + (isOptimistic ? "$optimistic" : "$eager"), // better name?
              BytecodeUtils.SOY_VALUE_PROVIDER_TYPE,
              args.stream().map(Expression::resultType).toArray(Type[]::new));
      MethodRef helperMethodRef =
          parent.innerMethods.registerLazyClosureMethod(helperMethod, helperMethodBody);
      return helperMethodRef.invoke(args);
    }
  }

  /**
   * Represents a parameter captured from our parent. To capture a value from our parent we grab the
   * expression that produces that value and then generate a local parameter in the child with the
   * same type.
   *
   * <p>{@link CompilationUnit#generateConstructor} generates the code to propagate the captured
   * values from the parent to the child, and from the constructor to the generated fields.
   */
  private static final class ParentCapture {
    static ParentCapture create(String paramName, Expression parentExpression) {
      return new ParentCapture(paramName, parentExpression);
    }

    private int localSlot = -1;

    /** The field in the closure that stores the captured value. */
    final String paramName;

    /** An expression that produces the value for this capture from the parent. */
    final Expression parentExpression;

    final Expression childExpression;

    ParentCapture(String paramName, Expression parentExpression) {
      this.paramName = paramName;
      this.parentExpression = parentExpression;
      this.childExpression =
          new Expression(
              parentExpression.resultType(), parentExpression.features().plus(Feature.CHEAP)) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              checkArgument(ParentCapture.this.localSlot != -1, "didn't call 'setLocal'");
              adapter.visitVarInsn(
                  parentExpression.resultType().getOpcode(Opcodes.ILOAD), localSlot);
            }
          };
    }

    void setLocal(int argSlot) {
      checkState(localSlot == -1);
      this.localSlot = argSlot;
    }
  }


  /**
   * The {@link LazyClosureParameterLookup} will generate expressions for all variable references
   * within a lazy closure. The strategy is simple
   *
   * <ul>
   *   <li>If the variable is a template parameter, query the parent variable lookup and generate a
   *       {@link ParentCapture} for it
   *   <li>If the variable is a local (synthetic or otherwise), check if the declaring node is a
   *       descendant of the current lazy node. If it is, generate code for a normal variable lookup
   *       (via our own VariableSet), otherwise generate a {@link ParentCapture} to grab the value
   *       from our parent.
   *   <li>Finally, for the {@link RenderContext}, we lazily generate a {@link ParentCapture} if
   *       necessary.
   * </ul>
   */
  private static final class LazyClosureParameterLookup implements TemplateParameterLookup {
    private final CompilationUnit params;
    private final TemplateParameterLookup parentParameterLookup;
    private final TemplateVariableManager variableSet;

    // These fields track all the parent captures that we need to generate.
    // NOTE: TemplateParam and LocalVar have identity semantics.  But the AST is guaranteed to not
    // have multiple copies.
    private final Map<VarDefn, ParentCapture> variableCaptures = new LinkedHashMap<>();
    private final Map<SyntheticVarName, ParentCapture> syntheticCaptures = new LinkedHashMap<>();
    private ParentCapture renderContextCapture;
    private ParentCapture ijCapture;
    private Optional<ParentCapture> paramsCapture;
    private final Optional<LocalVariable> stackFrame;

    LazyClosureParameterLookup(
        CompilationUnit params,
        TemplateParameterLookup parentParameterLookup,
        TemplateVariableManager variableSet,
        Optional<LocalVariable> stackFrame) {
      this.params = params;
      this.parentParameterLookup = parentParameterLookup;
      this.variableSet = variableSet;
      this.stackFrame = stackFrame;
    }

    @Override
    public LocalVariable getStackFrame() {
      return stackFrame.get();
    }

    @Override
    public Expression getParam(TemplateParam param) {
      ParentCapture capture = variableCaptures.get(param);
      if (capture == null) {
        Expression expression = parentParameterLookup.getParam(param);
        capture = ParentCapture.create(param.name(), expression);
        variableCaptures.put(param, capture);
      }
      return capture.childExpression;
    }

    @Override
    public Optional<Expression> getParamsRecord() {
      if (paramsCapture == null) {
        var parentParams = parentParameterLookup.getParamsRecord();
        paramsCapture = parentParams.map(e -> ParentCapture.create(StandardNames.PARAMS, e));
      }
      return paramsCapture.map(c -> c.childExpression);
    }

    @Override
    public Expression getLocal(AbstractLocalVarDefn<?> local) {
      if (isDescendantOf(local.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(local.name());
      }

      ParentCapture capture = variableCaptures.get(local);
      if (capture == null) {
        Expression expression = parentParameterLookup.getLocal(local);
        capture = ParentCapture.create(local.name(), expression);
        variableCaptures.put(local, capture);
      }
      return capture.childExpression;
    }

    @Override
    public Expression getLocal(SyntheticVarName varName) {
      if (isDescendantOf(varName.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(varName);
      }

      ParentCapture capture = syntheticCaptures.get(varName);
      if (capture == null) {
        Expression expression = parentParameterLookup.getLocal(varName);
        capture = ParentCapture.create(varName.name(), expression);
        syntheticCaptures.put(varName, capture);
      }
      return capture.childExpression;
    }

    Iterable<ParentCapture> getCaptures() {
      return Iterables.concat(
          Iterables.filter(
              asList(
                  renderContextCapture,
                  ijCapture,
                  paramsCapture == null ? null : paramsCapture.orElse(null)),
              Objects::nonNull),
          variableCaptures.values(),
          syntheticCaptures.values());
    }

    @Override
    public RenderContextExpression getRenderContext() {
      if (renderContextCapture == null) {
        renderContextCapture =
            ParentCapture.create(
                StandardNames.RENDER_CONTEXT, parentParameterLookup.getRenderContext());
      }
      return new RenderContextExpression(renderContextCapture.childExpression);
    }
  }
}
