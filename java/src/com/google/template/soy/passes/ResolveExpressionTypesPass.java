/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.template.soy.passes.CheckTemplateCallsPass.ARGUMENT_TYPE_MISMATCH;
import static com.google.template.soy.types.SoyTypes.SAFE_PROTO_TO_SANITIZED_TYPE;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basicfunctions.ConcatListsFunction;
import com.google.template.soy.basicfunctions.KeysFunction;
import com.google.template.soy.basicfunctions.LegacyObjectMapToMapFunction;
import com.google.template.soy.basicfunctions.ListSliceMethod;
import com.google.template.soy.basicfunctions.MapKeysFunction;
import com.google.template.soy.basicfunctions.MapToLegacyObjectMapFunction;
import com.google.template.soy.basicfunctions.NumberListSortMethod;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.AbstractParentExprNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.internal.ResolvedSignature;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AbstractMapType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.ErrorType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.VeDataType;
import com.google.template.soy.types.VeType;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Visitor which resolves all expression types.
 *
 */
public final class ResolveExpressionTypesPass implements CompilerFilePass {

  // Keep in alphabetical order.
  private static final SoyErrorKind BAD_FOREACH_TYPE =
      SoyErrorKind.of("Cannot iterate over {0} of type {1}.");
  private static final SoyErrorKind BAD_INDEX_TYPE = SoyErrorKind.of("Bad index type {0} for {1}.");
  private static final SoyErrorKind BAD_KEY_TYPE = SoyErrorKind.of("Bad key type {0} for {1}.");
  private static final SoyErrorKind BAD_LIST_COMP_TYPE =
      SoyErrorKind.of("Bad list comprehension type. {0} has type: {1}, but should be a list.");

