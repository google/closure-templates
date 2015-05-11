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

import static com.google.common.base.Predicates.notNull;
import static com.google.template.soy.jbcsrc.BytecodeUtils.NULLARY_INIT;
import static com.google.template.soy.jbcsrc.FieldRef.createField;
import static com.google.template.soy.jbcsrc.LocalVariable.createLocal;
import static com.google.template.soy.jbcsrc.LocalVariable.createThisVar;
import static com.google.template.soy.jbcsrc.MethodRef.RENDER_RESULT_DONE;
import static com.google.template.soy.jbcsrc.StandardNames.IJ_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.PARAMS_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.RENDER_CONTEXT_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.STATE_FIELD;
import static com.google.template.soy.jbcsrc.Statement.returnExpression;
import static com.google.template.soy.soytree.SoytreeUtils.isDescendantOf;
import static java.util.Arrays.asList;

import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyAbstractCachingValueProvider;
import com.google.template.soy.data.internal.RenderableThunk;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.runtime.DetachableContentProvider;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProvider;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A compiler for lazy closures.
 * 
 * <p>Certain Soy operations trigger lazy execution, in particular {@code {let ...}} and 
 * {@code {param ...}} statements.  This laziness allows Soy rendering to both limit the amount of 
 * temporary buffers that must be used as well as to delay evaluating expressions until the results
 * are needed (expression evaluation may trigger detaches).
 * 
 * <p>There are 2 kinds of lazy execution:
 * <ul>
 *     <li>Lazy expression evaluation. Triggered by {@link LetValueNode} or 
 *         {@link CallParamValueNode}.  For each of these we will generate a subtype of 
 *         {@link SoyAbstractCachingValueProvider}.
 *     <li>Lazy content evaluation.  Triggered by {@link LetContentNode} or 
 *         {@link CallParamContentNode}. For each of these we will generate a subtype of 
 *         {@link RenderableThunk} and appropriately wrap it in a {@link SanitizedContent} or 
 *         {@link StringData} value.
 * </ul>
 * 
 * <p>Each of these lazy statements execute in the context of their parents and have access to all
 * the local variables and parameters of their parent templates at the point of their definition. To
 * implement this, the child will be passed references to all needed data explicitly at the point
 * of definition.  To do this we will identify all the data that will be referenced by the closure
 * and pass it as explicit constructor parameters and will store them in fields.  So that, for a
 * template like: <pre> {@code    
 *   {template .foo}
 *     {{@literal @}param a : int}
 *     {let b : $a  + 1 /}
 *     {$b}
 *   {/template}
 * }</pre>
 * 
 * <p>The compiled result will look something like: <pre>{@code    
 *  ...
 *  LetValue$$b b = new LetValue$$b(params.getFieldProvider("a"));
 *  b.render(out);
 *  ...
 *  
 *  final class LetValue$$b extends SoyAbstractCachingValueProvider {
 *    final SoyValueProvider a;
 *    LetValue$$b(SoyValueProvider a) {
 *      this.a = a;
 *    }
 *
 *    {@literal @}Override protected SoyValue compute() {
 *       return eval(expr, node);
 *    }
 *  }}</pre>
 */
final class LazyClosureCompiler {
  // All our lazy closures are package private.  They should only be referenced by their parent
  // classes (or each other)
  private static final int LAZY_CLOSURE_ACCESS = Opcodes.ACC_FINAL;
  private static final Method DO_RESOLVE;
  private static final Method DO_RENDER;
  private static final Method DETACHABLE_CONTENT_PROVIDER_INIT;
  private static final FieldRef RESOLVED_VALUE = 
      FieldRef.instanceFieldReference(DetachableSoyValueProvider.class, "resolvedValue");

  static {
    try {
      DO_RESOLVE = Method.getMethod(
          DetachableSoyValueProvider.class.getDeclaredMethod("doResolve"));
      DO_RENDER = Method.getMethod(
          DetachableContentProvider.class.getDeclaredMethod("doRender", AdvisingAppendable.class));
      DETACHABLE_CONTENT_PROVIDER_INIT = Method.getMethod(
          DetachableContentProvider.class.getDeclaredConstructor(ContentKind.class));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new AssertionError(e);
    }
  }

