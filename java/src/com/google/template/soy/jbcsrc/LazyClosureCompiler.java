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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NULLARY_INIT;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantSanitizedContentKindAsContentKind;
import static com.google.template.soy.jbcsrc.restricted.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.restricted.LocalVariable.createThisVar;
import static com.google.template.soy.jbcsrc.restricted.MethodRef.RENDER_RESULT_DONE;
import static com.google.template.soy.jbcsrc.restricted.Statement.returnExpression;
import static com.google.template.soy.soytree.SoyTreeUtils.isDescendantOf;
import static java.util.Arrays.asList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.ExpressionDetacher.BasicDetacher;
import com.google.template.soy.jbcsrc.SoyNodeCompiler.CompiledMethodBody;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.runtime.DetachableContentProvider;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProvider;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProviderProvider;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * {template .foo}
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
 * LetValue$$b b = new LetValue$$b(params.getFieldProvider("a"));
 * b.render(out);
 * ...
 *
 * final class LetValue$$b extends DetachableSoyValueProvider {
 *   final SoyValueProvider a;
 *   LetValue$$b(SoyValueProvider a) {
 *     this.a = a;
 *   }
 *
 *   {@literal @}Override protected RenderResult doResolve() {
 *      this.resolvedValue = eval(expr, node);
 *      return RenderResult.done();
 *   }
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

  // All our lazy closures are package private.  They should only be referenced by their parent
  // classes (or each other)
  private static final int LAZY_CLOSURE_ACCESS = Opcodes.ACC_FINAL;
  private static final Method DO_RESOLVE;
  private static final Method DO_RESOLVE_DELEGATE;
  private static final Method DO_RENDER;
  private static final FieldRef RESOLVED_VALUE =
      FieldRef.instanceFieldReference(DetachableSoyValueProvider.class, "resolvedValue");
  private static final FieldRef RESOLVED_VALUE_PROVIDER =
      FieldRef.instanceFieldReference(
          DetachableSoyValueProviderProvider.class, "resolvedValueProvider");
  private static final TypeInfo DETACHABLE_CONTENT_PROVIDER_TYPE =
      TypeInfo.create(DetachableContentProvider.class);
  private static final TypeInfo DETACHABLE_VALUE_PROVIDER_TYPE =
      TypeInfo.create(DetachableSoyValueProvider.class);
  private static final TypeInfo DETACHABLE_VALUE_PROVIDER_PROVIDER_TYPE =
      TypeInfo.create(DetachableSoyValueProviderProvider.class);

  static {
    try {
      DO_RESOLVE =
          Method.getMethod(DetachableSoyValueProvider.class.getDeclaredMethod("doResolve"));
      DO_RESOLVE_DELEGATE =
          Method.getMethod(
              DetachableSoyValueProviderProvider.class.getDeclaredMethod("doResolveDelegate"));
      DO_RENDER =
          Method.getMethod(
              DetachableContentProvider.class.getDeclaredMethod(
                  "doRender", LoggingAdvisingAppendable.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new AssertionError(e);
    }
  }

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

  LazyClosure compileLazyExpression(
      String namePrefix, SoyNode declaringNode, String varName, ExprRootNode exprNode) {
    if (ExpressionCompiler.canCompileToConstant(exprNode)) {
      SoyExpression expression = parent.constantCompiler.compile(exprNode);
      return LazyClosure.create(
          varName,
          parent
              .fields
              .addStaticField(
                  getProposedName(namePrefix, varName), expression.boxAsSoyValueProvider())
              .accessor(),
          /* isTrivial=*/ true,
          /* requiresDetachLogicToResolve=*/ false);
    }
    Optional<Expression> asSoyValueProvider =
        parent.expressionToSoyValueProviderCompiler.compileAvoidingDetaches(exprNode);
    if (asSoyValueProvider.isPresent()) {
      Expression svp = asSoyValueProvider.get();
      return LazyClosure.create(
          varName,
          svp,
          /* isTrivial=*/ exprNode.getRoot().getKind() == ExprNode.Kind.VAR_REF_NODE,
          // It is possible that even if we could compile to a SoyValueProvider while avoiding
          // detaches, that it might still require a detach operation to resolve the expression.
          /* requiresDetachLogicToResolve=*/ ExpressionCompiler.requiresDetach(
              parent.analysis, exprNode));
    }

    TypeInfo type =
        parent.innerClasses.registerInnerClassWithGeneratedName(
            getProposedName(namePrefix, varName), LAZY_CLOSURE_ACCESS);
    SoyClassWriter writer =
        SoyClassWriter.builder(type)
            .setAccess(LAZY_CLOSURE_ACCESS)
            .extending(DETACHABLE_VALUE_PROVIDER_PROVIDER_TYPE)
            .sourceFileName(declaringNode.getSourceLocation().getFileName())
            .build();

    Optional<Expression> asSoyValueProviderProvider =
        new CompilationUnit(
                parent.analysis,
                writer,
                type,
                DETACHABLE_VALUE_PROVIDER_PROVIDER_TYPE,
                declaringNode)
            .compileExpressionToSoyValueProviderIfUseful(exprNode);

    if (asSoyValueProviderProvider.isPresent()) {
      parent.innerClasses.registerAsInnerClass(writer, type);
      writer.visitEnd();
      parent.innerClasses.add(writer.toClassData());
      return LazyClosure.create(
          varName,
          asSoyValueProviderProvider.get(),
          /* isTrivial=*/ false,
          // this must be true because we already failed when trying to compileAvoidingDetaches.
          /* requiresDetachLogicToResolve=*/ true);
    }

    writer =
        SoyClassWriter.builder(type)
            .setAccess(LAZY_CLOSURE_ACCESS)
            .extending(DETACHABLE_VALUE_PROVIDER_TYPE)
            .sourceFileName(declaringNode.getSourceLocation().getFileName())
            .build();
    Expression expr =
        new CompilationUnit(
                parent.analysis, writer, type, DETACHABLE_VALUE_PROVIDER_TYPE, declaringNode)
            .compileExpression(exprNode);

    parent.innerClasses.registerAsInnerClass(writer, type);
    writer.visitEnd();
    parent.innerClasses.add(writer.toClassData());
    return LazyClosure.create(
        varName,
        expr,
        /* isTrivial=*/ false,
        // this must be true, otherwise one of our earlier cases would have triggered.
        /* requiresDetachLogicToResolve=*/ true);
  }

  LazyClosure compileLazyContent(String namePrefix, RenderUnitNode renderUnit, String varName) {
    return compileLazyContent(
        namePrefix, renderUnit, varName, ExtraCodeCompiler.NO_OP, ExtraCodeCompiler.NO_OP);
  }

  LazyClosure compileLazyContent(
      String namePrefix,
      RenderUnitNode renderUnit,
      String varName,
      ExtraCodeCompiler prefix,
      ExtraCodeCompiler suffix) {
    String proposedName = getProposedName(namePrefix, varName);
    // Attempt to compile the whole thing to a string if possible.  The presense of a non-trivial
    // ExtraCodeCompiler means that it isn't just textual.
    Optional<Expression> asRawText =
        prefix == ExtraCodeCompiler.NO_OP && suffix == ExtraCodeCompiler.NO_OP
            ? asRawTextOnly(proposedName, renderUnit)
            : Optional.empty();
    if (asRawText.isPresent()) {
      return LazyClosure.create(
          varName, asRawText.get(), /*isTrivial=*/ true, /* requiresDetachLogicToResolve=*/ false);
    }
    if (!prefix.requiresDetachLogic(parent.analysis)
        && !suffix.requiresDetachLogic(parent.analysis)
        && !requiresDetachLogic(renderUnit)) {
      // We can evaluate this inline by writing everything into a new buffer!
      // because there are no detaches
      return LazyClosure.create(
          varName,
          renderIntoBuffer(renderUnit, prefix, suffix),
          /*isTrivial=*/ false,
          /* requiresDetachLogicToResolve=*/ false);
    }
    TypeInfo type =
        parent.innerClasses.registerInnerClassWithGeneratedName(proposedName, LAZY_CLOSURE_ACCESS);
    SoyClassWriter writer =
        SoyClassWriter.builder(type)
            .setAccess(LAZY_CLOSURE_ACCESS)
            .extending(DETACHABLE_CONTENT_PROVIDER_TYPE)
            .sourceFileName(renderUnit.getSourceLocation().getFileName())
            .build();
    Expression expr =
        new CompilationUnit(
                parent.analysis, writer, type, DETACHABLE_CONTENT_PROVIDER_TYPE, renderUnit)
            .compileRenderable(renderUnit, prefix, suffix);

    parent.innerClasses.registerAsInnerClass(writer, type);
    writer.visitEnd();
    parent.innerClasses.add(writer.toClassData());
    return LazyClosure.create(
        varName,
        expr,
        /* isTrivial=*/ false,
        // because our check above failed, this must be true
        /* requiresDetachLogicToResolve=*/ true);
  }

  private Expression renderIntoBuffer(
      RenderUnitNode renderUnitNode, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {
    TemplateVariableManager.Scope scope = parent.variables.enterScope();
    LocalVariable variable =
        scope
            .createTemporary("buffer", MethodRef.LOGGING_ADVISING_APPENDABLE_BUFFERING.returnType())
            .asNonNullable();
    Statement initBuffer = variable.store(MethodRef.LOGGING_ADVISING_APPENDABLE_BUFFERING.invoke());
    Statement populateBuffer =
        parent
            .compilerWithNewAppendable(AppendableExpression.forExpression(variable))
            .compileWithoutDetaches(renderUnitNode, prefix, suffix);

    return Statement.concat(
            initBuffer.labelStart(variable.start()), populateBuffer, scope.exitScope())
        .then(MethodRef.BUFFERED_SOY_VALUE_PROVIDER_CREATE.invoke(variable));
  }

  /**
   * Returns an SoyValueProvider expression for the given RenderUnitNode if it is composed of only
   * raw text.
   */
  private Optional<Expression> asRawTextOnly(String name, RenderUnitNode renderUnit) {
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
      value = MethodRef.STRING_DATA_FOR_VALUE.invoke(value);
    } else {
      value =
          MethodRef.ORDAIN_AS_SAFE.invoke(value, constantSanitizedContentKindAsContentKind(kind));
    }

    FieldRef staticField = parent.fields.addStaticField(name, value);
    return Optional.of(staticField.accessor());
  }

  private String getProposedName(String prefix, String varName) {
    return prefix + "_" + varName;
  }

  /** A simple object to aid in generating code for a single node. */
  private final class CompilationUnit {
    final TemplateAnalysis analysis;
    final FieldManager fields;
    final TypeInfo type;
    final TypeInfo baseClass;
    final SoyNode node;
    final SoyClassWriter writer;

    CompilationUnit(
        TemplateAnalysis analysis,
        SoyClassWriter writer,
        TypeInfo type,
        TypeInfo baseClass,
        SoyNode node) {
      this.analysis = analysis;
      this.writer = writer;
      this.fields = new FieldManager(type);
      this.type = type;
      this.baseClass = baseClass;
      this.node = node;
    }

    Expression compileExpression(ExprNode exprNode) {
      final Label start = new Label();
      final Label end = new Label();
      TemplateVariableManager variableSet =
          new TemplateVariableManager(
              type.type(),
              DO_RESOLVE,
              /*parameterNames=*/ ImmutableList.of(),
              start,
              end,
              /* isStatic=*/ false);
      final Expression thisVar = variableSet.getVariable("this");
      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(this, parent.parameterLookup, variableSet, thisVar);
      SoyExpression compile =
          ExpressionCompiler.createBasicCompiler(
                  analysis, lookup, variableSet, parent.javaSourceFunctionCompiler)
              .compile(exprNode);
      SoyExpression expression = compile.box();
      final Statement storeExpr = RESOLVED_VALUE.putInstanceField(thisVar, expression);
      final Statement returnDone = Statement.returnExpression(RENDER_RESULT_DONE.invoke());
      Statement doResolveImpl =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder adapter) {
              adapter.mark(start);
              storeExpr.gen(adapter);
              returnDone.gen(adapter);
              adapter.mark(end);

              variableSet.generateTableEntries(adapter);
            }
          };
      Expression constructExpr = generateConstructor(lookup.getCapturedFields());

      doResolveImpl.writeMethod(Opcodes.ACC_PROTECTED, DO_RESOLVE, writer);
      fields.defineFields(writer);
      fields.defineStaticInitializer(writer);
      return constructExpr;
    }

    Optional<Expression> compileExpressionToSoyValueProviderIfUseful(ExprNode exprNode) {
      final Label start = new Label();
      final Label end = new Label();
      TemplateVariableManager variableSet =
          new TemplateVariableManager(
              type.type(),
              DO_RESOLVE_DELEGATE,
              ImmutableList.of(),
              start,
              end,
              /*isStatic=*/ false);
      final Expression thisVar = variableSet.getVariable("this");

      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(this, parent.parameterLookup, variableSet, thisVar);
      ExpressionCompiler expressionCompiler =
          ExpressionCompiler.create(
              analysis, lookup, variableSet, parent.javaSourceFunctionCompiler);
      Optional<Expression> expr =
          ExpressionToSoyValueProviderCompiler.create(analysis, expressionCompiler, lookup)
              .compileToSoyValueProviderIfUsefulToPreserveStreaming(
                  exprNode, BasicDetacher.INSTANCE);

      if (!expr.isPresent()) {
        return Optional.empty();
      }

      final Statement storeExpr = RESOLVED_VALUE_PROVIDER.putInstanceField(thisVar, expr.get());
      final Statement returnDone = Statement.returnExpression(RENDER_RESULT_DONE.invoke());
      Statement doResolveImpl =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder adapter) {
              adapter.mark(start);
              storeExpr.gen(adapter);
              returnDone.gen(adapter);
              adapter.mark(end);

              variableSet.generateTableEntries(adapter);
            }
          };
      Expression constructExpr = generateConstructor(lookup.getCapturedFields());

      doResolveImpl.writeMethod(Opcodes.ACC_PROTECTED, DO_RESOLVE_DELEGATE, writer);
      fields.defineFields(writer);
      fields.defineStaticInitializer(writer);
      return Optional.of(constructExpr);
    }

    Expression compileRenderable(
        RenderUnitNode renderUnit, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix) {

      final Label start = new Label();
      final Label end = new Label();
      BasicExpressionCompiler constantCompiler =
          ExpressionCompiler.createConstantCompiler(
              analysis,
              new SimpleLocalVariableManager(
                  type.type(), BytecodeUtils.CLASS_INIT, /* isStatic=*/ true),
              parent.javaSourceFunctionCompiler);
      final TemplateVariableManager variableSet =
          new TemplateVariableManager(
              type.type(),
              DO_RENDER,
              ImmutableList.of(StandardNames.APPENDABLE),
              start,
              end,
              /* isStatic=*/ false);

      LazyClosureParameterLookup lookup =
          new LazyClosureParameterLookup(
              this, parent.parameterLookup, variableSet, variableSet.getVariable("this"));
      SoyNodeCompiler soyNodeCompiler =
          SoyNodeCompiler.create(
              analysis,
              parent.innerClasses,
              AppendableExpression.forExpression(
                  variableSet.getVariable(StandardNames.APPENDABLE).asNonNullable()),
              variableSet,
              lookup,
              fields,
              constantCompiler,
              parent.javaSourceFunctionCompiler);
      CompiledMethodBody compileChildren = soyNodeCompiler.compile(renderUnit, prefix, suffix);
      writer.setNumDetachStates(compileChildren.numberOfDetachStates());
      final Statement nodeBody = compileChildren.body();
      final Statement returnDone = returnExpression(MethodRef.RENDER_RESULT_DONE.invoke());
      Statement fullMethodBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder adapter) {
              adapter.mark(start);
              nodeBody.gen(adapter);
              adapter.mark(end);
              returnDone.gen(adapter);

              variableSet.generateTableEntries(adapter);
            }
          };
      Expression constructExpr = generateConstructor(lookup.getCapturedFields());

      fields.defineFields(writer);
      fullMethodBody.writeMethod(Opcodes.ACC_PROTECTED, DO_RENDER, writer);
      fields.defineStaticInitializer(writer);
      return constructExpr;
    }

    /**
     * Generates a public constructor that assigns our final field and checks for missing required
     * params and returns an expression invoking that constructor with
     *
     * <p>This constructor is called by the generate factory classes.
     */
    Expression generateConstructor(Iterable<ParentCapture> captures) {
      final Label start = new Label();
      final Label end = new Label();
      final LocalVariable thisVar = createThisVar(type, start, end);
      final List<LocalVariable> params = new ArrayList<>();
      List<Type> paramTypes = new ArrayList<>();
      final List<Statement> assignments = new ArrayList<>();
      final List<Expression> argExpressions = new ArrayList<>();
      int index = 1; // start at 1 since 'this' occupied slot 0
      for (ParentCapture capture : captures) {
        FieldRef field = capture.field();
        LocalVariable var = createLocal(field.name(), index, field.type(), start, end);
        assignments.add(field.putInstanceField(thisVar, var));
        argExpressions.add(capture.parentExpression());
        params.add(var);
        paramTypes.add(field.type());
        index += field.type().getSize();
      }

      Statement constructorBody =
          new Statement() {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.mark(start);
              // call super()
              cb.loadThis();
              cb.invokeConstructor(baseClass.type(), NULLARY_INIT);
              // assign params to fields
              for (Statement assignment : assignments) {
                assignment.gen(cb);
              }
              cb.returnValue();
              cb.mark(end);
              thisVar.tableEntry(cb);
              for (LocalVariable local : params) {
                local.tableEntry(cb);
              }
            }
          };

      ConstructorRef constructor = ConstructorRef.create(type, paramTypes);
      constructorBody.writeMethod(Opcodes.ACC_PUBLIC, constructor.method(), writer);
      return constructor.construct(argExpressions);
    }
  }

  /**
   * Represents a field captured from our parent. To capture a value from our parent we grab the
   * expression that produces that value and then generate a field in the child with the same type.
   *
   * <p>{@link CompilationUnit#generateConstructor} generates the code to propagate the captured
   * values from the parent to the child, and from the constructor to the generated fields.
   */
  @AutoValue
  abstract static class ParentCapture {
    static ParentCapture create(FieldRef captureField, Expression parentExpression) {
      if (parentExpression.isNonNullable()) {
        captureField = captureField.asNonNull();
      }
      return new AutoValue_LazyClosureCompiler_ParentCapture(captureField, parentExpression);
    }

    /** The field in the closure that stores the captured value. */
    abstract FieldRef field();

    /** An expression that produces the value for this capture from the parent. */
    abstract Expression parentExpression();
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
    private final Expression thisVar;

    // These fields track all the parent captures that we need to generate.
    // NOTE: TemplateParam and LocalVar have identity semantics.  But the AST is guaranteed to not
    // have multiple copies.
    private final Map<VarDefn, ParentCapture> localFields = new LinkedHashMap<>();
    private final Map<SyntheticVarName, ParentCapture> syntheticFields = new LinkedHashMap<>();
    private ParentCapture renderContextCapture;
    private ParentCapture ijCapture;
    private ParentCapture paramsCapture;

    LazyClosureParameterLookup(
        CompilationUnit params,
        TemplateParameterLookup parentParameterLookup,
        TemplateVariableManager variableSet,
        Expression thisVar) {
      this.params = params;
      this.parentParameterLookup = parentParameterLookup;
      this.variableSet = variableSet;
      this.thisVar = thisVar;
    }

    @Override
    public Expression getParam(TemplateParam param) {
      ParentCapture capturedField = localFields.get(param);
      if (capturedField == null) {
        Expression expression = parentParameterLookup.getParam(param);
        FieldRef field =
            params.fields.addGeneratedFinalField(param.name(), expression.resultType());
        capturedField = ParentCapture.create(field, expression);
        localFields.put(param, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    @Override
    public Expression getIjRecord() {
      if (ijCapture == null) {
        ijCapture =
            ParentCapture.create(
                params.fields.addFinalField(StandardNames.IJ, BytecodeUtils.SOY_RECORD_TYPE),
                parentParameterLookup.getIjRecord());
      }
      return ijCapture.field().accessor(thisVar);
    }

    @Override
    public Expression getParamsRecord() {
      if (paramsCapture == null) {
        paramsCapture =
            ParentCapture.create(
                params.fields.addFinalField(StandardNames.PARAMS, BytecodeUtils.SOY_RECORD_TYPE),
                parentParameterLookup.getParamsRecord());
      }
      return paramsCapture.field().accessor(thisVar);
    }

    @Override
    public Expression getLocal(AbstractLocalVarDefn<?> local) {
      if (isDescendantOf(local.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(local.name());
      }

      ParentCapture capturedField = localFields.get(local);
      if (capturedField == null) {
        Expression expression = parentParameterLookup.getLocal(local);
        FieldRef field =
            params.fields.addGeneratedFinalField(local.name(), expression.resultType());
        capturedField = ParentCapture.create(field, expression);
        localFields.put(local, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    @Override
    public Expression getLocal(SyntheticVarName varName) {
      if (isDescendantOf(varName.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(varName);
      }

      ParentCapture capturedField = syntheticFields.get(varName);
      if (capturedField == null) {
        Expression expression = parentParameterLookup.getLocal(varName);
        FieldRef field =
            params.fields.addGeneratedFinalField(varName.name(), expression.resultType());
        capturedField = ParentCapture.create(field, expression);
        syntheticFields.put(varName, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    Iterable<ParentCapture> getCapturedFields() {
      return Iterables.concat(
          Iterables.filter(
              asList(renderContextCapture, ijCapture, paramsCapture), Objects::nonNull),
          localFields.values(),
          syntheticFields.values());
    }

    @Override
    public RenderContextExpression getRenderContext() {
      if (renderContextCapture == null) {
        renderContextCapture =
            ParentCapture.create(
                params.fields.addFinalField(
                    StandardNames.RENDER_CONTEXT, BytecodeUtils.RENDER_CONTEXT_TYPE),
                parentParameterLookup.getRenderContext());
      }
      return new RenderContextExpression(renderContextCapture.field().accessor(thisVar));
    }
  }
}