  private static final SoyErrorKind BRACKET_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Type {0} does not support bracket access.");
  private static final SoyErrorKind BRACKET_ACCESS_NULLABLE_UNION =
      SoyErrorKind.of(
          "Union type that is nullable cannot use bracket access. To access this value, "
              + "first check for null or use null-safe (\"?[\") operations.");
  private static final SoyErrorKind CHECK_NOT_NULL_ON_COMPILE_TIME_NULL =
      SoyErrorKind.of("Cannot {0} on a value with a static type of ''null''.");
  private static final SoyErrorKind REDUNDANT_NON_NULL_ASSERTION_OPERATOR =
      SoyErrorKind.of("Found redundant non-null assertion operators (''!'').");
  private static final SoyErrorKind DOT_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Type {0} does not support dot access.");
  private static final SoyErrorKind DOT_ACCESS_NOT_SUPPORTED_CONSIDER_RECORD =
      SoyErrorKind.of("Type {0} does not support dot access (consider record instead of map).");
  private static final SoyErrorKind UNNECESSARY_NULL_SAFE_ACCESS =
      SoyErrorKind.of("This null safe access is unnecessary, it is on a value that is non-null.");
  private static final SoyErrorKind DUPLICATE_KEY_IN_MAP_LITERAL =
      SoyErrorKind.of("Map literals with duplicate keys are not allowed.  Duplicate key: ''{0}''");
  private static final SoyErrorKind KEYS_PASSED_MAP =
      SoyErrorKind.of(
          "Use the ''mapKeys'' function instead of ''keys'' for objects of type ''map''.");
  private static final SoyErrorKind ILLEGAL_MAP_RESOLVED_KEY_TYPE =
      SoyErrorKind.of(
          "A map''s keys must all be the same type. This map has keys of multiple types "
              + "(''{0}'').");
  private static final SoyErrorKind EMPTY_LIST_ACCESS =
      SoyErrorKind.of("Accessing item in empty list.");
  private static final SoyErrorKind EMPTY_LIST_FOREACH =
      SoyErrorKind.of("Cannot iterate over empty list.");
  private static final SoyErrorKind EMPTY_MAP_ACCESS =
      SoyErrorKind.of("Accessing item in empty map.");
  private static final SoyErrorKind INVALID_TYPE_SUBSTITUTION =
      SoyErrorKind.of("Expected expression of type ''{0}'', found ''{1}''.");
  private static final SoyErrorKind LIST_LENGTH_ERROR =
      SoyErrorKind.of(
          "Soy lists do not have a ''length'' field. Use function length(...) instead.");
  private static final SoyErrorKind MISSING_SOY_TYPE =
      SoyErrorKind.of("Missing Soy type for node {0}.");
  private static final SoyErrorKind NOT_PROTO_INIT =
      SoyErrorKind.of("Expected a protocol buffer for the second argument.");
  private static final SoyErrorKind NOT_A_PROTO_TYPE =
      SoyErrorKind.of("''{0}'' is a ''{1}'', expected a protocol buffer.");
  private static final SoyErrorKind OR_OPERATOR_HAS_CONSTANT_OPERAND =
      SoyErrorKind.of(
          "Constant operand ''{0}'' used with ''or'' operator. "
              + "Consider simplifying or using the ?: operator, see "
              + "go/soy/reference/expressions.md#logical-operators",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind STRING_LENGTH_ERROR =
      SoyErrorKind.of(
          "Soy strings do not have a ''length'' field. Use function strLen(...) instead.");
  private static final SoyErrorKind UNDEFINED_FIELD_FOR_PROTO_TYPE =
      SoyErrorKind.of(
          "Undefined field ''{0}'' for proto type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNDEFINED_FIELD_FOR_RECORD_TYPE =
      SoyErrorKind.of(
          "Undefined field ''{0}'' for record type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNKNOWN_PROTO_TYPE =
      SoyErrorKind.of("Unknown proto type ''{0}''.");
  private static final SoyErrorKind PROTO_FIELD_DOES_NOT_EXIST =
      SoyErrorKind.of(
          "Proto field ''{0}'' does not exist on {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind PROTO_MISSING_REQUIRED_FIELD =
      SoyErrorKind.of("Missing required proto field ''{0}''.");
  private static final SoyErrorKind PROTO_NULL_ARG_TYPE =
      SoyErrorKind.of("Cannot assign static type ''null'' to proto field ''{0}''.");
  private static final SoyErrorKind PROTO_FIELD_NAME_ALIAS_CONFLICT =
      SoyErrorKind.of("Alias ''{0}'' conflicts with a field with the same name in proto ''{1}''.");
  private static final SoyErrorKind TYPE_MISMATCH =
      SoyErrorKind.of("Soy types ''{0}'' and ''{1}'' are not comparable.");
  private static final SoyErrorKind DECLARED_DEFAULT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "The initializer for ''{0}'' has type ''{1}'' which is not assignable to type ''{2}''.");
  private static final SoyErrorKind STATE_MUST_BE_CONSTANT =
      SoyErrorKind.of("The initializer for ''{0}'' must be a constant value.  {1}.");
  private static final SoyErrorKind INCOMPATIBLE_ARITHMETIC_OP =
      SoyErrorKind.of("Using arithmetic operators on Soy types ''{0}'' and ''{1}'' is illegal.");
  private static final SoyErrorKind INCOMPATIBLE_ARITHMETIC_OP_UNARY =
      SoyErrorKind.of("Using arithmetic operators on the Soy type ''{0}'' is illegal.");
  private static final SoyErrorKind INCORRECT_ARG_TYPE =
      SoyErrorKind.of("Function ''{0}'' called with incorrect arg type {1} (expected {2}).");
  private static final SoyErrorKind LOOP_VARIABLE_NOT_IN_SCOPE =
      SoyErrorKind.of("Function ''{0}'' must have a loop variable as its argument.");
  private static final SoyErrorKind STRING_LITERAL_REQUIRED =
      SoyErrorKind.of("Argument to function ''{0}'' must be a string literal.");
  private static final SoyErrorKind INVALID_METHOD_BASE =
      SoyErrorKind.of(
          "Method ''{0}'' does not exist on type ''{1}''.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind MULTIPLE_METHODS_MATCH =
      SoyErrorKind.of(
          "Method ''{0}'' with {1} arg(s) for type ''{2}'' matches multiple method"
              + " implementations.");
  private static final SoyErrorKind METHOD_INVALID_PARAM_NUM =
      SoyErrorKind.of("Method ''{0}'' called with {1} parameter(s) but expected {2}.");
  private static final SoyErrorKind METHOD_INVALID_PARAM_TYPES =
      SoyErrorKind.of("Method ''{0}'' called with parameter types ({1}) but expected ({2}).");
  private static final SoyErrorKind METHOD_BASE_TYPE_NULL_SAFE_REQUIRED =
      SoyErrorKind.of(
          "Method calls are not allowed on objects with nullable types (''{0}''). Either ensure"
              + " the type is non-nullable or perform a null safe access (''?.'').");
  private static final SoyErrorKind EXPLICIT_NULL =
      SoyErrorKind.of("Explicit use of the ''null'' type is not allowed.");
  private static final SoyErrorKind AMBIGUOUS_INFERRED_TYPE =
      SoyErrorKind.of(
          "Using {0} in the initializer for a parameter with an inferred type is ambiguous. "
              + "Add an explicit type declaration.");
  private static final SoyErrorKind VE_NO_CONFIG_FOR_ELEMENT =
      SoyErrorKind.of(
          "Could not find logging configuration for this element.{0}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind TEMPLATE_TYPE_PARAMETERS_CANNOT_USE_INFERRED_TYPES =
      SoyErrorKind.of(
          "Template type parameters cannot be inferred. Instead, explicitly declare the type.");

  private final ErrorReporter errorReporter;

  private final ValidatedLoggingConfig loggingConfig;
  private final SoyMethod.Registry methodRegistry;
  /** Cached map that converts a string representation of types to actual soy types. */
  private final Map<Signature, ResolvedSignature> signatureMap = new HashMap<>();

  /** Current set of type substitutions. */
  private TypeSubstitution substitutions;

  private ExprEquivalence exprEquivalence;
  private SoyTypeRegistry typeRegistry;
  private TypeNodeConverter pluginTypeConverter;
  private final PluginResolver.Mode pluginResolutionMode;

  ResolveExpressionTypesPass(
      ErrorReporter errorReporter,
      ValidatedLoggingConfig loggingConfig,
      PluginResolver pluginResolver) {
    this.errorReporter = errorReporter;
    this.loggingConfig = loggingConfig;
    this.pluginResolutionMode =
        pluginResolver == null
            ? PluginResolver.Mode.REQUIRE_DEFINITIONS
            : pluginResolver.getPluginResolutionMode();
    this.methodRegistry =
        new CompositeMethodRegistry(
            ImmutableList.of(BuiltinMethod.REGISTRY, new PluginMethodRegistry(pluginResolver)));
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    substitutions = null; // make sure substitutions don't leak across files
    exprEquivalence = new ExprEquivalence();
    typeRegistry = file.getSoyTypeRegistry();
    pluginTypeConverter =
        TypeNodeConverter.builder(errorReporter)
            .setTypeRegistry(typeRegistry)
            .setSystemExternal(true)
            .build();
    new TypeAssignmentSoyVisitor().exec(file);
  }

  private final class TypeAssignmentSoyVisitor extends AbstractSoyNodeVisitor<Void> {

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      // need to visit expressions first so parameters with inferred types have their expressions
      // analyzed
      List<TemplateHeaderVarDefn> headerVars = node.getHeaderParams();
      // If the default value expressions are not constant, they could reference another default
      // value parameter, which won't work because it's looking up the type of the parameter when it
      // hasn't been inferred yet.  So report an error and override the type to be the errortype
      for (TemplateHeaderVarDefn headerVar : headerVars) {
        if (headerVar.defaultValue() == null) {
          continue;
        }
        for (ExprNode nonConstantChild :
            SoyTreeUtils.getNonConstantChildren(headerVar.defaultValue())) {
          String extra;
          switch (nonConstantChild.getKind()) {
            case VAR_REF_NODE:
              if (headerVar instanceof TemplateStateVar) {
                VarRefNode refNode = (VarRefNode) nonConstantChild;
                // @state depends on @state
                if (refNode.getDefnDecl() instanceof TemplateStateVar) {
                  extra = "State cannot be referenced in default initializers";
                } else {
                  continue; // @state depends on @param
                }
              } else {
                // @param depends on @state/@param
                extra = "Default parameters cannot depend on other parameters or state";
              }
              ((VarRefNode) nonConstantChild).setSubstituteType(ErrorType.getInstance());
              break;
            case FUNCTION_NODE:
              if (headerVar instanceof TemplateStateVar) {
                continue;
              }
              extra = "Only pure functions can be used in default param initializers";
              break;
            default:
              throw new AssertionError("Unexpected non-constant expression: " + nonConstantChild);
          }
          errorReporter.report(
              nonConstantChild.getSourceLocation(),
              STATE_MUST_BE_CONSTANT,
              headerVar.name(),
              extra);
        }
      }

      for (TemplateHeaderVarDefn headerVar : headerVars) {
        // TODO(lukes): there are more non-sensical declarations than just 'null'
        if (headerVar.getTypeNode() != null && NullType.getInstance().equals(headerVar.type())) {
          errorReporter.report(headerVar.getTypeNode().sourceLocation(), EXPLICIT_NULL);
        }
        if (headerVar.defaultValue() != null) {
          new ResolveTypesExprVisitor(
                  /* isDefaultInitializerForInferredParam=*/ headerVar.getTypeNode() == null, node)
              .exec(headerVar.defaultValue());
          SoyType actualType = headerVar.defaultValue().getRoot().getType();
          if (headerVar.getTypeNode() != null) {
            SoyType declaredType = headerVar.type();
            // Validation for template types happens later, it's intentional that it is not
            // assignable at this stage in parsing.
            if (!declaredType.isAssignableFrom(actualType)
                && !(declaredType.getKind() == SoyType.Kind.TEMPLATE
                    && actualType.getKind() == SoyType.Kind.NAMED_TEMPLATE)) {
              errorReporter.report(
                  headerVar.defaultValue().getSourceLocation(),
                  DECLARED_DEFAULT_TYPE_MISMATCH,
                  headerVar.name(),
                  actualType,
                  declaredType);
            }
          } else {
            // in this case the declaredType is inferred from the initializer expression, so just
            // assign
            headerVar.setType(actualType);
          }
        }
      }

      for (ExprRootNode expr : node.getExprList()) {
        if (expr.getType() != null) {
          continue; // must be a default value
        }
        // any other expression in a template declaration
        // currently this is just variant expressions, but might be other things in the future.
        new ResolveTypesExprVisitor(/* isDefaultInitializerForInferredParam=*/ false, node)
            .exec(expr);
      }

      visitChildren(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      visitSoyNode(node);
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      visitSoyNode(node);
      node.getVar().setType(node.getExpr().getType());
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitSoyNode(node);
      node.getVar()
          .setType(
              node.getContentKind() != null
                  ? SanitizedType.getTypeForContentKind(node.getContentKind())
                  : StringType.getInstance());
    }

    @Override
    protected void visitIfNode(IfNode node) {
      TypeSubstitution savedSubstitutionState = substitutions;
      for (SoyNode child : node.getChildren()) {
        if (child instanceof IfCondNode) {
          IfCondNode icn = (IfCondNode) child;
          visitExpressions(icn);

          // Visit the conditional expression to compute which types can be narrowed.
          TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
          visitor.exec(icn.getExpr());

          // Save the state of substitutions from the previous if block.
          TypeSubstitution previousSubstitutionState = substitutions;

          // Modify the current set of type substitutions for the 'true' branch
          // of the if statement.
          addTypeSubstitutions(visitor.positiveTypeConstraints);
          visitChildren(icn);

          // Rewind the substitutions back to the state before the if-condition.
          // Add in the negative substitutions, which will affect subsequent blocks
          // of the if statement.
          // So for example if we have a variable whose type is (A|B|C) and the
          // first if-block tests whether that variable is type A, then in the
          // 'else' block it must be of type (B|C); If a subsequent 'elseif'
          // statement tests whether it's type B, then in the following else block
          // it can only be of type C.
          substitutions = previousSubstitutionState;
          addTypeSubstitutions(visitor.negativeTypeConstraints);
        } else if (child instanceof IfElseNode) {
          // For the else node, we simply inherit the previous set of subsitutions.
          IfElseNode ien = (IfElseNode) child;
          visitChildren(ien);
        }
      }
      substitutions = savedSubstitutionState;
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitExpressions(node);

      TypeSubstitution savedSubstitutionState = substitutions;
      ExprNode switchExpr = node.getExpr().getRoot();
      for (SoyNode child : node.getChildren()) {
        if (child instanceof SwitchCaseNode) {
          SwitchCaseNode scn = ((SwitchCaseNode) child);
          visitExpressions(scn);

          // Calculate a new type for the switch expression: the union of the types of the case
          // statement.
          List<SoyType> caseTypes = new ArrayList<>();
          boolean nullFound = false;
          for (ExprRootNode expr : scn.getExprList()) {
            caseTypes.add(expr.getType());
            if (expr.getRoot().getKind() == ExprNode.Kind.NULL_NODE) {
              nullFound = true;
            }
          }
          SoyType caseType = typeRegistry.getOrCreateUnionType(caseTypes);

          TypeSubstitution previousSubstitutionState = substitutions;

          Map<ExprEquivalence.Wrapper, SoyType> positiveTypeConstraints = new HashMap<>();
          positiveTypeConstraints.put(exprEquivalence.wrap(switchExpr), caseType);
          addTypeSubstitutions(positiveTypeConstraints);
          visitChildren(scn);

          substitutions = previousSubstitutionState;

          if (nullFound) {
            // If a case statement has a null literal, the switch expression can't be null for any
            // of the following case statements.
            Map<ExprEquivalence.Wrapper, SoyType> negativeTypeConstraints = new HashMap<>();
            negativeTypeConstraints.put(
                exprEquivalence.wrap(switchExpr), SoyTypes.tryRemoveNull(switchExpr.getType()));
            addTypeSubstitutions(negativeTypeConstraints);
          }
        } else if (child instanceof SwitchDefaultNode) {
          // No new type substitutions for a default statement, but inherit the previous (negative)
          // subsitutions.
          SwitchDefaultNode sdn = ((SwitchDefaultNode) child);
          visitChildren(sdn);
        }
      }
      substitutions = savedSubstitutionState;
    }

    @Override
    protected void visitForNonemptyNode(ForNonemptyNode node) {
      // Set the inferred type of the loop variable.
      node.getVar().setType(getElementType(node.getExpr().getType(), node));
      // Visit the node body
      if (node.getIndexVar() != null) {
        // Set the type of the optional index to integer.
        node.getIndexVar().setType(IntType.getInstance());
      }
      visitChildren(node);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ExprHolderNode) {
        visitExpressions((ExprHolderNode) node);
      }

      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

  // Given a map of type subsitutions, add all the entries to the current set of
  // active substitutions.
  private void addTypeSubstitutions(Map<ExprEquivalence.Wrapper, SoyType> substitutionsToAdd) {
    for (Map.Entry<ExprEquivalence.Wrapper, SoyType> entry : substitutionsToAdd.entrySet()) {
      ExprNode expr = entry.getKey().get();
      // Get the existing type
      SoyType previousType = expr.getType();
      for (TypeSubstitution subst = substitutions; subst != null; subst = subst.parent) {
        if (exprEquivalence.equivalent(subst.expression, expr)) {
          previousType = subst.type;
          break;
        }
      }

      // If the new type is different than the current type, then add a new type substitution.
      if (!entry.getValue().equals(previousType)) {
        substitutions = new TypeSubstitution(substitutions, expr, entry.getValue());
      }
    }
  }

  private void visitExpressions(ExprHolderNode node) {
    ResolveTypesExprVisitor exprVisitor =
        new ResolveTypesExprVisitor(/* isDefaultInitializerForInferredParam=*/ false, node);
    for (ExprRootNode expr : node.getExprList()) {
      exprVisitor.exec(expr);
    }
  }

  /**
   * Given a collection type, compute the element type.
   *
   * @param collectionType The base type.
   * @param node The ForNonemptyNode being iterated.
   * @return The type of the elements of the collection.
   */
  private SoyType getElementType(SoyType collectionType, ForNonemptyNode node) {
    Preconditions.checkNotNull(collectionType);
    switch (collectionType.getKind()) {
      case UNKNOWN:
        // If we don't know anything about the base type, then make no assumptions
        // about the field type.
        return UnknownType.getInstance();

      case LIST:
        if (collectionType == ListType.EMPTY_LIST) {
          errorReporter.report(node.getExpr().getSourceLocation(), EMPTY_LIST_FOREACH);
          return ErrorType.getInstance();
        }
        return ((ListType) collectionType).getElementType();

      case UNION:
        {
          // If it's a union, then do the field type calculation for each member of
          // the union and combine the result.
          UnionType unionType = (UnionType) collectionType;
          List<SoyType> fieldTypes = new ArrayList<>(unionType.getMembers().size());
          for (SoyType unionMember : unionType.getMembers()) {
            SoyType elementType = getElementType(unionMember, node);
            if (elementType.getKind() == SoyType.Kind.ERROR) {
              return ErrorType.getInstance();
            }
            fieldTypes.add(elementType);
          }
          return SoyTypes.computeLowestCommonType(typeRegistry, fieldTypes);
        }

      default:
        errorReporter.report(
            node.getParent().getSourceLocation(),
            BAD_FOREACH_TYPE,
            node.getExpr().toSourceString(),
            node.getExpr().getType()); // Report the outermost union type in the error.
        return ErrorType.getInstance();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions to point to the
   * corresponding declaration object.
   */
  private final class ResolveTypesExprVisitor extends AbstractExprNodeVisitor<Void> {
    /**
     * Whether we are currently examining an expression in a default initializer for an inferred
     * template.
     *
     * <p>When inferring types, some values are not legal because they create ambiguity. null and
     * the empty collection literals all have this behavior.
     */
    final boolean isDefaultInitializerForInferredParam;

    /**
     * The ExprHolderNode of the expression. Used to get the SoyFileNode when visiting
     * ProtoInitNodes to resolve the aliased fields.
     */
    final SoyNode exprHolderNode;

    ResolveTypesExprVisitor(boolean isDefaultInitializerForInferredParam, SoyNode exprHolderNode) {
      this.isDefaultInitializerForInferredParam = isDefaultInitializerForInferredParam;
      this.exprHolderNode = exprHolderNode;
    }

    private final AbstractExprNodeVisitor<Void> checkAllTypesAssignedVisitor =
        new AbstractExprNodeVisitor<Void>() {
          @Override
          protected void visitExprNode(ExprNode node) {
            if (node instanceof ParentExprNode) {
              visitChildren((ParentExprNode) node);
            }
            requireNodeType(node);
          }
        };

    @Override
    public Void exec(ExprNode node) {
      Preconditions.checkArgument(node instanceof ExprRootNode);
      visit(node);
      // Check that every node in the tree had a type assigned
      checkAllTypesAssignedVisitor.exec(node);
      return null;
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visitChildren(node);
      ExprNode expr = node.getRoot();
      node.setType(expr.getType());
      tryApplySubstitution(node);
    }

    @Override
    protected void visitAssertNonNullOpNode(AssertNonNullOpNode node) {
      visitChildren(node);
      finishAssertNonNullOpNode(node);
    }

    private void finishAssertNonNullOpNode(AssertNonNullOpNode node) {
      ExprNode child = node.getChild(0);
      SoyType type = child.getType();
      if (type.getKind() == Kind.NULL) {
        errorReporter.report(
            node.getSourceLocation(),
            CHECK_NOT_NULL_ON_COMPILE_TIME_NULL,
            "use the non-null assertion operator ('!')");
        node.setType(ErrorType.getInstance());
      } else if (node.getChild(0).getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        errorReporter.report(node.getSourceLocation(), REDUNDANT_NON_NULL_ASSERTION_OPERATOR);
        node.setType(ErrorType.getInstance());
      } else {
        node.setType(SoyTypes.removeNull(type));
      }
    }

    @Override
    protected void visitPrimitiveNode(PrimitiveNode node) {
      // We don't do anything here because primitive nodes already have type information.
      if (isDefaultInitializerForInferredParam && node.getKind() == ExprNode.Kind.NULL_NODE) {
        errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "a 'null' literal");
      }
    }

    @Override
    protected void visitListLiteralNode(ListLiteralNode node) {
      visitChildren(node);
      List<SoyType> elementTypes = new ArrayList<>(node.numChildren());
      for (ExprNode child : node.getChildren()) {
        requireNodeType(child);
        elementTypes.add(child.getType());
      }
      // Special case for empty list.
      if (elementTypes.isEmpty()) {
        if (isDefaultInitializerForInferredParam) {
          errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty list");
        }
        node.setType(ListType.EMPTY_LIST);
      } else {
        node.setType(
            typeRegistry.getOrCreateListType(
                SoyTypes.computeLowestCommonType(typeRegistry, elementTypes)));
      }
      tryApplySubstitution(node);
    }

    @Override
    protected void visitListComprehensionNode(ListComprehensionNode node) {

      // Resolve the listExpr in "[itemMapExpr for $var, $index in listExpr if filterExpr]".
      visit(node.getListExpr());

      // Report an error if listExpr did not actually evaluate to a list.
      // TODO(lukes): Should we allow iterating over the unknown type?  We do for for-loops.
      if (!(node.getListExpr().getType() instanceof ListType)) {
        errorReporter.report(
            node.getListExpr().getSourceLocation(),
            BAD_LIST_COMP_TYPE,
            node.getListExpr().toSourceString(),
            node.getListExpr().getType());
        node.getListIterVar().setType(ErrorType.getInstance());
      } else {
        // Otherwise, use the list element type to set the type of the iterator ($var in this
        // example).
        node.getListIterVar().setType(((ListType) node.getListExpr().getType()).getElementType());
      }

      if (node.getIndexVar() != null) {
        // Set the type of the optional index to integer ($index in this example).
        node.getIndexVar().setType(IntType.getInstance());
      }

      if (node.getFilterExpr() != null) {
        // Visit the optional filter expr, and make sure it evaluates to a boolean.
        visit(node.getFilterExpr());
      }

      // Resolve the type of the itemMapExpr, and use it to determine the comprehension's resulting
      // list type.
      visit(node.getListItemTransformExpr());
      node.setType(typeRegistry.getOrCreateListType(node.getListItemTransformExpr().getType()));

      tryApplySubstitution(node);
    }

    @Override
    protected void visitRecordLiteralNode(RecordLiteralNode node) {
      visitChildren(node);

      int numChildren = node.numChildren();
      checkState(numChildren == node.getKeys().size());
      checkState(numChildren > 0);

      List<RecordType.Member> members = new ArrayList<>();
      for (int i = 0; i < numChildren; i++) {
        members.add(RecordType.memberOf(node.getKey(i).identifier(), node.getChild(i).getType()));
      }
      node.setType(typeRegistry.getOrCreateRecordType(members));

      tryApplySubstitution(node);
    }

    @Override
    protected void visitMapLiteralNode(MapLiteralNode node) {
      visitChildren(node);

      int numChildren = node.numChildren();
      checkState(numChildren % 2 == 0);
      if (numChildren == 0) {
        node.setType(MapType.EMPTY_MAP);
        if (isDefaultInitializerForInferredParam) {
          errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty map");
        }
        return;
      }

      Set<String> duplicateKeyErrors = new HashSet<>();
      Map<String, SoyType> recordFieldTypes = new LinkedHashMap<>();
      List<SoyType> keyTypes = new ArrayList<>(numChildren / 2);
      List<SoyType> valueTypes = new ArrayList<>(numChildren / 2);
      Checkpoint checkpoint = errorReporter.checkpoint();
      for (int i = 0; i < numChildren; i += 2) {
        ExprNode key = node.getChild(i);
        ExprNode value = node.getChild(i + 1);
        // TODO: consider using ExprEquivalence to detect duplicate map keys
        if (key.getKind() == ExprNode.Kind.STRING_NODE) {
          String fieldName = ((StringNode) key).getValue();
          SoyType prev = recordFieldTypes.put(fieldName, value.getType());
          if (prev != null && duplicateKeyErrors.add(fieldName)) {
            errorReporter.report(key.getSourceLocation(), DUPLICATE_KEY_IN_MAP_LITERAL, fieldName);
          }
        }
        keyTypes.add(key.getType());
        if (!MapType.isAllowedKeyType(key.getType())) {
          errorReporter.report(key.getSourceLocation(), MapType.BAD_MAP_KEY_TYPE, key.getType());
        }
        valueTypes.add(value.getType());
      }
      SoyType commonKeyType = SoyTypes.computeLowestCommonType(typeRegistry, keyTypes);
      if (!errorReporter.errorsSince(checkpoint) && !MapType.isAllowedKeyType(commonKeyType)) {
        errorReporter.report(
            node.getSourceLocation(), ILLEGAL_MAP_RESOLVED_KEY_TYPE, commonKeyType);
      }
      SoyType commonValueType = SoyTypes.computeLowestCommonType(typeRegistry, valueTypes);
      node.setType(typeRegistry.getOrCreateMapType(commonKeyType, commonValueType));

      tryApplySubstitution(node);
    }

    @Override
    protected void visitVarRefNode(VarRefNode varRef) {
      SoyType newType = getTypeSubstitution(varRef);
      if (newType != null) {
        varRef.setSubstituteType(newType);
      } else {
        if (varRef.getType() == null) {
          // sanity check, default params and state params have complex type initialization logic
          // double check that it worked.
          throw new IllegalStateException(
              "VarRefNode @" + varRef.getSourceLocation() + " doesn't have a type!");
        }
      }
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
      visit(nullSafeAccessNode.getBase());
      visitNullSafeAccessNodeRecurse(nullSafeAccessNode);
    }

    private void visitNullSafeAccessNodeRecurse(NullSafeAccessNode nullSafeAccessNode) {
      if (nullSafeAccessNode.getBase().getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        errorReporter.report(nullSafeAccessNode.getSourceLocation(), UNNECESSARY_NULL_SAFE_ACCESS);
      }
      if (nullSafeAccessNode.getDataAccess().getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
        NullSafeAccessNode dataAccess = (NullSafeAccessNode) nullSafeAccessNode.getDataAccess();
        calculateAccessChainTypes(
            nullSafeAccessNode.getBase().getType(), (DataAccessNode) dataAccess.getBase());
        visitNullSafeAccessNodeRecurse(dataAccess);
      } else {
        AccessChainComponentNode dataAccess =
            (AccessChainComponentNode) nullSafeAccessNode.getDataAccess();
        DataAccessNode childDataAccess = getDataAccessChild(dataAccess);
        calculateAccessChainTypes(nullSafeAccessNode.getBase().getType(), childDataAccess);
        finishAssertNonNullOpNodeChain(dataAccess);
      }
      // TODO(b/138252762): This should be nullable.
      nullSafeAccessNode.setType(nullSafeAccessNode.getDataAccess().getType());
      tryApplySubstitution(nullSafeAccessNode);
    }

    private DataAccessNode getDataAccessChild(AccessChainComponentNode expr) {
      AccessChainComponentNode child = expr;
      while (child.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        child = (AccessChainComponentNode) ((AssertNonNullOpNode) child).getChild(0);
      }
      return (DataAccessNode) child;
    }

    private void finishAssertNonNullOpNodeChain(AccessChainComponentNode node) {
      if (node.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        AssertNonNullOpNode nonNullNode = (AssertNonNullOpNode) node;
        finishAssertNonNullOpNodeChain((AccessChainComponentNode) nonNullNode.getChild(0));
        finishAssertNonNullOpNode(nonNullNode);
      }
    }

    private void calculateAccessChainTypes(SoyType baseType, DataAccessNode dataAccess) {
      boolean nullSafe = true;
      if (dataAccess.getBaseExprChild() instanceof DataAccessNode) {
        calculateAccessChainTypes(baseType, (DataAccessNode) dataAccess.getBaseExprChild());
        nullSafe = false;
        baseType = dataAccess.getBaseExprChild().getType();
      } else if (dataAccess.getBaseExprChild().getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        AssertNonNullOpNode baseExpr = (AssertNonNullOpNode) dataAccess.getBaseExprChild();
        DataAccessNode childDataAccess = getDataAccessChild(baseExpr);
        calculateAccessChainTypes(baseType, childDataAccess);
        finishAssertNonNullOpNodeChain(baseExpr);
        nullSafe = false;
        baseType = dataAccess.getBaseExprChild().getType();
      }

      ExprNode base = dataAccess.getBaseExprChild();
      if (base.getKind() == ExprNode.Kind.GLOBAL_NODE) {
        GlobalNode global = (GlobalNode) base;
        if (!global.isResolved()) {
          global.resolve(baseType, new NullNode(base.getSourceLocation()));
        }
      }

      switch (dataAccess.getKind()) {
        case FIELD_ACCESS_NODE:
          finishFieldAccessNode((FieldAccessNode) dataAccess);
          break;
        case ITEM_ACCESS_NODE:
          finishItemAccessNode((ItemAccessNode) dataAccess, nullSafe);
          break;
        case METHOD_CALL_NODE:
          finishMethodCallNode((MethodCallNode) dataAccess, nullSafe);
          break;
        default:
          throw new AssertionError(dataAccess.getKind());
      }
    }

    @Override
    protected void visitFieldAccessNode(FieldAccessNode node) {
      checkState(!node.isNullSafe());
      visit(node.getBaseExprChild());
      finishFieldAccessNode(node);
    }

    private void finishFieldAccessNode(FieldAccessNode node) {
      node.setType(
          getFieldType(
              node.getBaseExprChild().getType(),
              node.getFieldName(),
              node.getAccessSourceLocation()));
      tryApplySubstitution(node);
    }

    @Override
    protected void visitItemAccessNode(ItemAccessNode node) {
      checkState(!node.isNullSafe());
      visit(node.getBaseExprChild());
      finishItemAccessNode(node, /* nullSafe= */ false);
    }

    private void finishItemAccessNode(ItemAccessNode node, boolean nullSafe) {
      visit(node.getKeyExprChild());
      SoyType itemType =
          getItemType(
              node.getBaseExprChild().getType(),
              node.getKeyExprChild().getType(),
              nullSafe,
              node.getAccessSourceLocation(),
              node.getKeyExprChild().getSourceLocation());
      node.setType(itemType);
      tryApplySubstitution(node);
    }

    @Override
    protected void visitMethodCallNode(MethodCallNode node) {
      checkState(!node.isNullSafe());
      visit(node.getBaseExprChild());
      finishMethodCallNode(node, /* nullSafe= */ false);
    }

    private void finishMethodCallNode(MethodCallNode node, boolean nullSafe) {
      for (ExprNode child : node.getParams()) {
        visit(child);
      }

      SoyType baseType = node.getBaseType(nullSafe);
      SoyMethod method = resolveMethodFromBaseType(node, baseType);

      if (method == null) {
        node.setType(ErrorType.getInstance());
        return;
      }

      node.setSoyMethod(method);

      if (method instanceof BuiltinMethod) {
        node.setType(((BuiltinMethod) method).getReturnType(node, typeRegistry, errorReporter));
      } else if (method instanceof SoySourceFunctionMethod) {
        SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) method;
        SoySourceFunction sourceFunction = sourceMethod.getImpl();
        if (sourceFunction instanceof ConcatListsFunction) {
          node.setType(getGenericListType(node.getChildren()));
        } else if (sourceFunction instanceof ListSliceMethod) {
          // list<T>.slice(...) returns list<T>
          node.setType(node.getBaseExprChild().getType());
        } else if (sourceFunction instanceof NumberListSortMethod) {
          // list<T>.sort() returns list<T>
          // The sort() method only supports lists of number, int, or float.
          node.setType(node.getBaseExprChild().getType());
        } else {
          node.setType(sourceMethod.getReturnType());
        }
      } else {
        throw new AssertionError();
      }
    }

    @Nullable
    private SoyMethod resolveMethodFromBaseType(MethodCallNode node, SoyType baseType) {
      if (SoyTypes.isNullable(baseType)) {
        errorReporter.report(
            node.getBaseExprChild().getSourceLocation(),
            METHOD_BASE_TYPE_NULL_SAFE_REQUIRED,
            baseType);
        return null;
      }

      int numParams = node.numChildren() - 1;
      String methodName = node.getMethodName().identifier();
      SourceLocation srcLoc = node.getAccessSourceLocation();
      List<SoyType> argTypes = node.getParams().stream().map(ExprNode::getType).collect(toList());

      // This contains all methods that match name and base type.
      ImmutableList<? extends SoyMethod> matchNameAndType =
          methodRegistry.matchForNameAndBase(methodName, baseType);

      // Subset of previous that also matches arg count.
      List<SoyMethod> andMatchArgCount =
          matchNameAndType.stream().filter(m -> m.getNumArgs() == numParams).collect(toList());

      if (!matchNameAndType.isEmpty() && andMatchArgCount.isEmpty()) {
        // We matched the base type and method name but did not match on arity.
        Set<Integer> allNumArgs =
            matchNameAndType.stream()
                .map(SoyMethod::getNumArgs)
                .collect(toImmutableSortedSet(naturalOrder()));
        String validSize = Joiner.on(" or ").join(allNumArgs);
        errorReporter.report(srcLoc, METHOD_INVALID_PARAM_NUM, methodName, numParams, validSize);
        return null;
      }

      // Subset of previous that also matches arg types.
      List<SoyMethod> andMatchArgType =
          andMatchArgCount.stream().filter(m -> m.appliesToArgs(argTypes)).collect(toList());

      if (andMatchArgType.size() == 1) {
        // Matched exactly one method. Success!
        SoyMethod method = andMatchArgCount.get(0);
        PluginResolver.warnIfDeprecated(errorReporter, methodName, method, srcLoc);
        return method;
      }

      if (!andMatchArgType.isEmpty()) {
        // Unexpected. Matched multiple methods. Plug-in validation should mostly prevent this but
        // methods applying to base type "any" could still cause this.
        errorReporter.report(srcLoc, MULTIPLE_METHODS_MATCH, methodName, numParams, baseType);
      } else if (!andMatchArgCount.isEmpty()) {
        // We matched base type, method name, and arity but not argument types.
        String expected =
            Joiner.on(", ").join(((SoySourceFunctionMethod) andMatchArgCount.get(0)).getArgTypes());
        String actual = Joiner.on(", ").join(argTypes);
        errorReporter.report(srcLoc, METHOD_INVALID_PARAM_TYPES, methodName, actual, expected);
      } else {
        String didYouMean = "";
        Set<String> matching =
            new HashSet<>(methodRegistry.matchForBaseAndArgs(baseType, argTypes).values());
        if (!matching.isEmpty()) {
          didYouMean = SoyErrors.getDidYouMeanMessage(matching, methodName);
        }
        // We did not match base type and method name. No method found.
        if (pluginResolutionMode == PluginResolver.Mode.REQUIRE_DEFINITIONS) {
          errorReporter.report(srcLoc, INVALID_METHOD_BASE, methodName, baseType, didYouMean);
        } else if (pluginResolutionMode == PluginResolver.Mode.ALLOW_UNDEFINED_AND_WARN) {
          errorReporter.warn(srcLoc, INVALID_METHOD_BASE, methodName, baseType, didYouMean);
        } else {
          // :( this is for kythe since we can't load plugin definitions since they are too
          // heavyweight.
        }
      }

      return null;
    }

    @Override
    protected void visitGlobalNode(GlobalNode node) {
      // Do nothing, global nodes already have type information.
    }

    @Override
    protected void visitNegativeOpNode(NegativeOpNode node) {
      visitChildren(node);
      SoyType childType = node.getChild(0).getType();
      if (SoyTypes.isNumericOrUnknown(childType)) {
        node.setType(childType);
      } else {
        errorReporter.report(
            node.getOperatorLocation(), INCOMPATIBLE_ARITHMETIC_OP_UNARY, childType);
        node.setType(UnknownType.getInstance());
      }
      tryApplySubstitution(node);
    }

    @Override
    protected void visitNotOpNode(NotOpNode node) {
      visitChildren(node);
      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitTimesOpNode(TimesOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override
    protected void visitDivideByOpNode(DivideByOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override
    protected void visitModOpNode(ModOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override
    protected void visitPlusOpNode(PlusOpNode node) {
      visitChildren(node);
      SoyType left = node.getChild(0).getType();
      SoyType right = node.getChild(1).getType();
      SoyType result =
          SoyTypes.getSoyTypeForBinaryOperator(left, right, new SoyTypes.SoyTypePlusOperator());
      if (result == null) {
        errorReporter.report(node.getOperatorLocation(), INCOMPATIBLE_ARITHMETIC_OP, left, right);
        result = UnknownType.getInstance();
      }
      node.setType(result);
      tryApplySubstitution(node);
    }

    @Override
    protected void visitMinusOpNode(MinusOpNode node) {
      visitArithmeticOpNode(node);
    }

    @Override
    protected void visitLessThanOpNode(LessThanOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override
    protected void visitGreaterThanOpNode(GreaterThanOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override
    protected void visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override
    protected void visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
      visitComparisonOpNode(node);
    }

    @Override
    protected void visitEqualOpNode(EqualOpNode node) {
      visitEqualComparisonOpNode(node);
    }

    @Override
    protected void visitNotEqualOpNode(NotEqualOpNode node) {
      visitEqualComparisonOpNode(node);
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      visit(node.getChild(0)); // Assign normal types to left child

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the left hand side to help narrow types used on the right hand side.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // For 'and' the second child only gets evaluated if node 0 is truthy.  So apply the positive
      // assertions.
      addTypeSubstitutions(visitor.positiveTypeConstraints);
      visit(node.getChild(1));

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitOrOpNode(OrOpNode node) {
      ExprNode lhs = node.getChild(0);
      if (SoyTreeUtils.isConstantExpr(lhs)) {
        errorReporter.warn(
            node.getOperatorLocation(), OR_OPERATOR_HAS_CONSTANT_OPERAND, lhs.toSourceString());
      }
      visit(lhs); // Assign normal types to left child

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the left hand side to help narrow types used on the right hand side.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // For 'or' the second child only gets evaluated if node 0 is falsy.  So apply the negative
      // assertions.
      addTypeSubstitutions(visitor.negativeTypeConstraints);
      ExprNode rhs = node.getChild(1);
      visit(rhs);
      if (SoyTreeUtils.isConstantExpr(rhs)) {
        errorReporter.warn(
            node.getOperatorLocation(), OR_OPERATOR_HAS_CONSTANT_OPERAND, rhs.toSourceString());
      }

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // Now, re-visit the first node but with substitutions. The reason is because
      // the value of node 0 is what will be returned if node 0 is truthy.
      addTypeSubstitutions(visitor.positiveTypeConstraints);
      visit(node.getChild(0));

      // For the null-coalescing operator, the node 1 only gets evaluated
      // if node 0 is falsey. Use the negative substitutions for this case.
      addTypeSubstitutions(visitor.negativeTypeConstraints);
      visit(node.getChild(1));

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      node.setType(
          SoyTypes.computeLowestCommonType(
              typeRegistry, node.getChild(0).getType(), node.getChild(1).getType()));
      tryApplySubstitution(node);
    }

    @Override
    protected void visitConditionalOpNode(ConditionalOpNode node) {
      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitution savedSubstitutionState = substitutions;

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = new TypeNarrowingConditionVisitor();
      visitor.visitAndImplicitlyCastToBoolean(node.getChild(0));

      // Modify the current set of type substitutions for the 'true' branch
      // of the conditional.
      addTypeSubstitutions(visitor.positiveTypeConstraints);
      visit(node.getChild(1));

      // Rewind the substitutions back to the state before the expression.
      // Add in the negative substitutions, which will affect the 'false'
      // branch.
      substitutions = savedSubstitutionState;
      addTypeSubstitutions(visitor.negativeTypeConstraints);
      visit(node.getChild(2));

      // Restore substitutions to previous state
      substitutions = savedSubstitutionState;

      // For a conditional node, it will return either child 1 or 2.
      node.setType(
          SoyTypes.computeLowestCommonType(
              typeRegistry, node.getChild(1).getType(), node.getChild(2).getType()));
      tryApplySubstitution(node);
    }

    /**
     * Converts a signature annotation (string representation of type information) to a resolved
     * signature (actual Soy type).
     *
     * @param signature The input signature.
     * @param className The class name of the Soy function that has the signature annotation. This
     *     is also used as a fake file path in the reported error.
     * @param errorReporter The Soy error reporter.
     */
    private @Nullable ResolvedSignature getOrCreateFunctionSignature(
        Signature signature, String className, ErrorReporter errorReporter) {
      ResolvedSignature resolvedSignature = signatureMap.get(signature);
      if (resolvedSignature != null) {
        return resolvedSignature;
      }
      ImmutableList.Builder<SoyType> paramTypes = ImmutableList.builder();
      for (String paramTypeString : signature.parameterTypes()) {
        TypeNode paramType = SoyFileParser.parseType(paramTypeString, className, errorReporter);
        if (paramType == null) {
          return null;
        }
        paramTypes.add(pluginTypeConverter.getOrCreateType(paramType));
      }
      TypeNode returnType =
          SoyFileParser.parseType(signature.returnType(), className, errorReporter);
      if (returnType == null) {
        return null;
      }
      resolvedSignature =
          ResolvedSignature.create(
              paramTypes.build(), pluginTypeConverter.getOrCreateType(returnType));
      signatureMap.put(signature, resolvedSignature);
      return resolvedSignature;
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      visitChildren(node);
      Object knownFunction = node.getSoyFunction();
      if (knownFunction.getClass().isAnnotationPresent(SoyFunctionSignature.class)) {
        checkState(
            knownFunction instanceof TypedSoyFunction || knownFunction instanceof SoySourceFunction,
            "Classes annotated with @SoyFunctionSignature must either extend "
                + "TypedSoyFunction or implement SoySourceFunction.");
        visitSoyFunctionWithSignature(
            knownFunction.getClass().getAnnotation(SoyFunctionSignature.class),
            knownFunction.getClass().getCanonicalName(),
            node);
      } else if (knownFunction instanceof BuiltinFunction) {
        visitBuiltinFunction((BuiltinFunction) knownFunction, node);
      }


      // Always attempt to visit for internal soy functions, even if we already had a signature.
      visitInternalSoyFunction(knownFunction, node);
      tryApplySubstitution(node);

      // If we didn't set the allowed types for params above, then set them to unknown types.
      if (node.getAllowedParamTypes() == null) {
        node.setAllowedParamTypes(
            Collections.nCopies(node.numChildren(), UnknownType.getInstance()));
      }
    }

    /**
     * For soy functions with type annotation, perform the strict type checking and set the return
     * type.
     */
    private void visitSoyFunctionWithSignature(
        SoyFunctionSignature fnSignature, String className, FunctionNode node) {
      ResolvedSignature matchedSignature = null;
      // Found the matched signature for the current function call.
      for (Signature signature : fnSignature.value()) {
        if (signature.parameterTypes().length == node.numChildren()) {
          matchedSignature = getOrCreateFunctionSignature(signature, className, errorReporter);
          break;
        }
      }
      // TODO(b/71386491): Maybe we should set this to ErrorType
      if (matchedSignature == null) {
        node.setType(UnknownType.getInstance());
        return;
      }
      for (int i = 0; i < node.numChildren(); ++i) {
        checkArgType(node.getChild(i), matchedSignature.parameterTypes().get(i), node);
      }
      node.setAllowedParamTypes(matchedSignature.parameterTypes());
      node.setType(matchedSignature.returnType());
    }

    private void visitKeysFunction(FunctionNode node) {
      ListType listType;
      SoyType argType = node.getChild(0).getType();
      if (argType.equals(LegacyObjectMapType.EMPTY_MAP)) {
        listType = ListType.EMPTY_LIST;
      } else {
        SoyType listArg;
        if (argType.getKind() == Kind.LEGACY_OBJECT_MAP) {
          listArg = ((LegacyObjectMapType) argType).getKeyType(); // pretty much just string
        } else if (argType.getKind() == Kind.LIST) {
          listArg = IntType.getInstance();
        } else if (argType.getKind() == Kind.MAP) {
          errorReporter.report(node.getSourceLocation(), KEYS_PASSED_MAP);
          listArg = ErrorType.getInstance();
        } else {
          listArg = UnknownType.getInstance();
        }
        listType = typeRegistry.getOrCreateListType(listArg);
      }
      node.setType(listType);
    }

    private void visitMapKeysFunction(FunctionNode node) {
      SoyType argType = node.getChild(0).getType();
      if (argType.equals(MapType.EMPTY_MAP)) {
        node.setType(ListType.EMPTY_LIST);
      } else {
        node.setType(typeRegistry.getOrCreateListType(((MapType) argType).getKeyType()));
      }
    }

    private void visitLegacyObjectMapToMapFunction(FunctionNode node) {
      SoyType argType = node.getChild(0).getType();
      if (argType.equals(LegacyObjectMapType.EMPTY_MAP)) {
        node.setType(MapType.EMPTY_MAP);
      } else if (argType.isAssignableFrom(UnknownType.getInstance())) {
        // Allow the type of the arg to be unknown as legacy_object_map functionality on unknown
        // types is allowed (i.e. bracket access on a variable with an unknown type).
        node.setType(
            typeRegistry.getOrCreateMapType(StringType.getInstance(), UnknownType.getInstance()));
      } else {
        LegacyObjectMapType actualArgType = (LegacyObjectMapType) argType;
        node.setType(
            typeRegistry.getOrCreateMapType(
                // Converting a legacy_object_map<K,V> to a map creates a value of type
                // map<string, V>, not map<K, V>. legacy_object_map<K, ...> is misleading:
                // although Soy will type check K consistently, the runtime implementation of
                // legacy_object_map just coerces the key to a string.
                // b/69051605 will change many Soy params to have a type of map<string, ...>,
                // so legacyObjectMapToMap() needs to have this return type too.
                StringType.getInstance(), actualArgType.getValueType()));
      }
    }

    private void visitMapToLegacyObjectMapFunction(FunctionNode node) {
      SoyType argType = node.getChild(0).getType();
      if (argType.equals(MapType.EMPTY_MAP)) {
        node.setType(LegacyObjectMapType.EMPTY_MAP);
      } else {
        MapType actualArgType = (MapType) argType;
        node.setType(
            typeRegistry.getOrCreateLegacyObjectMapType(
                // Converting a map to a legacy object map coerces all the keys to strings
                StringType.getInstance(), actualArgType.getValueType()));
      }
    }

    @Override
    protected void visitProtoInitNode(ProtoInitNode node) {
      visitChildren(node);

      String protoName = node.getProtoName();
      SoyType type = typeRegistry.getType(protoName);

      if (type == null) {
        errorReporter.report(node.getSourceLocation(), UNKNOWN_PROTO_TYPE, protoName);
        node.setType(ErrorType.getInstance());
        return;
      }
      if (type.getKind() != SoyType.Kind.PROTO) {
        errorReporter.report(node.getSourceLocation(), NOT_A_PROTO_TYPE, protoName, type);
        node.setType(ErrorType.getInstance());
        return;
      }
      if (SAFE_PROTO_TO_SANITIZED_TYPE.containsKey(protoName)) {
        errorReporter.report(
            node.getSourceLocation(),
            TypeNodeConverter.SAFE_PROTO_TYPE,
            SAFE_PROTO_TO_SANITIZED_TYPE.get(protoName),
            protoName);
        node.setType(ErrorType.getInstance());
        return;
      }

      node.setType(type);

      SoyProtoType protoType = (SoyProtoType) type;
      // TODO(user): Consider writing a soyProtoTypeImpl.getRequiredFields()
      Set<String> givenParams = new HashSet<>();
      ImmutableSet<String> fields = protoType.getFieldNames();

      SoyFileNode file = exprHolderNode.getNearestAncestor(SoyFileNode.class);
      boolean hasAliasedParams = false;
      List<Identifier> resolvedIdentifiers = new ArrayList<>();

      // Resolve aliases for the given field names of the proto.
      for (Identifier id : node.getParamNames()) {
        String originalName = id.identifier();
        Identifier resolvedName = file.resolveAlias(id);
        if (!resolvedName.identifier().equals(originalName)) {
          // Check that the aliased name does not conflict with a field in the proto as we cannot
          // determine whether the intended field to instantiate is the regular field or the
          // aliased value.
          if (fields.contains(originalName)
              && !protoType.getFieldDescriptor(originalName).isExtension()) {
            errorReporter.report(
                id.location(),
                PROTO_FIELD_NAME_ALIAS_CONFLICT,
                originalName,
                protoType.getDescriptor().getName());
            node.setType(ErrorType.getInstance());
            continue;
          }
          hasAliasedParams = true;
          id = resolvedName;
        }
        resolvedIdentifiers.add(id);
        givenParams.add(id.identifier());
      }

      if (node.getType().getKind() == Kind.ERROR) {
        return;
      }

      // Replace the ProtoInitNode to have a list of the resolved param names.
      if (hasAliasedParams) {
        ProtoInitNode resolvedNode =
            new ProtoInitNode(node.getIdentifier(), resolvedIdentifiers, node.getSourceLocation());
        resolvedNode.setType(node.getType());
        resolvedNode.addChildren(node.getChildren());
        node.getParent().replaceChild(node, resolvedNode);
        node = resolvedNode;
      }

      // Check that all proto required fields are present.
      for (String field : fields) {
        if (protoType.getFieldDescriptor(field).isRequired() && !givenParams.contains(field)) {
          errorReporter.report(node.getSourceLocation(), PROTO_MISSING_REQUIRED_FIELD, field);
        }
      }

      for (int i = 0; i < node.numChildren(); i++) {
        Identifier fieldName = node.getParamNames().get(i);
        ExprNode expr = node.getChild(i);

        // Check that each arg exists in the proto.
        if (!fields.contains(fieldName.identifier())) {
          String extraErrorMessage =
              SoyErrors.getDidYouMeanMessageForProtoFields(
                  fields, protoType.getDescriptor(), fieldName.identifier());
          errorReporter.report(
              fieldName.location(),
              PROTO_FIELD_DOES_NOT_EXIST,
              fieldName.identifier(),
              protoType,
              extraErrorMessage);
          continue;
        }

        // Check that the arg type is not null and that it matches the expected field type.
        SoyType argType = expr.getType();
        if (argType.equals(NullType.getInstance())) {
          errorReporter.report(
              expr.getSourceLocation(), PROTO_NULL_ARG_TYPE, fieldName.identifier());
        }

        SoyType fieldType = protoType.getFieldType(fieldName.identifier());

        // Let args with unknown or error types pass
        if (argType.equals(UnknownType.getInstance()) || argType.equals(ErrorType.getInstance())) {
          continue;
        }

        // Same for List<?>, for repeated fields
        if (fieldType.getKind() == Kind.LIST && argType.getKind() == Kind.LIST) {
          SoyType argElementType = ((ListType) argType).getElementType();
          if (argElementType == null || argElementType.equals(UnknownType.getInstance())) {
            continue;
          }
        }

        SoyType expectedType = SoyTypes.makeNullable(fieldType);
        if (!expectedType.isAssignableFrom(argType)) {
          argType =
              RuntimeTypeCoercion.maybeCoerceType(
                  expr,
                  expectedType instanceof UnionType
                      ? ((UnionType) expectedType).getMembers()
                      : ImmutableList.of(expectedType));
        }
        if (!expectedType.isAssignableFrom(argType)) {
          errorReporter.report(
              expr.getSourceLocation(),
              ARGUMENT_TYPE_MISMATCH,
              fieldName.identifier(),
              expectedType,
              argType);
        }
      }
    }

    @Override
    protected void visitVeLiteralNode(VeLiteralNode node) {
      SoyType type;
      ValidatedLoggableElement config = loggingConfig.getElement(node.getName().identifier());
      if (config == null) {
        errorReporter.report(
            node.getName().location(),
            VE_NO_CONFIG_FOR_ELEMENT,
            SoyErrors.getDidYouMeanMessage(
                loggingConfig.allKnownIdentifiers(), node.getName().identifier()));
        type = ErrorType.getInstance();
      } else {
        if (config.getProtoName().isPresent()) {
          type = typeRegistry.getOrCreateVeType(config.getProtoName().get());
        } else {
          type = VeType.NO_DATA;
        }
        node.setLoggableElement(config);
      }
      node.setType(type);
    }

    @Override
    protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
      // Template literals are not legal as default values without a declared type. This is because
      // we don't have enough information to resolve the type at the time this pass is run. For
      // example, two templates may have each other as default parameters, which would create a
      // circular dependency for the type resolution.
      if (isDefaultInitializerForInferredParam) {
        errorReporter.report(
            node.getSourceLocation(), TEMPLATE_TYPE_PARAMETERS_CANNOT_USE_INFERRED_TYPES);
      }
      // Template literal nodes are instantiated with a temporary type because we don't have enough
      // information to give them a type at the time this pass is run -- we need to know the
      // signature of the referenced template. The type is resolved and checked in a later pass.
    }

    private void visitComparisonOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      SoyType left = node.getChild(0).getType();
      SoyType right = node.getChild(1).getType();
      SoyType result =
          SoyTypes.getSoyTypeForBinaryOperator(left, right, new SoyTypes.SoyTypeComparisonOp());
      if (result == null) {
        errorReporter.report(node.getOperatorLocation(), TYPE_MISMATCH, left, right);
      }
      node.setType(BoolType.getInstance());
    }

    private void visitEqualComparisonOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      SoyType left = node.getChild(0).getType();
      SoyType right = node.getChild(1).getType();
      SoyType result =
          SoyTypes.getSoyTypeForBinaryOperator(
              left, right, new SoyTypes.SoyTypeEqualComparisonOp());
      if (result == null) {
        errorReporter.report(node.getOperatorLocation(), TYPE_MISMATCH, left, right);
      }
      node.setType(BoolType.getInstance());
    }

    private void visitArithmeticOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      boolean isDivide = node instanceof DivideByOpNode;
      SoyType left = node.getChild(0).getType();
      SoyType right = node.getChild(1).getType();
      SoyType result =
          SoyTypes.getSoyTypeForBinaryOperator(
              left, right, new SoyTypes.SoyTypeArithmeticOperator());
      if (result == null) {
        errorReporter.report(node.getOperatorLocation(), INCOMPATIBLE_ARITHMETIC_OP, left, right);
        result = UnknownType.getInstance();
      }
      // Division is special. it is always coerced to a float. For other operators, use the value
      // returned by getSoyTypeForBinaryOperator.
      // TODO(b/64098780): Should we add nullability to divide operator? Probably not, but we should
      // also consolidate the behaviors when we divide something by 0 or null.
      node.setType(isDivide ? FloatType.getInstance() : result);
      tryApplySubstitution(node);
    }

    /**
     * Helper function that reports an error if a node's type field is {@code null}. The error will
     * show what kind of node it was, and where in the template it was found.
     */
    private void requireNodeType(ExprNode node) {
      if (node.getType() == null) {
        errorReporter.report(node.getSourceLocation(), MISSING_SOY_TYPE, node.getClass().getName());
      }
    }

    /**
     * Given a base type and a field name, compute the field type.
     *
     * @param baseType The base type.
     * @param fieldName The name of the field.
     * @param sourceLocation The source location of the expression
     * @return The type of the field.
     */
    private SoyType getFieldType(
        SoyType baseType, String fieldName, SourceLocation sourceLocation) {
      Preconditions.checkNotNull(baseType);
      switch (baseType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the field type.
          return UnknownType.getInstance();

        case RECORD:
          {
            RecordType recordType = (RecordType) baseType;
            SoyType fieldType = recordType.getMemberType(fieldName);
            if (fieldType != null) {
              return fieldType;
            } else {
              String extraErrorMessage =
                  SoyErrors.getDidYouMeanMessage(recordType.getMemberNames(), fieldName);
              errorReporter.report(
                  sourceLocation,
                  UNDEFINED_FIELD_FOR_RECORD_TYPE,
                  fieldName,
                  baseType,
                  extraErrorMessage);
              return ErrorType.getInstance();
            }
          }

        case PROTO:
          {
            SoyProtoType protoType = (SoyProtoType) baseType;
            SoyType fieldType = protoType.getFieldType(fieldName);
            if (fieldType != null) {
              return fieldType;
            } else {
              String extraErrorMessage =
                  SoyErrors.getDidYouMeanMessageForProtoFields(
                      protoType.getFieldNames(), protoType.getDescriptor(), fieldName);
              errorReporter.report(
                  sourceLocation,
                  UNDEFINED_FIELD_FOR_PROTO_TYPE,
                  fieldName,
                  baseType,
                  extraErrorMessage);
              return ErrorType.getInstance();
            }
          }

        case LEGACY_OBJECT_MAP:
          {
            errorReporter.report(
                sourceLocation, DOT_ACCESS_NOT_SUPPORTED_CONSIDER_RECORD, baseType);
            return ErrorType.getInstance();
          }

        case UNION:
          {
            // If it's a union, then do the field type calculation for each member of
            // the union and combine the result.
            UnionType unionType = (UnionType) baseType;
            List<SoyType> fieldTypes = new ArrayList<>(unionType.getMembers().size());
            for (SoyType unionMember : unionType.getMembers()) {
              // TODO: Only exclude nulls when FieldAccessNode is null-safe.
              if (unionMember.getKind() == SoyType.Kind.NULL) {
                continue;
              }
              SoyType fieldType = getFieldType(unionMember, fieldName, sourceLocation);
              // If this member's field type resolved to an error, bail out to avoid spamming
              // the user with multiple error messages for the same line.
              if (fieldType == ErrorType.getInstance()) {
                return fieldType;
              }
              fieldTypes.add(fieldType);
            }
            return SoyTypes.computeLowestCommonType(typeRegistry, fieldTypes);
          }

        case ERROR:
          // report no additional errors
          return ErrorType.getInstance();

          // calling .length on strings/lists is common in v1 templates. So provide better error
          // messages for when users are migrating.
        case STRING:
        case CSS:
        case JS:
        case ATTRIBUTES:
        case HTML:
        case URI:
          if (fieldName.equals("length")) {
            errorReporter.report(sourceLocation, STRING_LENGTH_ERROR);
            return ErrorType.getInstance();
          }
          // else fall through

        case LIST:
          if (fieldName.equals("length")) {
            errorReporter.report(sourceLocation, LIST_LENGTH_ERROR);
            return ErrorType.getInstance();
          }
          // else fall through

        case ANY:
        case NULL:
        case BOOL:
        case INT:
        case FLOAT:
        case TRUSTED_RESOURCE_URI:
        case MAP:
        case NAMED_TEMPLATE:
        case PROTO_ENUM:
        case TEMPLATE:
        case VE:
        case VE_DATA:
        case MESSAGE:
          errorReporter.report(sourceLocation, DOT_ACCESS_NOT_SUPPORTED, baseType);
          return ErrorType.getInstance();
      }
      throw new AssertionError("unhandled kind: " + baseType.getKind());
    }

    /** Given a base type and an item key type, compute the item value type. */
    private SoyType getItemType(
        SoyType baseType,
        SoyType keyType,
        boolean isNullSafe,
        SourceLocation baseLocation,
        SourceLocation keyLocation) {
      Preconditions.checkNotNull(baseType);
      Preconditions.checkNotNull(keyType);
      switch (baseType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the item type.
          return UnknownType.getInstance();

        case LIST:
          ListType listType = (ListType) baseType;
          if (listType.equals(ListType.EMPTY_LIST)) {
            errorReporter.report(baseLocation, EMPTY_LIST_ACCESS);
            return ErrorType.getInstance();
          }

          // For lists, the key type must either be unknown or assignable to integer.
          if (keyType.getKind() != SoyType.Kind.UNKNOWN
              && !IntType.getInstance().isAssignableFrom(keyType)) {
            errorReporter.report(keyLocation, BAD_INDEX_TYPE, keyType, baseType);
            // fall through and report the element type.  This will allow more later type checks to
            // be evaluated.
          }
          return listType.getElementType();

        case LEGACY_OBJECT_MAP:
        case MAP:
          {
            AbstractMapType mapType = (AbstractMapType) baseType;
            if (mapType.equals(LegacyObjectMapType.EMPTY_MAP)
                || mapType.equals(MapType.EMPTY_MAP)) {
              errorReporter.report(baseLocation, EMPTY_MAP_ACCESS);
              return ErrorType.getInstance();
            }

            // For maps, the key type must either be unknown or assignable to the declared key type.
            if (keyType.getKind() != SoyType.Kind.UNKNOWN
                && !mapType.getKeyType().isAssignableFrom(keyType)) {
              errorReporter.report(keyLocation, BAD_KEY_TYPE, keyType, baseType);
              // fall through and report the value type.  This will allow more later type checks to
              // be evaluated.
            }
            return mapType.getValueType();
          }
        case UNION:
          {
            // If it's a union, then do the item type calculation for each member of
            // the union and combine the result.
            UnionType unionType = (UnionType) baseType;
            List<SoyType> itemTypes = new ArrayList<>(unionType.getMembers().size());
            for (SoyType unionMember : unionType.getMembers()) {
              // Skips null types for now.
              if (unionMember.equals(NullType.getInstance())) {
                continue;
              }
              SoyType itemType =
                  getItemType(unionMember, keyType, isNullSafe, baseLocation, keyLocation);
              // If this member's item type resolved to an error, bail out to avoid spamming
              // the user with multiple error messages for the same line.
              if (itemType == ErrorType.getInstance()) {
                return itemType;
              }
              itemTypes.add(itemType);
            }
            // If this is a nullable union type but the operation is not null-safe, report an error.
            if (unionType.isNullable() && !isNullSafe) {
              errorReporter.report(baseLocation, BRACKET_ACCESS_NULLABLE_UNION);
              return ErrorType.getInstance();
            }
            return SoyTypes.computeLowestCommonType(typeRegistry, itemTypes);
          }

        case ERROR:
          return ErrorType.getInstance();

        case ANY:
        case NULL:
        case BOOL:
        case INT:
        case FLOAT:
        case STRING:
        case HTML:
        case ATTRIBUTES:
        case JS:
        case CSS:
        case URI:
        case TRUSTED_RESOURCE_URI:
        case RECORD:
        case PROTO:
        case PROTO_ENUM:
        case NAMED_TEMPLATE:
        case TEMPLATE:
        case VE:
        case VE_DATA:
        case MESSAGE:
          errorReporter.report(baseLocation, BRACKET_ACCESS_NOT_SUPPORTED, baseType);
          return ErrorType.getInstance();
      }
      throw new AssertionError("unhandled kind: " + baseType.getKind());
    }

    private void tryApplySubstitution(AbstractParentExprNode parentNode) {
      SoyType newType = getTypeSubstitution(parentNode);
      if (newType != null) {
        if (!parentNode.getType().isAssignableFrom(newType)) {
          errorReporter.report(
              parentNode.getSourceLocation(),
              INVALID_TYPE_SUBSTITUTION,
              parentNode.getType(),
              newType);
        }
        parentNode.setType(newType);
      }
    }

    @Nullable
    private SoyType getTypeSubstitution(ExprNode expr) {
      // If there's a type substitution in effect for this expression, then change
      // the type of the variable reference to the substituted type.
      for (TypeSubstitution subst = substitutions; subst != null; subst = subst.parent) {
        if (exprEquivalence.equivalent(subst.expression, expr)) {
          return subst.type;
        }
      }
      return null;
    }

    /**
     * Private helper that checks types of the arguments and tries to set the return type for some
     * built-in functions.
     */
    private void visitBuiltinFunction(BuiltinFunction builtinFunction, FunctionNode node) {
      switch (builtinFunction) {
        case CHECK_NOT_NULL:
          SoyType type = node.getChild(0).getType();
          if (type.equals(NullType.getInstance())) {
            errorReporter.report(
                node.getSourceLocation(), CHECK_NOT_NULL_ON_COMPILE_TIME_NULL, "call checkNotNull");
          } else {
            // Same type as its child but with nulls removed
            node.setType(SoyTypes.removeNull(type));
          }
          break;
        case INDEX:
          requireLoopVariableInScope(node, 0);
          node.setType(IntType.getInstance());
          break;
        case IS_PRIMARY_MSG_IN_USE:
          // don't bother checking the args, they are only ever set by the MsgIdFunctionPass
          node.setType(BoolType.getInstance());
          break;
        case IS_FIRST:
        case IS_LAST:
          requireLoopVariableInScope(node, 0);
          node.setType(BoolType.getInstance());
          break;
        case CSS:
          checkArgIsStringLiteral(node, node.numChildren() - 1, builtinFunction);
          node.setType(StringType.getInstance());
          break;
        case SOY_SERVER_KEY:
        case XID:
          // arg validation is already handled by the XidPass
          node.setType(StringType.getInstance());
          break;
        case UNKNOWN_JS_GLOBAL:
        case V1_EXPRESSION:
          checkArgIsStringLiteral(node, 0, builtinFunction);
          node.setType(UnknownType.getInstance());
          break;
        case DEBUG_SOY_TEMPLATE_INFO:
          node.setType(BoolType.getInstance());
          break;
        case VE_DATA:
          // Arg validation is already handled by the VeLogValidationPass
          node.setType(VeDataType.getInstance());
          break;
        case TO_FLOAT: // is added to the AST after this pass
        case REMAINDER:
          node.setType(IntType.getInstance());
          break;
        case MSG_WITH_ID:
          node.setType(
              RecordType.of(
                  ImmutableMap.of(
                      "id",
                      StringType.getInstance(),
                      "msg",
                      node.numChildren() > 0
                          ? node.getChild(0).getType()
                          : UnknownType.getInstance())));
      }
    }

    /** Private helper that reports an error if the argument is not a string literal. */
    private void checkArgIsStringLiteral(
        FunctionNode node, int childIndex, BuiltinFunction funcName) {
      if (childIndex < 0 || childIndex >= node.numChildren()) {
        return;
      }

      ExprNode arg = node.getChild(childIndex);
      if (!(arg instanceof StringNode)) {
        errorReporter.report(arg.getSourceLocation(), STRING_LITERAL_REQUIRED, funcName.getName());
      }
    }

    /**
     * Private helper that checks types of the arguments and tries to set the return type for some
     * basic functions provided by Soy.
     */
    private void visitInternalSoyFunction(Object fn, FunctionNode node) {
      // Here we have special handling for a variety of 'generic' function.
      if (fn instanceof LegacyObjectMapToMapFunction) {
        // If argument type is incorrect, do not try to create a return type. Instead, set the
        // return type to unknown.
        if (checkArgType(node.getChild(0), LegacyObjectMapType.ANY_MAP, node)) {
          visitLegacyObjectMapToMapFunction(node);
        } else {
          node.setType(UnknownType.getInstance());
        }
      } else if (fn instanceof MapToLegacyObjectMapFunction) {
        // If argument type is incorrect, do not try to create a return type. Instead, set the
        // return type to unknown.
        // We disallow unknown for this function in order to ensure that maps remain strongly typed
        if (checkArgType(node.getChild(0), MapType.ANY_MAP, node, UnknownPolicy.DISALLOWED)) {
          visitMapToLegacyObjectMapFunction(node);
        } else {
          node.setType(UnknownType.getInstance());
        }
      } else if (fn instanceof KeysFunction) {
        visitKeysFunction(node);
      } else if (fn instanceof MapKeysFunction) {
        // We disallow unknown for this function in order to ensure that maps remain strongly typed
        if (checkArgType(node.getChild(0), MapType.ANY_MAP, node, UnknownPolicy.DISALLOWED)) {
          visitMapKeysFunction(node);
        } else {
          node.setType(UnknownType.getInstance());
        }
      } else if (fn instanceof ConcatListsFunction) {
        node.setType(getGenericListType(node.getChildren()));
      } else if (fn instanceof LoggingFunction) {
        // LoggingFunctions always return string.
        node.setType(StringType.getInstance());
      } else if (node.getType() == null) {
        // We have no way of knowing the return type of a function.
        // TODO: think about adding function type declarations.
        // TODO(b/70946095): at the very least we could hard code types for standard functions for
        // example, everything in the BasicFunctionsModule.
        // TODO(b/70946095): Maybe we should set to ErrorType if checkArgType failed.
        node.setType(UnknownType.getInstance());
      }
    }

    private SoyType getGenericListType(Iterable<ExprNode> intersectionOf) {
      ImmutableSet.Builder<SoyType> elementTypesBuilder = ImmutableSet.builder();
      for (ExprNode childNode : intersectionOf) {
        SoyType elementType = ((ListType) childNode.getType()).getElementType();
        if (elementType != null) { // Empty lists have no element type
          elementTypesBuilder.add(elementType);
        }
      }

      ImmutableSet<SoyType> elementTypes = elementTypesBuilder.build();
      return elementTypes.isEmpty()
          ? ListType.EMPTY_LIST
          : typeRegistry.getOrCreateListType(typeRegistry.getOrCreateUnionType(elementTypes));
    }

    /** @param fn The function that must take a loop variable. */
    private void requireLoopVariableInScope(FunctionNode fn, int childIndex) {
      if (childIndex < 0 || childIndex >= fn.numChildren()) {
        return;
      }

      ExprNode loopVariable = fn.getChild(childIndex);
      if (!(loopVariable instanceof VarRefNode
          && ((VarRefNode) loopVariable).getDefnDecl() instanceof LocalVar
          && ((LocalVar) ((VarRefNode) loopVariable).getDefnDecl()).declaringNode()
              instanceof ForNonemptyNode)) {
        errorReporter.report(
            fn.getSourceLocation(), LOOP_VARIABLE_NOT_IN_SCOPE, fn.getFunctionName());
      }
    }

    /** Checks the argument type. Returns false if an incorrect arg type error was reported. */
    private boolean checkArgType(ExprNode arg, SoyType expectedType, FunctionNode node) {
      return checkArgType(arg, expectedType, node, UnknownPolicy.ALLOWED);
    }

    /** Checks the argument type. Returns false if an incorrect arg type error was reported. */
    private boolean checkArgType(
        ExprNode arg, SoyType expectedType, FunctionNode node, UnknownPolicy policy) {
      SoyType.Kind argTypeKind = arg.getType().getKind();
      if (argTypeKind == SoyType.Kind.ERROR || expectedType.getKind() == SoyType.Kind.ERROR) {
        return false;
      }
      if (policy == UnknownPolicy.ALLOWED && argTypeKind == SoyType.Kind.UNKNOWN) {
        return true;
      }
      if (!expectedType.isAssignableFrom(arg.getType())) {
        errorReporter.report(
            arg.getSourceLocation(),
            INCORRECT_ARG_TYPE,
            node.getFunctionName(),
            arg.getType(),
            expectedType);
        return false;
      }
      return true;
    }
  }

  /**
   * Visitor which analyzes a boolean expression and determines if any of the variables involved in
   * the expression can have their types narrowed depending on the outcome of the condition.
   *
   * <p>For example, if the condition is "$var != null", then we know that if the condition is true,
   * then $var cannot be of null type. We also know that if the condition is false, then $var must
   * be of the null type.
   *
   * <p>The result of the analysis is two "constraint maps", which map variables to types,
   * indicating that the variable satisfies the criteria of being of that type.
   *
   * <p>The "positiveConstraints" map is the set of constraints that will be satisfied if the
   * condition is true, the "negativeConstraints" is the set of constraints that will be satisfied
   * if the condition is false.
   *
   * <p>TODO(user) - support instanceof tests. Right now the only type tests supported are
   * comparisons with null. If we added an 'instanceof' operator to Soy, the combination of
   * instanceof + flow-based type analysis would effectively allow template authors to do typecasts,
   * without having to add a cast operator to the language.
   */
  private final class TypeNarrowingConditionVisitor extends AbstractExprNodeVisitor<Void> {
    // Type constraints that are valid if the condition is true.
    Map<ExprEquivalence.Wrapper, SoyType> positiveTypeConstraints = new LinkedHashMap<>();

    // Type constraints that are valid if the condition is false.
    Map<ExprEquivalence.Wrapper, SoyType> negativeTypeConstraints = new LinkedHashMap<>();

    @Override
    public Void exec(ExprNode node) {
      visit(node);
      return null;
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visitAndImplicitlyCastToBoolean(node.getRoot());
    }

    void visitAndImplicitlyCastToBoolean(ExprNode node) {
      // In places where the expression is implicitly cast to a boolean, treat
      // a reference to a variable as a comparison of that variable with null.
      // So for example an expression like {if $var} should be treated as
      // {if $var != null} but something like {if $var > 0} should not be changed.
      visit(node);
      ExprEquivalence.Wrapper wrapped = exprEquivalence.wrap(node);
      positiveTypeConstraints.put(wrapped, SoyTypes.tryRemoveNull(node.getType()));
      // TODO(lukes): The 'negative' type constraint here is not optimal.  What we really know is
      // that the value of the expression is 'falsy' we could use that to inform later checks but
      // for now we just assume it has its normal type.
      negativeTypeConstraints.put(wrapped, node.getType());
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      Preconditions.checkArgument(node.numChildren() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = new TypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = new TypeNarrowingConditionVisitor();
      leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

      // Both the left side and right side constraints will be valid if the condition is true.
      computeConstraintUnionInto(
          leftVisitor.positiveTypeConstraints,
          rightVisitor.positiveTypeConstraints,
          /*into=*/ positiveTypeConstraints);
      // If the condition is false, then the overall constraint is the intersection of
      // the complements of the true constraints.
      computeConstraintIntersectionInto(
          leftVisitor.negativeTypeConstraints,
          rightVisitor.negativeTypeConstraints,
          /*into=*/ negativeTypeConstraints);
    }

    @Override
    protected void visitOrOpNode(OrOpNode node) {
      Preconditions.checkArgument(node.numChildren() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = new TypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = new TypeNarrowingConditionVisitor();
      leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

      // If the condition is true, then only constraints that appear on both sides of the
      // operator will be valid.
      computeConstraintIntersectionInto(
          leftVisitor.positiveTypeConstraints,
          rightVisitor.positiveTypeConstraints,
          /*into=*/ positiveTypeConstraints);
      // If the condition is false, then both sides must be false, so the overall constraint
      // is the union of the complements of the constraints on each side.
      computeConstraintUnionInto(
          leftVisitor.negativeTypeConstraints,
          rightVisitor.negativeTypeConstraints,
          /*into=*/ negativeTypeConstraints);
    }

    @Override
    protected void visitNotOpNode(NotOpNode node) {
      // For a logical not node, compute the positive and negative constraints of the
      // operand and then simply swap them.
      TypeNarrowingConditionVisitor childVisitor = new TypeNarrowingConditionVisitor();
      childVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
      positiveTypeConstraints.putAll(childVisitor.negativeTypeConstraints);
      negativeTypeConstraints.putAll(childVisitor.positiveTypeConstraints);
    }

    @Override
    protected void visitEqualOpNode(EqualOpNode node) {
      if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(0));
        positiveTypeConstraints.put(wrappedExpr, NullType.getInstance());
        negativeTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
      } else if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(1));
        positiveTypeConstraints.put(wrappedExpr, NullType.getInstance());
        negativeTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
      }
      // Otherwise don't make any inferences (don't visit children).
    }

    @Override
    protected void visitNotEqualOpNode(NotEqualOpNode node) {
      if (node.getChild(1).getKind() == ExprNode.Kind.NULL_NODE) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(0));
        positiveTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
        negativeTypeConstraints.put(wrappedExpr, NullType.getInstance());
      } else if (node.getChild(0).getKind() == ExprNode.Kind.NULL_NODE) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(1));
        positiveTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
        negativeTypeConstraints.put(wrappedExpr, NullType.getInstance());
      }
      // Otherwise don't make any inferences (don't visit children).
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      // Don't make any inferences (don't visit children).
      // Note: It would be possible to support this case by expanding it into
      // if-statements.
    }

    @Override
    protected void visitConditionalOpNode(ConditionalOpNode node) {
      // Don't make any inferences (don't visit children).
      // Note: It would be possible to support this case by expanding it into
      // if-statements.
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      // Handle 'isNull(<expr>)' and 'isNonnull(<expr>)'.
      if (node.numChildren() != 1) {
        return;
      } else if (node.getFunctionName().equals("isNonnull")) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(0));
        positiveTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
        negativeTypeConstraints.put(wrappedExpr, NullType.getInstance());
      } else if (node.getFunctionName().equals("isNull")) {
        ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node.getChild(0));
        positiveTypeConstraints.put(wrappedExpr, NullType.getInstance());
        negativeTypeConstraints.put(
            wrappedExpr, SoyTypes.tryRemoveNull(wrappedExpr.get().getType()));
      }
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    /**
     * Compute a map which combines the constraints from both the left and right side of an
     * expression. The result should be a set of constraints which satisfy <strong>both</strong>
     * sides.
     *
     * @param left Constraints from the left side.
     * @param right Constraints from the right side.
     */
    private <T> void computeConstraintUnionInto(
        Map<T, SoyType> left, Map<T, SoyType> right, Map<T, SoyType> into) {
      if (left.isEmpty()) {
        return;
      }
      if (right.isEmpty()) {
        return;
      }
      into.putAll(left);
      for (Map.Entry<T, SoyType> entry : right.entrySet()) {
        // The union of two constraints is a *stricter* constraint.
        // Thus "((a instanceof any) AND (a instanceof bool)) == (a instanceof bool)"
        // For now, it's sufficient that the map contains an entry for the variable
        // (since we're only testing for nullability). Once we add support for more
        // complex type tests, we'll need to add code here that combines the two
        // constraints.
        into.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    /**
     * Compute a map which combines the constraints from both the left and right side of an
     * expression. The result should be a set of constraints which satisfy <strong>either</strong>
     * sides.
     *
     * @param left Constraints from the left side.
     * @param right Constraints from the right side.
     */
    private <T> void computeConstraintIntersectionInto(
        Map<T, SoyType> left, Map<T, SoyType> right, Map<T, SoyType> into) {
      if (left.isEmpty()) {
        return;
      }
      if (right.isEmpty()) {
        return;
      }
      for (Map.Entry<T, SoyType> entry : left.entrySet()) {
        // A variable must be present in both the left and right sides in order to be
        // included in the output.
        SoyType rightSideType = right.get(entry.getKey());
        if (rightSideType != null) {
          // The intersection of two constraints is a *looser* constraint.
          // Thus "((a instanceof any) OR (a instanceof bool)) == (a instanceof any)"
          into.put(
              entry.getKey(),
              SoyTypes.computeLowestCommonType(typeRegistry, entry.getValue(), rightSideType));
        }
      }
    }
  }

  /** Whether or not we allow unknown values to be accepted implicitly. */
  private enum UnknownPolicy {
    ALLOWED,
    DISALLOWED;
  }

  /**
   * Class that is used to temporarily substitute the type of a variable.
   *
   * <p>Type substitution preferences are implemented via a custom stack in order for new
   * substitutions to override old ones. This means that lookups for type substitutions are linear
   * in the number of active substitutions. This should be fine because the stack depth is unlikely
   * to be >10. If we end up observing large stacks (100s of active substitutions), then we should
   * rewrite to a hashed data structure to make it faster to do negative lookups.
   */
  private static final class TypeSubstitution {
    /** Parent substitution. */
    @Nullable final TypeSubstitution parent;

    /** The expression whose type we are overriding. */
    final ExprNode expression;

    /** The new type of the variable. */
    final SoyType type;

    TypeSubstitution(@Nullable TypeSubstitution parent, ExprNode expression, SoyType type) {
      this.parent = parent;
      this.expression = expression;
      this.type = type;
    }
  }

  private static final class CompositeMethodRegistry implements SoyMethod.Registry {
    private final List<SoyMethod.Registry> registries;

    public CompositeMethodRegistry(List<SoyMethod.Registry> registries) {
      this.registries = registries;
    }

    @Override
    public ImmutableList<? extends SoyMethod> matchForNameAndBase(
        String methodName, SoyType baseType) {
      return registries.stream()
          .flatMap(r -> r.matchForNameAndBase(methodName, baseType).stream())
          .collect(toImmutableList());
    }

    @Override
    public ImmutableMultimap<SoyMethod, String> matchForBaseAndArgs(
        SoyType baseType, List<SoyType> argTypes) {
      ImmutableListMultimap.Builder<SoyMethod, String> combined = ImmutableListMultimap.builder();
      registries.forEach(r -> combined.putAll(r.matchForBaseAndArgs(baseType, argTypes)));
      return combined.build();
    }
  }

  private final class PluginMethodRegistry implements SoyMethod.Registry {

    private final PluginResolver plugins;
    private final LoadingCache<String, ImmutableList<SoySourceFunctionMethod>> methodCache =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, ImmutableList<SoySourceFunctionMethod>>() {
                  @Override
                  public ImmutableList<SoySourceFunctionMethod> load(String methodName) {
                    ImmutableList.Builder<SoySourceFunctionMethod> methods =
                        ImmutableList.builder();
                    List<SoySourceFunction> functions = plugins.lookupSoyMethods(methodName);
                    for (SoySourceFunction function : functions) {
                      SoyMethodSignature methodSig =
                          function.getClass().getAnnotation(SoyMethodSignature.class);
                      SoyType baseType = parseType(methodSig.baseType(), function);

                      for (Signature signature : methodSig.value()) {
                        SoyType returnType = parseType(signature.returnType(), function);
                        ImmutableList<SoyType> argTypes =
                            Arrays.stream(signature.parameterTypes())
                                .map(s -> parseType(s, function))
                                .collect(toImmutableList());

                        methods.add(
                            new SoySourceFunctionMethod(
                                function, baseType, returnType, argTypes, methodSig.name()));
                      }
                    }
                    return methods.build();
                  }

                  private SoyType parseType(String t, SoySourceFunction fct) {
                    TypeNode typeNode =
                        SoyFileParser.parseType(t, fct.getClass().getName(), errorReporter);
                    return typeNode != null
                        ? pluginTypeConverter.getOrCreateType(typeNode)
                        : ErrorType.getInstance();
                  }
                });

    PluginMethodRegistry(PluginResolver plugins) {
      this.plugins = plugins;
    }

    @Override
    public ImmutableList<SoySourceFunctionMethod> matchForNameAndBase(
        String methodName, SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return methodCache.getUnchecked(methodName).stream()
          .filter(m -> m.appliesToBase(baseType))
          .collect(toImmutableList());
    }

    @Override
    public ImmutableMultimap<SoyMethod, String> matchForBaseAndArgs(
        SoyType baseType, List<SoyType> argTypes) {
      ImmutableListMultimap.Builder<SoyMethod, String> builder = ImmutableListMultimap.builder();
      plugins
          .getAllMethodNames()
          .forEach(
              methodName -> {
                for (SoySourceFunctionMethod m : methodCache.getUnchecked(methodName)) {
                  if (m.appliesToBase(baseType) && m.getNumArgs() == argTypes.size()) {
                    builder.put(m, methodName);
                  }
                }
              });
      return builder.build();
    }
  }
}