  private static final Converter<Kind, String> KIND_CLASS_FROMAT = 
      Enums.stringConverter(Kind.class)
          .reverse()
          .andThen(CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL));
  
  private final CompiledTemplateRegistry registry;
  private final InnerClasses innerClasses;
  private final VariableLookup parentVariables;
  private final ErrorReporter errorReporter;
  private final ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler;

  LazyClosureCompiler(
      CompiledTemplateRegistry registry, 
      InnerClasses innerClasses,
      VariableLookup parentVariables, 
      ErrorReporter errorReporter,
      ExpressionToSoyValueProviderCompiler expressionToSoyValueProviderCompiler) {
    this.registry = registry;
    this.innerClasses = innerClasses;
    this.parentVariables = parentVariables;
    this.errorReporter = errorReporter;
    this.expressionToSoyValueProviderCompiler = expressionToSoyValueProviderCompiler;
  }
  
  Expression compileLazyExpression(SoyNode declaringNode, String varName, ExprNode exprNode) {
    Optional<Expression> asSoyValueProvider =
        expressionToSoyValueProviderCompiler.compileAvoidingDetaches(exprNode);
    if (asSoyValueProvider.isPresent()) {
      return asSoyValueProvider.get();
    }
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new CheckClassAdapter(writer, false);
    TypeInfo type = innerClasses.registerInnerClassWithGeneratedName(
        getProposedName(declaringNode, varName),
        LAZY_CLOSURE_ACCESS);
    Expression expr = 
        new CompilationUnit(visitor, type, DetachableSoyValueProvider.class, declaringNode)
            .compileExpression(exprNode);
    
    innerClasses.registerAsInnerClass(visitor, type);
    innerClasses.add(ClassData.create(type, writer.toByteArray()));
    visitor.visitEnd();
    return expr;
  }

  Expression compileLazyContent(RenderUnitNode renderUnit, String varName) {
    // TODO(lukes): consider adding an optimization for renderUnits that only contain RawTextNodes
    // they do exist!
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new CheckClassAdapter(writer, false);
    TypeInfo type = innerClasses.registerInnerClassWithGeneratedName(
        getProposedName(renderUnit, varName),
        LAZY_CLOSURE_ACCESS);
    Expression expr = 
        new CompilationUnit(visitor, type, DetachableContentProvider.class, renderUnit)
            .compileRenderable(renderUnit);
    
    innerClasses.registerAsInnerClass(visitor, type);
    innerClasses.add(ClassData.create(type, writer.toByteArray()));
    visitor.visitEnd();
    return expr;
  }

  private String getProposedName(SoyNode declaringNode, String varName) {
    return KIND_CLASS_FROMAT.convert(declaringNode.getKind()) + "_" + varName;
  }

  /**
   * A simple object to aid in generating code for a single node.
   */
  private final class CompilationUnit {
    final UniqueNameGenerator fieldNames = UniqueNameGenerator.forFieldNames();
    final TypeInfo type;
    final TypeInfo baseClass;
    final SoyNode node;
    final ClassVisitor visitor;

    CompilationUnit(ClassVisitor visitor, TypeInfo type, Class<?> baseClass, SoyNode node) {
      this.visitor = visitor;
      this.type = type;
      this.baseClass = TypeInfo.create(baseClass);
      this.node = node;
      visitor.visit(Opcodes.V1_7, 
          LAZY_CLOSURE_ACCESS,
          type.internalName(), 
          null, // not a generic type
          this.baseClass.internalName(), // superclass
          new String[] {});
    }

    Expression compileExpression(ExprNode exprNode) {
      final Label start = new Label();
      final Label end = new Label();
      final LocalVariable thisVar = createThisVar(type, start, end);
      VariableSet variableSet = new VariableSet(fieldNames, type, thisVar, DO_RESOLVE);
      LazyClosureVariableLookup lookup = 
          new LazyClosureVariableLookup(this, parentVariables, variableSet, thisVar);
      SoyExpression expression = 
          ExpressionCompiler.createBasicCompiler(lookup, errorReporter)
              .compile(exprNode)
              .box();
      final Statement storeExpr = RESOLVED_VALUE.putInstanceField(thisVar, expression);
      final Statement returnDone = Statement.returnExpression(RENDER_RESULT_DONE.invoke());
      Statement doResolveImpl = new Statement() {
        @Override void doGen(CodeBuilder adapter) {
          adapter.mark(start);
          storeExpr.gen(adapter);
          returnDone.gen(adapter);
          adapter.mark(end);
        }
      };
      Expression constructExpr = generateConstructor(
          new Statement() {
            @Override void doGen(CodeBuilder adapter) {
              adapter.loadThis();
              adapter.invokeConstructor(baseClass.type(), NULLARY_INIT);
            }
          }, 
          lookup.getCapturedFields());

      doResolveImpl.writeMethod(Opcodes.ACC_PROTECTED, DO_RESOLVE, visitor);
      variableSet.defineFields(visitor);
      return constructExpr;
    }

    Expression compileRenderable(RenderUnitNode renderUnit) {
      FieldRef stateField = createField(type, STATE_FIELD, Type.INT_TYPE);
      stateField.defineField(visitor);
      fieldNames.claimName(STATE_FIELD);

      final Label start = new Label();
      final Label end = new Label();
      final LocalVariable thisVar = createThisVar(type, start, end);
      final LocalVariable appendableVar = 
          createLocal("appendable", 1, Type.getType(AdvisingAppendable.class), start, end);

      final VariableSet variableSet = new VariableSet(fieldNames, type, thisVar, DO_RENDER);
      LazyClosureVariableLookup lookup = 
          new LazyClosureVariableLookup(this, parentVariables, variableSet, thisVar);
      SoyNodeCompiler soyNodeCompiler = SoyNodeCompiler.create(registry, innerClasses, stateField,
          thisVar, appendableVar, variableSet, lookup, errorReporter);
      final Statement nodeBody = soyNodeCompiler.compileChildren(renderUnit);
      final Statement returnDone = returnExpression(MethodRef.RENDER_RESULT_DONE.invoke());
      Statement fullMethodBody = new Statement() {
        @Override void doGen(CodeBuilder adapter) {
          adapter.mark(start);
          nodeBody.gen(adapter);
          adapter.mark(end);
          returnDone.gen(adapter);

          thisVar.tableEntry(adapter);
          appendableVar.tableEntry(adapter);
          variableSet.generateTableEntries(adapter);
        }
      };

      final Expression contentKind = 
          renderUnit.getContentKind() == null 
              ? BytecodeUtils.constantNull(ContentKind.class) 
              : FieldRef.enumReference(renderUnit.getContentKind()).accessor();
      Statement superClassContstructor = new Statement() {
        @Override void doGen(CodeBuilder adapter) {
          adapter.loadThis();
          contentKind.gen(adapter);
          adapter.invokeConstructor(baseClass.type(), DETACHABLE_CONTENT_PROVIDER_INIT);
        }
      };
      Expression constructExpr = 
          generateConstructor(superClassContstructor, lookup.getCapturedFields());

      fullMethodBody.writeMethod(Opcodes.ACC_PROTECTED, DO_RENDER, visitor);
      variableSet.defineFields(visitor);
      return constructExpr;
    }

    /** 
     * Generates a public constructor that assigns our final field and checks for missing required 
     * params and returns an expression invoking that constructor with 
     * 
     * <p>This constructor is called by the generate factory classes.
     */
    Expression generateConstructor(final Statement superClassConstructorInvocation, 
        Iterable<ParentCapture> captures) {
      final Label start = new Label();
      final Label end = new Label();
      final LocalVariable thisVar = createThisVar(type, start, end);
      final List<LocalVariable> params = new ArrayList<>();
      List<Type> paramTypes = new ArrayList<>();
      final List<Statement> assignments = new ArrayList<>();
      final List<Expression> argExpressions = new ArrayList<>();
      int index = 1;  // start at 1 since 'this' occupied slot 0
      for (ParentCapture capture : captures) {
        FieldRef field = capture.field();
        field.defineField(visitor);
        LocalVariable var = createLocal(field.name(), index, field.type(), start, end);
        assignments.add(field.putInstanceField(thisVar, var));
        argExpressions.add(capture.parentExpression());
        params.add(var);
        paramTypes.add(field.type());
        index += field.type().getSize();
      }

      Statement constructorBody = new Statement() {
        @Override void doGen(CodeBuilder ga) {
          ga.mark(start);
          // call super()
          superClassConstructorInvocation.gen(ga);
          for (Statement assignment : assignments) {
            assignment.gen(ga);
          }
          ga.returnValue();
          ga.mark(end);
          thisVar.tableEntry(ga);
          for (LocalVariable local : params) {
            local.tableEntry(ga);
          }
        }
      };

      ConstructorRef constructor = ConstructorRef.create(type, paramTypes);
      constructorBody.writeMethod(Opcodes.ACC_PUBLIC, constructor.method(), visitor);
      return constructor.construct(argExpressions);
    }
  }

  /**
   * Represents a field captured from our parent.  To capture a value from our parent we grab the
   * expression that produces that value and then generate a field in the child with the same type.
   * 
   * <p>{@link CompilationUnit#generateConstructor(Iterable)} generates the code to
   * propagate the captured values from the parent to the child, and from the constructor to the
   * generated fields.
   */
  @AutoValue abstract static class ParentCapture {
    static ParentCapture create(TypeInfo owner, String name, Expression parentExpression) {
      return new AutoValue_LazyClosureCompiler_ParentCapture(
          FieldRef.createFinalField(owner, name, parentExpression.resultType()),
          parentExpression);
    }

    /** The field in the closure that stores the captured value. */
    abstract FieldRef field();

    /** An expression that produces the value for this capture from the parent. */
    abstract Expression parentExpression();
  }

  /**
   * The {@link LazyClosureVariableLookup} will generate expressions for all variable references
   * within a lazy closure.  The strategy is simple
   * 
   * <ul>
   *     <li>If the variable is a template parameter, query the parent variable lookup and generate
   *         a {@link ParentCapture} for it
   *     <li>If the variable is a local (synthetic or otherwise), check if the declaring node is a
   *         descendant of the current lazy node.  If it is, generate code for a normal variable 
   *         lookup (via our own VariableSet), otherwise generate a {@link ParentCapture} to grab
   *         the value from our parent.
   *     <li>Finally, for the {@link RenderContext}, we lazily generate a {@link ParentCapture} if
   *         necessary.
   * </ul>
   */
  private static final class LazyClosureVariableLookup implements VariableLookup {
    private final CompilationUnit params;
    private final VariableLookup parent;
    private final VariableSet variableSet;
    private final Expression thisVar;

    // These fields track all the parent captures that we need to generate.
    private final Map<TemplateParam, ParentCapture> paramFields = new LinkedHashMap<>();
    private final Map<LocalVar, ParentCapture> localFields = new LinkedHashMap<>();
    private final Map<SyntheticVarName, ParentCapture> syntheticFields = new LinkedHashMap<>();
    private ParentCapture renderContextCapture;
    private ParentCapture paramsCapture;
    private ParentCapture ijCapture;

    LazyClosureVariableLookup(
        CompilationUnit params,
        VariableLookup parent, 
        VariableSet variableSet, 
        Expression thisVar) {
      this.params = params;
      this.parent = parent;
      this.variableSet = variableSet;
      this.thisVar = thisVar;
    }

    @Override public Expression getParam(TemplateParam param) {
      // All params are packed into fields
      ParentCapture capturedField = paramFields.get(param);
      if (capturedField == null) {
        String name = param.name();
        params.fieldNames.claimName(name);
        capturedField = ParentCapture.create(params.type, name, parent.getParam(param));
        paramFields.put(param, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    @Override public Expression getLocal(LocalVar local) {
      if (isDescendantOf(local.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(local.name()).local();
      }
      
      ParentCapture capturedField = localFields.get(local);
      if (capturedField == null) {
        String name = params.fieldNames.generateName(local.name());
        capturedField = ParentCapture.create(params.type, name, parent.getLocal(local));
        localFields.put(local, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    @Override public Expression getLocal(SyntheticVarName varName) {
      if (isDescendantOf(varName.declaringNode(), params.node)) {
        // in this case, we just delegate to VariableSet
        return variableSet.getVariable(varName).local();
      }

      ParentCapture capturedField = syntheticFields.get(varName);
      if (capturedField == null) {
        String name = params.fieldNames.generateName(varName.name());
        capturedField = ParentCapture.create(params.type, name, parent.getLocal(varName));
        syntheticFields.put(varName, capturedField);
      }
      return capturedField.field().accessor(thisVar);
    }

    Iterable<ParentCapture> getCapturedFields() {
      return Iterables.concat(
          Iterables.filter(asList(paramsCapture, ijCapture, renderContextCapture), notNull()),
          paramFields.values(), 
          localFields.values(), 
          syntheticFields.values());
    }

    @Override public Expression getRenderContext() {
      if (renderContextCapture == null) {
        params.fieldNames.claimName(RENDER_CONTEXT_FIELD);
        renderContextCapture =
            ParentCapture.create(params.type, RENDER_CONTEXT_FIELD, parent.getRenderContext());
      }
      return renderContextCapture.field().accessor(thisVar);
    }

    @Override public Expression getParamsRecord() {
      if (paramsCapture == null) {
        params.fieldNames.claimName(PARAMS_FIELD);
        paramsCapture =
            ParentCapture.create(params.type, PARAMS_FIELD, parent.getParamsRecord());
      }
      return paramsCapture.field().accessor(thisVar);
    }

    @Override public Expression getIjRecord() {
      if (ijCapture == null) {
        params.fieldNames.claimName(IJ_FIELD);
        ijCapture =
            ParentCapture.create(params.type, IJ_FIELD, parent.getIjRecord());
      }
      return ijCapture.field().accessor(thisVar);
    }
  }
}
