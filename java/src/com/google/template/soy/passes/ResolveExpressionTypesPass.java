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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.template.soy.passes.CheckTemplateCallsPass.ARGUMENT_TYPE_MISMATCH;
import static com.google.template.soy.types.SoyTypes.SAFE_PROTO_TO_SANITIZED_TYPE;
import static com.google.template.soy.types.SoyTypes.getMapKeysType;
import static com.google.template.soy.types.SoyTypes.getMapValuesType;
import static com.google.template.soy.types.SoyTypes.isNullOrUndefined;
import static com.google.template.soy.types.SoyTypes.isNullish;
import static com.google.template.soy.types.SoyTypes.tryRemoveNull;
import static com.google.template.soy.types.SoyTypes.tryRemoveNullish;
import static com.google.template.soy.types.SoyTypes.tryRemoveUndefined;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basicfunctions.ConcatListsFunction;
import com.google.template.soy.basicfunctions.ConcatMapsMethod;
import com.google.template.soy.basicfunctions.KeysFunction;
import com.google.template.soy.basicfunctions.LegacyObjectMapToMapFunction;
import com.google.template.soy.basicfunctions.ListFlatMethod;
import com.google.template.soy.basicfunctions.ListReverseMethod;
import com.google.template.soy.basicfunctions.ListSliceMethod;
import com.google.template.soy.basicfunctions.ListUniqMethod;
import com.google.template.soy.basicfunctions.MapEntriesMethod;
import com.google.template.soy.basicfunctions.MapKeysFunction;
import com.google.template.soy.basicfunctions.MapToLegacyObjectMapFunction;
import com.google.template.soy.basicfunctions.MapValuesMethod;
import com.google.template.soy.basicfunctions.MaxFunction;
import com.google.template.soy.basicfunctions.MinFunction;
import com.google.template.soy.basicfunctions.NumberListSortMethod;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.AbstractParentExprNode;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.FunctionNode.ExternRef;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseAndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseOrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseXorOpNode;
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
import com.google.template.soy.exprtree.OperatorNodes.ShiftLeftOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftRightOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.util.TopoSort;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.internal.ResolvedSignature;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFieldSignature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.FileMetadata.Constant;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AbstractMapType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.FunctionType.Parameter;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.ProtoImportType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateModuleImportType;
import com.google.template.soy.types.TemplateType;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Visitor which resolves all expression types. */
final class ResolveExpressionTypesPass implements CompilerFileSetPass.TopologicallyOrdered {
  // Constant type resolution requires topological ordering of inputs.

  // Keep in alphabetical order.
  private static final SoyErrorKind BAD_FOREACH_TYPE =
      SoyErrorKind.of("Cannot iterate over {0} of type {1}.");
  private static final SoyErrorKind BAD_INDEX_TYPE = SoyErrorKind.of("Bad index type {0} for {1}.");
  private static final SoyErrorKind BAD_KEY_TYPE = SoyErrorKind.of("Bad key type {0} for {1}.");
  private static final SoyErrorKind BAD_LIST_COMP_TYPE =
      SoyErrorKind.of("Bad list comprehension type. {0} has type: {1}, but should be a list.");
  private static final SoyErrorKind BAD_MAP_LITERAL_FROM_LIST_TYPE =
      SoyErrorKind.of(
          "Bad list to map constructor. {0} has type: {1}, but should be a list of records with 2"
              + " fields named key and value.");

  private static final SoyErrorKind BRACKET_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Type {0} does not support bracket access.");
  private static final SoyErrorKind BRACKET_ACCESS_NULLABLE_UNION =
      SoyErrorKind.of(
          "Union type that is nullable cannot use bracket access. To access this value, "
              + "first check for null or use null-safe (\"?[\") operations.");
  private static final SoyErrorKind CHECK_NOT_NULL_ON_COMPILE_TIME_NULL =
      SoyErrorKind.of("Cannot {0} on a value with a static type of ''null'' or ''undefined''.");
  private static final SoyErrorKind REDUNDANT_NON_NULL_ASSERTION_OPERATOR =
      SoyErrorKind.of("Found redundant non-null assertion operators (''!'').");
  private static final SoyErrorKind NULLISH_FIELD_ACCESS =
      SoyErrorKind.of("Field access not allowed on nullable type.");
  private static final SoyErrorKind NO_SUCH_FIELD =
      SoyErrorKind.of(
          "Field ''{0}'' does not exist on type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DOT_ACCESS_NOT_SUPPORTED_CONSIDER_RECORD =
      SoyErrorKind.of("Type {0} does not support dot access (consider record instead of map).");
  private static final SoyErrorKind NO_SUCH_EXTERN_OVERLOAD_1 =
      SoyErrorKind.of("Parameter types, {0}, do not satisfy the function signature, {1}.");
  private static final SoyErrorKind NO_SUCH_EXTERN_OVERLOAD_N =
      SoyErrorKind.of(
          "Parameter types, {0}, do not uniquely satisfy one of the function signatures [{1}].");
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
  private static final SoyErrorKind EMPTY_LIST_COMPREHENSION =
      SoyErrorKind.of("Cannot use list comprehension over empty list.");
  private static final SoyErrorKind EMPTY_MAP_ACCESS =
      SoyErrorKind.of("Accessing item in empty map.");
  private static final SoyErrorKind INVALID_TYPE_SUBSTITUTION =
      SoyErrorKind.of("Cannot narrow expression of type ''{0}'' to ''{1}''.");
  private static final SoyErrorKind MISSING_SOY_TYPE =
      SoyErrorKind.of("Missing Soy type for node {0}.");
  private static final SoyErrorKind NOT_PROTO_INIT =
      SoyErrorKind.of("Expected a protocol buffer for the second argument.");
  private static final SoyErrorKind OR_OPERATOR_HAS_CONSTANT_OPERAND =
      SoyErrorKind.of(
          "Constant operand ''{0}'' used with ''or'' operator. "
              + "Consider simplifying or using the ?? operator, see "
              + "go/soy/reference/expressions.md#logical-operators",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNDEFINED_FIELD_FOR_RECORD_TYPE =
      SoyErrorKind.of(
          "Undefined field ''{0}'' for record type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind PROTO_FIELD_DOES_NOT_EXIST =
      SoyErrorKind.of(
          "Proto field ''{0}'' does not exist in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DID_YOU_MEAN_GETTER_ONLY_FIELD_FOR_PROTO_TYPE =
      SoyErrorKind.of(
          "Did you mean ''{0}''? Proto field ''{0}'' for proto type {1} can only be accessed "
              + "via ''{2}''. See http://go/soy/dev/protos.md#accessing-proto-fields for more "
              + "info.",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind GETTER_ONLY_FIELD_FOR_PROTO_TYPE =
      SoyErrorKind.of(
          "Proto field ''{0}'' for proto type {1} can only be accessed via ''{2}''. "
              + "See http://go/soy/dev/protos.md#accessing-proto-fields for more info.",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind PROTO_MISSING_REQUIRED_FIELD =
      SoyErrorKind.of("Missing required proto field ''{0}''.");
  private static final SoyErrorKind PROTO_NULL_ARG_TYPE =
      SoyErrorKind.of(
          "Cannot assign static type ''null'' or ''undefined'' to proto field ''{0}''.");
  private static final SoyErrorKind PROTO_FIELD_NAME_IMPORT_CONFLICT =
      SoyErrorKind.of(
          "Imported symbol ''{0}'' conflicts with a field of proto constructor ''{1}''.");
  private static final SoyErrorKind TYPE_MISMATCH =
      SoyErrorKind.of("Soy types ''{0}'' and ''{1}'' are not comparable.");
  private static final SoyErrorKind DECLARED_DEFAULT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "The initializer for ''{0}'' has type ''{1}'' which is not assignable to type ''{2}''.");
  private static final SoyErrorKind PARAM_DEPENDS_ON_PARAM =
      SoyErrorKind.of("Param initializers may not depend on other params.");
  private static final SoyErrorKind PARAM_DEPENDS_ON_FUNCTION =
      SoyErrorKind.of("Only pure functions are allowed in param initializers.");
  private static final SoyErrorKind STATE_CYCLE =
      SoyErrorKind.of("Illegal cycle in state param initializers: {0}.");
  private static final SoyErrorKind INCOMPATIBLE_ARITHMETIC_OP =
      SoyErrorKind.of(
          "Using arithmetic operator ''{0}'' on Soy types ''{1}'' and ''{2}'' is illegal.");
  private static final SoyErrorKind INCOMPATIBLE_ARITHMETIC_OP_UNARY =
      SoyErrorKind.of("Using arithmetic operators on the Soy type ''{0}'' is illegal.");
  private static final SoyErrorKind INCORRECT_ARG_TYPE =
      SoyErrorKind.of("Function ''{0}'' called with incorrect arg type {1} (expected {2}).");
  private static final SoyErrorKind INCORRECT_ARG_STYLE =
      SoyErrorKind.of("Function called with incorrect arg style (positional or named).");
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
      SoyErrorKind.of("Use of the ''null'' or ''undefined'' literal is not allowed.");
  private static final SoyErrorKind AMBIGUOUS_INFERRED_TYPE =
      SoyErrorKind.of(
          "Using {0} in the initializer for a parameter with an inferred type is ambiguous. "
              + "Add an explicit type declaration.");
  private static final SoyErrorKind TEMPLATE_TYPE_PARAMETERS_CANNOT_USE_INFERRED_TYPES =
      SoyErrorKind.of(
          "Template type parameters cannot be inferred. Instead, explicitly declare the type.");
  private static final SoyErrorKind CANNOT_USE_INFERRED_TYPES =
      SoyErrorKind.of("Type cannot be inferred, the param definition requires an explicit type.");
  private static final SoyErrorKind PROTO_EXT_FQN =
      SoyErrorKind.of(
          "Extensions fields in proto init functions must be imported symbols. Fully qualified"
              + " names are not allowed.");
  private static final SoyErrorKind NOT_PROTO_MESSAGE =
      SoyErrorKind.of("Only proto messages may be instantiated.");
  private static final SoyErrorKind MUST_USE_TEMPLATES_IMMEDIATELY =
      SoyErrorKind.of(
          "Templates may only be called to initialize a '{'let'}', set a '{'param'}', or as the"
              + " sole child of a print statement.");
  private static final SoyErrorKind CONSTANTS_CANT_BE_NULLABLE =
      SoyErrorKind.of("Type calculated type, {0}, is nullable, which is not allowed for const.");
  private static final SoyErrorKind NOT_ALLOWED_IN_CONSTANT_VALUE =
      SoyErrorKind.of("This operation is not allowed inside a const value definition.");
  private static final SoyErrorKind ILLEGAL_SWITCH_EXPRESSION_TYPE =
      SoyErrorKind.of("Type ''{0}'' is not allowed in a switch expression.");
  private static final SoyErrorKind SWITCH_CASE_TYPE_MISMATCH =
      SoyErrorKind.of("Case type ''{0}'' not assignable to switch type ''{1}''.");
  private static final SoyErrorKind BAD_DELCALL_VARIANT_TYPE =
      SoyErrorKind.of("Delcall variant must be of type string, int, or proto enum. Found ''{0}''.");
  private static final SoyErrorKind INVALID_VARIANT_EXPRESSION =
      SoyErrorKind.of("Invalid variant literal value ''{0}'' in ''delcall''.");
  private static final SoyErrorKind PLURAL_EXPR_TYPE =
      SoyErrorKind.of("Plural expression must be a number type. Found ''{0}''.");
  private static final SoyErrorKind PLURAL_EXPR_NULLABLE =
      SoyErrorKind.of("Plural expression should be a non-nullable number type. Found ''{0}''.");
  private static final SoyErrorKind BAD_CLASS_STRING =
      SoyErrorKind.of(
          "Spaces are not allowed in CSS class names. Either remove the space(s) or pass the"
              + " individual class names to multiple separate calls of the css() function.");
  private static final SoyErrorKind CAN_OMIT_KIND_ONLY_FOR_SINGLE_CALL =
      SoyErrorKind.of(
          "The ''kind'' attribute can be omitted only if the let contains a single "
              + "call command.");

  private final ErrorReporter errorReporter;

  private final SoyMethod.Registry methodRegistry;
  private final boolean rewriteShortFormCalls;
  private final Supplier<FileSetMetadata> templateRegistryFromDeps;

  /** Cached map that converts a string representation of types to actual soy types. */
  private final Map<Signature, ResolvedSignature> signatureMap = new HashMap<>();

  private final ResolveTypesExprVisitor exprVisitor =
      new ResolveTypesExprVisitor(/* inferringParam= */ false);
  private final ResolveTypesExprVisitor paramInfExprVisitor =
      new ResolveTypesExprVisitor(/* inferringParam= */ true);
  private final ResolveTypesExprVisitor constExprVisitor = new ResolveTypesConstNodeVisitor();
  private final FieldRegistry fieldRegistry;

  /** Current set of type substitutions. */
  private TypeSubstitutions substitutions;

  private final ExprEquivalence exprEquivalence;
  private SoyTypeRegistry typeRegistry;
  private TypeNodeConverter pluginTypeConverter;
  private final PluginResolver.Mode pluginResolutionMode;
  private ImmutableMap<String, TemplateType> allTemplateTypes;
  private ConstantsTypeIndex constantsTypeLookup;
  private ExternsTypeIndex externsTypeLookup;
  private SoyFileNode currentFile;

  ResolveExpressionTypesPass(
      ErrorReporter errorReporter,
      PluginResolver pluginResolver,
      boolean rewriteShortFormCalls,
      Supplier<FileSetMetadata> templateRegistryFromDeps) {
    this.errorReporter = errorReporter;
    this.pluginResolutionMode =
        pluginResolver == null
            ? PluginResolver.Mode.REQUIRE_DEFINITIONS
            : pluginResolver.getPluginResolutionMode();
    this.rewriteShortFormCalls = rewriteShortFormCalls;
    this.templateRegistryFromDeps = templateRegistryFromDeps;
    this.methodRegistry =
        new CompositeMethodRegistry(
            ImmutableList.of(BuiltinMethod.REGISTRY, new PluginMethodRegistry(pluginResolver)));
    this.fieldRegistry = new FieldRegistry(pluginResolver);
    this.exprEquivalence = new ExprEquivalence();
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    CollectTemplateTypesVisitor templateTypesVisitor = new CollectTemplateTypesVisitor();
    Map<String, TemplateType> allTemplateTypesBuilder = new HashMap<>();
    this.externsTypeLookup = new ExternsTypeIndex(templateRegistryFromDeps);
    for (SoyFileNode sourceFile : sourceFiles) {
      prepFile(sourceFile);
      // This needs to happen before templateTypesVisitor so it can infer the type of an extern
      // that's the default value of a @state variable.
      setExternTypes(sourceFile);
      allTemplateTypesBuilder.putAll(templateTypesVisitor.exec(sourceFile));
    }
    this.allTemplateTypes = ImmutableMap.copyOf(allTemplateTypesBuilder);
    this.constantsTypeLookup = new ConstantsTypeIndex(templateRegistryFromDeps);

    for (SoyFileNode sourceFile : sourceFiles) {
      prepFile(sourceFile);
      new TypeAssignmentSoyVisitor().exec(sourceFile);
    }
    return Result.CONTINUE;
  }

  private void setExternTypes(SoyFileNode sourceFile) {
    // Process externs defined in this file.
    sourceFile
        .getExterns()
        .forEach(
            extern -> {
              extern.getVar().setType(extern.getType());
              externsTypeLookup.put(extern);
            });
    // Process imported externs.
    sourceFile
        .getImports()
        .forEach(
            importNode ->
                importNode.visitVars(
                    (var, parentType) -> {
                      if (!var.hasType()
                          && parentType != null
                          && parentType.getKind() == Kind.TEMPLATE_MODULE) {
                        TemplateModuleImportType moduleType = (TemplateModuleImportType) parentType;
                        String symbol = var.getSymbol();
                        if (moduleType.getExternNames().contains(symbol)) {
                          List<FunctionType> types =
                              externsTypeLookup.get(moduleType.getPath(), symbol);
                          // The return type is what's important here, and extern overloads are
                          // required to have the same return type, so it's okay to just grab the
                          // first one.
                          var.setType(types.get(0));
                        }
                      }
                    }));
  }

  private void prepFile(SoyFileNode file) {
    substitutions = new TypeSubstitutions(exprEquivalence);
    typeRegistry = file.getSoyTypeRegistry();
    currentFile = file;
    pluginTypeConverter =
        TypeNodeConverter.builder(errorReporter)
            .setTypeRegistry(typeRegistry)
            .setSystemExternal(true)
            .build();
  }

  private TypeNarrowingConditionVisitor createTypeNarrowingConditionVisitor() {
    return new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
  }

  /**
   * A quick first subpass to determine the template type for all templates in every file the file
   * set being compiled.
   */
  private final class CollectTemplateTypesVisitor
      extends AbstractSoyNodeVisitor<Map<String, TemplateType>> {

    private Map<String, TemplateType> types;

    @Override
    public Map<String, TemplateType> exec(SoyNode node) {
      types = new HashMap<>();
      visit(node);
      return types;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      // We only need to visit params for which we will infer the type from the default value. These
      // params have a default value and no type declaration. Because we never infer types from
      // template types this is safe to do without regards to topological ordering of calls.
      node.getHeaderParams().stream()
          .filter(headerVar -> headerVar.defaultValue() != null && headerVar.getTypeNode() == null)
          .forEach(
              headerVar -> {
                paramInfExprVisitor.exec(headerVar.defaultValue());
                headerVar.setType(headerVar.defaultValue().getRoot().getType());
              });

      // These template types only contain the information from passes that run before this. There
      // is currently nothing that guarantees that a subsequent pass doesn't mutate the TemplateNode
      // in such a way as the TemplateType changes.
      types.put(node.getTemplateName(), TemplateMetadata.buildTemplateType(node));
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      node.getTemplates().forEach(this::visit);
    }
  }

  private final class TypeAssignmentSoyVisitor extends AbstractSoyNodeVisitor<Void> {

    @Override
    protected void visitImportNode(ImportNode node) {
      node.visitVars(
          (var, parentType) -> {
            if (!var.hasType()) {
              SoyType newType = UnknownType.getInstance();
              if (parentType != null && parentType.getKind() == Kind.TEMPLATE_MODULE) {
                TemplateModuleImportType moduleType = (TemplateModuleImportType) parentType;
                String symbol = var.getSymbol();
                if (moduleType.getConstantNames().contains(symbol)) {
                  SoyType constantType = constantsTypeLookup.get(moduleType.getPath(), symbol);
                  if (constantType != null) {
                    newType = constantType;
                  }
                }
              }
              var.setType(newType);
            }
          });
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      List<TemplateStateVar> allStateVars = new ArrayList<>();
      SetMultimap<TemplateStateVar, TemplateStateVar> stateToStateDeps = HashMultimap.create();

      // need to visit expressions first so parameters with inferred types have their expressions
      // analyzed
      List<TemplateHeaderVarDefn> headerVars = node.getHeaderParams();
      // If the default value expressions are not constant, they could reference another default
      // value parameter, which won't work because it's looking up the type of the parameter when it
      // hasn't been inferred yet.  So report an error and override the type to be the errortype
      for (TemplateHeaderVarDefn headerVar : headerVars) {
        if (headerVar instanceof TemplateStateVar) {
          allStateVars.add((TemplateStateVar) headerVar);
        }
        // TODO(lukes): there are more nonsensical declarations than just 'null'
        if (headerVar.getTypeNode() != null && headerVar.type().isNullOrUndefined()) {
          errorReporter.report(headerVar.getTypeNode().sourceLocation(), EXPLICIT_NULL);
        }
        if (headerVar.defaultValue() == null) {
          continue;
        }
        for (ExprNode nonConstantChild :
            SoyTreeUtils.getNonConstantChildren(headerVar.defaultValue())) {
          ExprNode.Kind kind = nonConstantChild.getKind();
          switch (kind) {
            case VAR_REF_NODE:
              VarRefNode refNode = (VarRefNode) nonConstantChild;
              if (headerVar instanceof TemplateStateVar) {
                // @state depends on @state
                if (refNode.getDefnDecl() instanceof TemplateStateVar) {
                  stateToStateDeps.put(
                      (TemplateStateVar) headerVar, (TemplateStateVar) refNode.getDefnDecl());
                }
                continue; // @state depends on @param or @state
              } else {
                errorReporter.report(nonConstantChild.getSourceLocation(), PARAM_DEPENDS_ON_PARAM);
              }
              refNode.setSubstituteType(UnknownType.getInstance());
              break;
            case FUNCTION_NODE:
              if (headerVar instanceof TemplateStateVar) {
                continue;
              }
              errorReporter.report(nonConstantChild.getSourceLocation(), PARAM_DEPENDS_ON_FUNCTION);
              break;
            default:
              throw new AssertionError("Unexpected non-constant expression: " + nonConstantChild);
          }
        }
      }

      // Note that cycles are currently impossible because forward references are not allowed.
      TopoSort<TemplateStateVar> topoSort = new TopoSort<>();
      try {
        topoSort.sort(allStateVars, stateToStateDeps::get);
      } catch (NoSuchElementException e) {
        ImmutableList<TemplateStateVar> cycle = topoSort.getCyclicKeys();
        String cycleText = cycle.stream().map(AbstractVarDefn::name).collect(joining(" --> "));
        errorReporter.report(cycle.get(0).getSourceLocation(), STATE_CYCLE, cycleText);
      }

      // Now visit header params for which we have both a default value and a declared type. This
      // is just to check for type conflicts. We can't do this in CollectTemplateTypesVisitor
      // because the default value may in fact be a template literal.
      node.getHeaderParams().stream()
          .filter(headerVar -> headerVar.defaultValue() != null && headerVar.getTypeNode() != null)
          .forEach(
              headerVar -> {
                exprVisitor.exec(headerVar.defaultValue());
                SoyType actualType = headerVar.defaultValue().getRoot().getType();

                SoyType declaredType = headerVar.type();
                if (!declaredType.isAssignableFromStrict(actualType)) {
                  actualType =
                      RuntimeTypeCoercion.maybeCoerceType(
                          headerVar.defaultValue().getRoot(), SoyTypes.expandUnions(declaredType));
                }
                if (!declaredType.isAssignableFromLoose(actualType)) {
                  SourceLocation loc = headerVar.defaultValue().getSourceLocation();
                  if (!loc.isKnown()) {
                    loc = headerVar.getSourceLocation();
                  }
                  errorReporter.report(
                      loc,
                      DECLARED_DEFAULT_TYPE_MISMATCH,
                      headerVar.name(),
                      actualType,
                      declaredType);
                }
              });

      for (ExprRootNode expr : node.getExprList()) {
        if (expr.getType() != null) {
          continue; // must be a default value
        }
        // any other expression in a template declaration
        // currently this is just variant expressions, but might be other things in the future.
        exprVisitor.exec(expr);
      }
      visitChildren(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      allowShortFormCall(node.getExpr());
      visitSoyNode(node);
    }

    @Override
    protected void visitConstNode(ConstNode node) {
      constExprVisitor.exec(node.getExpr());
      SoyType type = node.getExpr().getType();
      if (isNullish(type)) {
        errorReporter.report(node.getSourceLocation(), CONSTANTS_CANT_BE_NULLABLE, type);
      }
      node.getVar().setType(type);
      // Store the type of this constant in the index so that imports of this constant in other
      // files (topologically processed) can have their type set in #visitImportNode.
      constantsTypeLookup.put(node);
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      allowShortFormCall(node.getExpr());
      visitSoyNode(node);
      node.getVar().setType(node.getExpr().getType());
    }

    @Override
    protected void visitCallParamValueNode(CallParamValueNode node) {
      allowShortFormCall(node.getExpr());
      visitSoyNode(node);
    }

    private void allowShortFormCall(ExprRootNode rootNode) {
      if (rootNode.getRoot() instanceof FunctionNode) {
        FunctionNode fnNode = (FunctionNode) rootNode.getRoot();
        if (!fnNode.hasStaticName()
            && (fnNode.getNameExpr().getType() instanceof TemplateImportType
                || fnNode.getNameExpr().getType() instanceof TemplateType)) {
          fnNode.setAllowedToInvokeAsFunction(true);
        }
      }
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitSoyNode(node);
      if (node.isImplicitContentKind()) {
        if (!(node.numChildren() == 1 && node.getChild(0) instanceof CallBasicNode)) {
          if (rewriteShortFormCalls) {
            // Be permissive when running fixer.
            errorReporter.report(node.getSourceLocation(), CAN_OMIT_KIND_ONLY_FOR_SINGLE_CALL);
          }
          // Avoid duplicate errors later.
          node.setContentKind(SanitizedContentKind.HTML);
          return;
        }
        CallBasicNode callNode = (CallBasicNode) node.getChild(0);
        TemplateType templateType = (TemplateType) callNode.getCalleeExpr().getType();
        node.setContentKind(templateType.getContentKind().getSanitizedContentKind());
      }
      node.getVar()
          .setType(
              node.getContentKind() != null
                  ? SanitizedType.getTypeForContentKind(node.getContentKind())
                  : StringType.getInstance());
    }

    @Override
    protected void visitIfNode(IfNode node) {
      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();
      for (SoyNode child : node.getChildren()) {
        if (child instanceof IfCondNode) {
          IfCondNode icn = (IfCondNode) child;
          visitExpressions(icn);

          // Visit the conditional expression to compute which types can be narrowed.
          TypeNarrowingConditionVisitor visitor = createTypeNarrowingConditionVisitor();
          visitor.ifTruthy(icn.getExpr());

          // Save the state of substitutions from the previous if block.
          TypeSubstitutions.Checkpoint previousSubstitutionState = substitutions.checkpoint();

          // Modify the current set of type substitutions for the 'true' branch
          // of the if statement.
          substitutions.addAll(visitor.positiveTypeConstraints);
          visitChildren(icn);

          // Rewind the substitutions back to the state before the if-condition.
          // Add in the negative substitutions, which will affect subsequent blocks
          // of the if statement.
          // So for example if we have a variable whose type is (A|B|C) and the
          // first if-block tests whether that variable is type A, then in the
          // 'else' block it must be of type (B|C); If a subsequent 'elseif'
          // statement tests whether its type B, then in the following else block
          // it can only be of type C.
          substitutions.restore(previousSubstitutionState);
          substitutions.addAll(visitor.negativeTypeConstraints);
        } else if (child instanceof IfElseNode) {
          // For the else node, we simply inherit the previous set of substitutions.
          IfElseNode ien = (IfElseNode) child;
          visitChildren(ien);
        }
      }
      substitutions.restore(savedSubstitutionState);
    }

    private final ImmutableSet<SoyType.Kind> allowedSwitchTypes =
        ImmutableSet.of(
            Kind.BOOL, Kind.INT, Kind.FLOAT, Kind.STRING, Kind.PROTO_ENUM, Kind.UNKNOWN, Kind.ANY);

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitExpressions(node);

      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();
      ExprNode switchExpr = node.getExpr().getRoot();
      SoyType switchExprType = switchExpr.getType();
      boolean exprTypeError = false;
      if (SoyTypes.isNullOrUndefined(switchExprType)
          || !SoyTypes.isKindOrUnionOfKinds(tryRemoveNullish(switchExprType), allowedSwitchTypes)) {
        errorReporter.report(
            switchExpr.getSourceLocation(), ILLEGAL_SWITCH_EXPRESSION_TYPE, switchExprType);
        exprTypeError = true;
      } else if (tryRemoveNullish(switchExprType).getKind() == Kind.PROTO_ENUM) {
        // Allow int cases in proto switch.
        switchExprType = UnionType.of(switchExprType, IntType.getInstance());
      }
      SoyType switchExprNarrowedType = switchExpr.getType();
      for (SoyNode child : node.getChildren()) {
        if (child instanceof SwitchCaseNode) {
          SwitchCaseNode scn = ((SwitchCaseNode) child);
          visitExpressions(scn);

          // Calculate a new type for the switch expression: the union of the types of the case
          // statement.
          List<SoyType> caseTypes = new ArrayList<>();

          for (ExprRootNode expr : scn.getExprList()) {
            SoyType type = expr.getType();
            caseTypes.add(type);
            if (expr.getRoot().getKind() == ExprNode.Kind.NULL_NODE) {
              switchExprNarrowedType = tryRemoveNull(switchExprNarrowedType);
            } else if (expr.getRoot().getKind() == ExprNode.Kind.UNDEFINED_NODE) {
              switchExprNarrowedType = tryRemoveUndefined(switchExprNarrowedType);
            }

            if (!exprTypeError && type.getKind() != Kind.UNKNOWN && !type.isNullOrUndefined()) {
              // Type system has problems with nullability and proto values. So we have to allow
              // "case null" even if we don't think the type is nullable.
              if (!switchExprType.isAssignableFromLoose(type)) {
                errorReporter.report(
                    expr.getSourceLocation(), SWITCH_CASE_TYPE_MISMATCH, type, switchExprType);
              }
            }
          }
          SoyType caseType = typeRegistry.getOrCreateUnionType(caseTypes);

          TypeSubstitutions.Checkpoint previousSubstitutionState = substitutions.checkpoint();

          Map<ExprEquivalence.Wrapper, SoyType> positiveTypeConstraints = new HashMap<>();
          positiveTypeConstraints.put(exprEquivalence.wrap(switchExpr), caseType);
          substitutions.addAll(positiveTypeConstraints);
          visitChildren(scn);

          substitutions.restore(previousSubstitutionState);

          if (!switchExpr.getType().equals(switchExprNarrowedType)) {
            // If a case statement has a null/undefined literal, the switch expression can't be
            // null/undefined for any of the following case statements.
            Map<ExprEquivalence.Wrapper, SoyType> negativeTypeConstraints = new HashMap<>();
            negativeTypeConstraints.put(exprEquivalence.wrap(switchExpr), switchExprNarrowedType);
            substitutions.addAll(negativeTypeConstraints);
          }
        } else if (child instanceof SwitchDefaultNode) {
          // No new type substitutions for a default statement, but inherit the previous (negative)
          // substitutions.
          SwitchDefaultNode sdn = ((SwitchDefaultNode) child);
          visitChildren(sdn);
        }
      }
      substitutions.restore(savedSubstitutionState);
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
    protected void visitMsgPluralNode(MsgPluralNode node) {
      super.visitMsgPluralNode(node);
      SoyType exprType = node.getExpr().getType();
      if (exprType != UnknownType.getInstance() && !SoyTypes.isIntFloatOrNumber(exprType)) {
        SoyType notNullable = tryRemoveNullish(exprType);
        if (!notNullable.equals(exprType) && SoyTypes.isIntFloatOrNumber(notNullable)) {
          errorReporter.warn(node.getExpr().getSourceLocation(), PLURAL_EXPR_NULLABLE, exprType);
        } else {
          errorReporter.report(node.getExpr().getSourceLocation(), PLURAL_EXPR_TYPE, exprType);
        }
      }
    }

    private final ImmutableSet<SoyType.Kind> allowedVariantTypes =
        ImmutableSet.of(
            SoyType.Kind.STRING, SoyType.Kind.INT, SoyType.Kind.PROTO_ENUM, SoyType.Kind.UNKNOWN);

    @Override
    protected void visitCallDelegateNode(CallDelegateNode node) {
      super.visitCallDelegateNode(node);

      ExprRootNode variant = node.getDelCalleeVariantExpr();
      if (variant == null) {
        return;
      }

      SourceLocation location = variant.getSourceLocation();
      SoyType variantType = variant.getType();
      if (SoyTypes.isNullOrUndefined(variantType)
          || !SoyTypes.isKindOrUnionOfKinds(tryRemoveNullish(variantType), allowedVariantTypes)) {
        errorReporter.report(location, BAD_DELCALL_VARIANT_TYPE, variantType);
      }

      // Do some sanity checks on the variant expression.
      if (variant.getRoot().getKind() == ExprNode.Kind.STRING_NODE) {
        // If the variant is a fixed string, it evaluates to an identifier.
        String variantStr = ((StringNode) variant.getRoot()).getValue();
        if (!BaseUtils.isIdentifier(variantStr)) {
          errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variantStr);
        }
      } else if (variant.getRoot().getKind() == ExprNode.Kind.INTEGER_NODE) {
        long variantInt = ((IntegerNode) variant.getRoot()).getValue();
        if (variantInt < 0) {
          errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variant.toSourceString());
        }
      }
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

  private void visitExpressions(ExprHolderNode node) {
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
          return UnknownType.getInstance();
        }
        return ((ListType) collectionType).getElementType();

      case UNION:
        {
          // If it's a union, then do the field type calculation for each member of
          // the union and combine the result.
          ErrorReporter.Checkpoint cp = errorReporter.checkpoint();
          UnionType unionType = (UnionType) collectionType;
          List<SoyType> fieldTypes = new ArrayList<>(unionType.getMembers().size());
          for (SoyType unionMember : unionType.getMembers()) {
            SoyType elementType = getElementType(unionMember, node);
            if (errorReporter.errorsSince(cp)) {
              return elementType;
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
        return UnknownType.getInstance();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions to point to the
   * corresponding declaration object.
   */
  private class ResolveTypesExprVisitor extends AbstractExprNodeVisitor<Void> {
    /**
     * Whether we are currently examining an expression in a default initializer for an inferred
     * template.
     *
     * <p>When inferring types, some values are not legal because they create ambiguity. null and
     * the empty collection literals all have this behavior.
     */
    final boolean inferringParam;

    ResolveTypesExprVisitor(boolean inferringParam) {
      this.inferringParam = inferringParam;
    }

    private final AbstractExprNodeVisitor<Void> checkAllTypesAssignedVisitor =
        new AbstractExprNodeVisitor<>() {
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
      if (type.isNullOrUndefined()) {
        errorReporter.report(
            node.getSourceLocation(),
            CHECK_NOT_NULL_ON_COMPILE_TIME_NULL,
            "use the non-null assertion operator ('!')");
        node.setType(UnknownType.getInstance());
      } else if (node.getChild(0).getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        errorReporter.report(node.getSourceLocation(), REDUNDANT_NON_NULL_ASSERTION_OPERATOR);
        node.setType(UnknownType.getInstance());
      } else {
        node.setType(tryRemoveNullish(type));
      }
    }

    @Override
    protected void visitPrimitiveNode(PrimitiveNode node) {
      // We don't do anything here because primitive nodes already have type information.
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
        if (inferringParam) {
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

      SoyType listExprType = node.getListExpr().getType();

      // Report an error if listExpr did not actually evaluate to a list.
      if (!SoyTypes.isKindOrUnionOfKind(listExprType, SoyType.Kind.LIST)
          && listExprType.getKind() != SoyType.Kind.UNKNOWN) {
        errorReporter.report(
            node.getListExpr().getSourceLocation(),
            BAD_LIST_COMP_TYPE,
            node.getListExpr().toSourceString(),
            listExprType);
        node.getListIterVar().setType(UnknownType.getInstance());
      } else if (listExprType.getKind() == SoyType.Kind.UNKNOWN) {
        node.getListIterVar().setType(UnknownType.getInstance());
      } else {
        SoyType listElementType;
        if (listExprType.getKind() == SoyType.Kind.LIST) {
          listElementType = ((ListType) listExprType).getElementType();
        } else {
          UnionType union = (UnionType) listExprType;
          listElementType =
              UnionType.of(
                  union.getMembers().stream()
                      .map(member -> ((ListType) member).getElementType())
                      .collect(toImmutableList()));
        }

        if (listElementType == null) {
          // Report an error if listExpr was the empty list
          errorReporter.report(node.getListExpr().getSourceLocation(), EMPTY_LIST_COMPREHENSION);
          node.getListIterVar().setType(UnknownType.getInstance());
        } else {
          // Otherwise, use the list element type to set the type of the iterator ($var in this
          // example).
          node.getListIterVar().setType(listElementType);
        }
      }

      if (node.getIndexVar() != null) {
        // Set the type of the optional index to integer ($index in this example).
        node.getIndexVar().setType(IntType.getInstance());
      }

      TypeSubstitutions.Checkpoint savedSubstitutions = substitutions.checkpoint();

      if (node.getFilterExpr() != null) {
        // Visit the optional filter expr, and make sure it evaluates to a boolean.
        visit(node.getFilterExpr());

        // Narrow the type of the list item based on the filter expression.
        TypeNarrowingConditionVisitor typeNarrowing = createTypeNarrowingConditionVisitor();
        typeNarrowing.ifTruthy(node.getFilterExpr());
        substitutions.addAll(typeNarrowing.positiveTypeConstraints);
      }

      // Resolve the type of the itemMapExpr, and use it to determine the comprehension's resulting
      // list type.
      visit(node.getListItemTransformExpr());
      node.setType(typeRegistry.getOrCreateListType(node.getListItemTransformExpr().getType()));

      // Return the type substitutions to their state before narrowing the list item type.
      substitutions.restore(savedSubstitutions);

      tryApplySubstitution(node);
    }

    @Override
    protected void visitRecordLiteralNode(RecordLiteralNode node) {
      visitChildren(node);

      int numChildren = node.numChildren();
      checkState(numChildren == node.getKeys().size());

      List<RecordType.Member> members = new ArrayList<>();
      for (int i = 0; i < numChildren; i++) {
        members.add(
            RecordType.memberOf(node.getKey(i).identifier(), false, node.getChild(i).getType()));
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
        if (inferringParam) {
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

    private boolean isListOfKeyValueRecords(SoyType type) {
      if (!(type instanceof ListType)) {
        return false;
      }
      ListType listType = (ListType) type;

      if (!(listType.getElementType() instanceof RecordType)) {
        return false;
      }
      RecordType recordType = (RecordType) listType.getElementType();

      return ImmutableSet.copyOf(recordType.getMemberNames())
          .equals(MapLiteralFromListNode.MAP_RECORD_FIELDS);
    }

    @Override
    protected void visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      // Resolve the listExpr in "map(listExpr)".
      visit(node.getListExpr());
      if (node.getListExpr().getType().equals(ListType.EMPTY_LIST)) {
        node.setType(MapType.EMPTY_MAP);
        if (inferringParam) {
          errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty map");
        }
        return;
      }

      if (!isListOfKeyValueRecords(node.getListExpr().getType())) {
        errorReporter.report(
            node.getListExpr().getSourceLocation(),
            BAD_MAP_LITERAL_FROM_LIST_TYPE,
            node.getListExpr().toSourceString(),
            node.getListExpr().getType());
        node.setType(MapType.EMPTY_MAP);
        return;
      }

      SoyType keyType =
          ((RecordType) ((ListType) node.getListExpr().getType()).getElementType())
              .getMemberType(MapLiteralFromListNode.KEY_STRING);
      SoyType valueType =
          ((RecordType) ((ListType) node.getListExpr().getType()).getElementType())
              .getMemberType(MapLiteralFromListNode.VALUE_STRING);
      if (!MapType.isAllowedKeyType(keyType)) {
        errorReporter.report(node.getSourceLocation(), MapType.BAD_MAP_KEY_TYPE, keyType);
      }
      // TODO: Catch duplicate keys whenever possible. This is important to support when we make the
      // map from list constructor syntax less clunky (e.g. by supporting tuples, see b/182212609).
      node.setType(typeRegistry.getOrCreateMapType(keyType, valueType));
      tryApplySubstitution(node);
    }

    @Override
    protected void visitVarRefNode(VarRefNode varRef) {
      // Only resolve template types after CollectTemplateTypesVisitor has run. Param type inference
      // should not need this.
      if (allTemplateTypes != null) {
        VarDefn defn = varRef.getDefnDecl();
        if (defn != null && defn.hasType() && defn.type().getKind() == Kind.TEMPLATE_TYPE) {
          TemplateImportType templateType = (TemplateImportType) defn.type();
          if (templateType.getBasicTemplateType() == null) {
            String fqn = templateType.getName();
            TemplateMetadata metadataFromLib =
                templateRegistryFromDeps.get().getBasicTemplateOrElement(fqn);
            if (metadataFromLib != null) {
              // Type is available from deps.
              templateType.setBasicTemplateType(metadataFromLib.getTemplateType());
            } else {
              // Type is available from CollectTemplateTypesVisitor.
              templateType.setBasicTemplateType(allTemplateTypes.get(fqn));
            }
          }
        }
      }

      SoyType newType = substitutions.getTypeSubstitution(varRef);
      if (newType != null) {
        varRef.setSubstituteType(newType);
      } else if (!varRef.hasType()) {
        if (inferringParam) {
          errorReporter.report(varRef.getSourceLocation(), CANNOT_USE_INFERRED_TYPES);
          varRef.setSubstituteType(UnknownType.getInstance());
        } else {
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

      // Rebuild the normalized DataAccessNode chain so that the type substitutions can be applied.
      ExprNode nsBaseExpr = nullSafeAccessNode.asMergedBase();
      SoyType maybeSubstitutedType = substitutions.getTypeSubstitution(nsBaseExpr);
      SoyType baseType =
          maybeSubstitutedType != null
              ? maybeSubstitutedType
              : nullSafeAccessNode.getBase().getType();

      if (nullSafeAccessNode.getDataAccess().getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
        NullSafeAccessNode dataAccess = (NullSafeAccessNode) nullSafeAccessNode.getDataAccess();
        calculateAccessChainTypes(nsBaseExpr, baseType, (DataAccessNode) dataAccess.getBase());
        visitNullSafeAccessNodeRecurse(dataAccess);
      } else if (nullSafeAccessNode.getDataAccess() instanceof AccessChainComponentNode) {
        AccessChainComponentNode dataAccess =
            (AccessChainComponentNode) nullSafeAccessNode.getDataAccess();
        DataAccessNode childDataAccess = getDataAccessChild(dataAccess);
        calculateAccessChainTypes(nsBaseExpr, baseType, childDataAccess);
        finishAssertNonNullOpNodeChain(dataAccess);
      }
      SoyType type = nullSafeAccessNode.getDataAccess().getType();
      if (isNullish(nullSafeAccessNode.getBase().getType())
          && !hasNonNullAssertion(nullSafeAccessNode.getDataAccess())) {
        type = SoyTypes.makeNullable(type);
      }
      nullSafeAccessNode.setType(type);
      tryApplySubstitution(nullSafeAccessNode);
    }

    private ExprNode getTail(ExprNode expr) {
      while (expr instanceof NullSafeAccessNode) {
        expr = ((NullSafeAccessNode) expr).getDataAccess();
      }
      return expr;
    }

    private boolean hasNonNullAssertion(ExprNode expr) {
      return hasNonNullAssertionRecurse(getTail(expr));
    }

    private boolean hasNonNullAssertionRecurse(ExprNode expr) {
      if (expr instanceof AssertNonNullOpNode) {
        return true;
      }
      if (!(expr instanceof DataAccessNode)) {
        return false;
      }
      return hasNonNullAssertionRecurse(((DataAccessNode) expr).getBaseExprChild());
    }

    private DataAccessNode getDataAccessChild(AccessChainComponentNode expr) {
      AccessChainComponentNode child = expr;
      while (child.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        child = (AccessChainComponentNode) child.getChild(0);
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

    /**
     * Calculates the types for each node in an access chain that is a branch of a
     * NullSafeAccessNode. `nsBaseExpr` and `baseType` is the merged base expression and type of the
     * parent NullSafeAccessNode(s) (i.e., the nodes on the other side of the null safe access).
     */
    private void calculateAccessChainTypes(
        ExprNode nsBaseExpr, SoyType baseType, DataAccessNode dataAccess) {
      boolean nullSafe = true;
      if (dataAccess.getBaseExprChild() instanceof DataAccessNode) {
        calculateAccessChainTypes(
            nsBaseExpr, baseType, (DataAccessNode) dataAccess.getBaseExprChild());
        nullSafe = false;

        // Rebuild the normalized DataAccessNode chain by combining this section of the base access
        // with the merged bases of parent NullSafeAccessNodes.
        SoyType maybeSubstitutedType =
            substitutions.getTypeSubstitution(
                NullSafeAccessNode.copyAndGraftPlaceholders(
                    (DataAccessNode) dataAccess.getBaseExprChild(), ImmutableList.of(nsBaseExpr)));
        baseType =
            maybeSubstitutedType != null
                ? maybeSubstitutedType
                : dataAccess.getBaseExprChild().getType();
        ((DataAccessNode) dataAccess.getBaseExprChild()).setType(baseType);

      } else if (dataAccess.getBaseExprChild().getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
        AssertNonNullOpNode baseExpr = (AssertNonNullOpNode) dataAccess.getBaseExprChild();
        DataAccessNode childDataAccess = getDataAccessChild(baseExpr);
        calculateAccessChainTypes(nsBaseExpr, baseType, childDataAccess);
        finishAssertNonNullOpNodeChain(baseExpr);
        nullSafe = false;
        baseType = dataAccess.getBaseExprChild().getType();
      }

      ExprNode base = dataAccess.getBaseExprChild();
      if (NullSafeAccessNode.isPlaceholder(base)) {
        GroupNode node =
            new GroupNode(
                new NullNode(base.getSourceLocation()),
                base.getSourceLocation(),
                /* nullSafePlaceholder= */ true);
        node.setType(baseType);
        base.getParent().replaceChild(base, node);
      }

      switch (dataAccess.getKind()) {
        case FIELD_ACCESS_NODE:
          finishFieldAccessNode((FieldAccessNode) dataAccess, nullSafe);
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
      finishFieldAccessNode(node, /* nullSafe= */ false);
    }

    private void finishFieldAccessNode(FieldAccessNode node, boolean nullSafe) {
      SoyType baseType = node.getBaseExprChild().getType();
      if (nullSafe) {
        baseType = tryRemoveNullish(baseType);
      }

      SoyType nonNullType = tryRemoveNullish(baseType);
      SoySourceFunctionMethod fieldImpl = fieldRegistry.findField(node.getFieldName(), nonNullType);
      if (fieldImpl != null) {
        if (!nonNullType.equals(baseType)) {
          errorReporter.report(node.getAccessSourceLocation(), NULLISH_FIELD_ACCESS);
        }
        node.setType(fieldImpl.getReturnType());
        node.setSoyMethod(fieldImpl);
      } else {
        node.setType(getFieldType(baseType, node.getFieldName(), node.getAccessSourceLocation()));
      }
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
          getItemTypeForAccessNode(
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
      tryApplySubstitution(node);
    }

    private void finishMethodCallNode(MethodCallNode node, boolean nullSafe) {
      for (ExprNode child : node.getParams()) {
        visit(child);
      }

      SoyType baseType = node.getBaseType(nullSafe);
      SoyMethod method = resolveMethodFromBaseType(node, baseType);

      if (method == null) {
        node.setType(UnknownType.getInstance());
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
        } else if (sourceFunction instanceof ConcatMapsMethod) {
          node.setType(getGenericMapType(node.getChildren()));
        } else if (sourceFunction instanceof MapKeysFunction) {
          if (baseType.equals(MapType.EMPTY_MAP)) {
            node.setType(ListType.EMPTY_LIST);
          } else {
            node.setType(ListType.of(getMapKeysType(baseType)));
          }
        } else if (sourceFunction instanceof MapValuesMethod) {
          if (baseType.equals(MapType.EMPTY_MAP)) {
            node.setType(ListType.EMPTY_LIST);
          } else {
            node.setType(ListType.of(getMapValuesType(baseType)));
          }
        } else if (sourceFunction instanceof MapEntriesMethod) {
          if (baseType.equals(MapType.EMPTY_MAP)) {
            node.setType(ListType.EMPTY_LIST);
          } else {
            node.setType(
                ListType.of(
                    RecordType.of(
                        ImmutableList.of(
                            RecordType.memberOf("key", false, getMapKeysType(baseType)),
                            RecordType.memberOf("value", false, getMapValuesType(baseType))))));
          }
        } else if (sourceFunction instanceof ListSliceMethod
            || sourceFunction instanceof ListReverseMethod
            || sourceFunction instanceof ListUniqMethod) {
          // list<T>.slice(...), list<T>.uniq(), and list<T>.reverse() return list<T>
          node.setType(baseType);
        } else if (sourceFunction instanceof NumberListSortMethod) {
          // list<T>.sort() returns list<T>
          // The sort() method only supports lists of number, int, or float.
          node.setType(baseType);
        } else if (sourceFunction instanceof ListFlatMethod) {
          // Determine type for common cases:
          // list<X>.flat() -> list<X> (X not list)
          // list<list<X>>.flat() or list<list<X>>.flat(1) -> list<X>
          // list<list<list<X>>>.flat(2) etc -> list<X>
          int maxDepth;
          if (node.getParams().size() == 1) {
            // This will only work for int literal in the source code.
            if (node.getParams().get(0).getKind() == ExprNode.Kind.INTEGER_NODE) {
              maxDepth = (int) ((IntegerNode) node.getParams().get(0)).getValue();
            } else {
              maxDepth = 0;
            }
          } else {
            maxDepth = 1;
          }

          // Default type if logic below fails.
          node.setType(sourceMethod.getReturnType());
          ListType returnType = (ListType) baseType;
          while (maxDepth-- > 0) {
            if (returnType.getElementType().getKind() == Kind.LIST) {
              returnType = (ListType) returnType.getElementType();
            } else if (returnType.getElementType().getKind() == Kind.UNION) {
              UnionType unionType = (UnionType) returnType.getElementType();
              if (SoyTypes.containsKinds(unionType, ImmutableSet.of(Kind.LIST))) {
                returnType = null;
              }
              break;
            } else {
              break;
            }
          }
          if (returnType != null) {
            node.setType(returnType);
          }
        } else {
          node.setType(sourceMethod.getReturnType());
        }
      } else {
        throw new AssertionError();
      }
    }

    @Nullable
    private SoyMethod resolveMethodFromBaseType(MethodCallNode node, SoyType baseType) {
      if (isNullish(baseType)) {
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

      boolean replaceNode = true;

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
            new LinkedHashSet<>(methodRegistry.matchForBaseAndArgs(baseType, argTypes).values());
        if (!matching.isEmpty()) {
          didYouMean = SoyErrors.getDidYouMeanMessage(matching, methodName);
        }
        // We did not match base type and method name. No method found.
        switch (pluginResolutionMode) {
          case REQUIRE_DEFINITIONS:
            errorReporter.report(srcLoc, INVALID_METHOD_BASE, methodName, baseType, didYouMean);
            break;
          case ALLOW_UNDEFINED_AND_WARN:
            errorReporter.warn(srcLoc, INVALID_METHOD_BASE, methodName, baseType, didYouMean);
            replaceNode = false;
            break;
          default:
            // :( this is for kythe since we can't load plugin definitions since they are too
            // heavyweight.
            replaceNode = false;
        }
      }

      if (replaceNode) {
        GlobalNode.replaceExprWithError(node);
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
        errorReporter.report(
            node.getOperatorLocation(),
            INCOMPATIBLE_ARITHMETIC_OP,
            node.getOperator().getTokenString(),
            left,
            right);
        result = UnknownType.getInstance();
      }
      node.setType(result);
      tryApplySubstitution(node);
    }

    @Override
    protected void visitMinusOpNode(MinusOpNode node) {
      visitArithmeticOpNode(node);
    }

    private void visitLongOnlyOpNode(AbstractOperatorNode node) {
      visitChildren(node);
      SoyType result = IntType.getInstance();
      SoyType left = node.getChild(0).getType();
      SoyType right = node.getChild(1).getType();
      if (left.getKind() != Kind.INT || right.getKind() != Kind.INT) {
        errorReporter.report(
            node.getOperatorLocation(),
            INCOMPATIBLE_ARITHMETIC_OP,
            node.getOperator().getTokenString(),
            left,
            right);
        result = UnknownType.getInstance();
      }
      node.setType(result);
    }

    @Override
    protected void visitShiftLeftOpNode(ShiftLeftOpNode node) {
      visitLongOnlyOpNode(node);
    }

    @Override
    protected void visitShiftRightOpNode(ShiftRightOpNode node) {
      visitLongOnlyOpNode(node);
    }

    @Override
    protected void visitBitwiseOrOpNode(BitwiseOrOpNode node) {
      visitLongOnlyOpNode(node);
    }

    @Override
    protected void visitBitwiseXorOpNode(BitwiseXorOpNode node) {
      visitLongOnlyOpNode(node);
    }

    @Override
    protected void visitBitwiseAndOpNode(BitwiseAndOpNode node) {
      visitLongOnlyOpNode(node);
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
    protected void visitTripleEqualOpNode(TripleEqualOpNode node) {
      visitEqualComparisonOpNode(node);
    }

    @Override
    protected void visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
      visitEqualComparisonOpNode(node);
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      processAnd(node);
      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
      processAnd(node);
      node.setType(UnionType.of(node.getChild(0).getType(), node.getChild(1).getType()));
    }

    private void processAnd(AbstractOperatorNode node) {
      visit(node.getChild(0)); // Assign normal types to left child

      // Save the state of substitutions.
      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();

      // Visit the left hand side to help narrow types used on the right hand side.
      TypeNarrowingConditionVisitor visitor = createTypeNarrowingConditionVisitor();
      visitor.ifTruthy(node.getChild(0));

      // For 'and' the second child only gets evaluated if node 0 is truthy.  So apply the positive
      // assertions.
      substitutions.addAll(visitor.positiveTypeConstraints);
      visit(node.getChild(1));

      // Restore substitutions to previous state
      substitutions.restore(savedSubstitutionState);
    }

    @Override
    protected void visitOrOpNode(OrOpNode node) {
      processOr(node);
      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitBarBarOpNode(BarBarOpNode node) {
      processOr(node);
      node.setType(UnionType.of(node.getChild(0).getType(), node.getChild(1).getType()));
    }

    private void processOr(AbstractOperatorNode node) {
      ExprNode lhs = node.getChild(0);
      if (SoyTreeUtils.isConstantExpr(lhs)) {
        errorReporter.warn(
            node.getOperatorLocation(), OR_OPERATOR_HAS_CONSTANT_OPERAND, lhs.toSourceString());
      }
      visit(lhs); // Assign normal types to left child

      // Save the state of substitutions.
      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();

      // Visit the left hand side to help narrow types used on the right hand side.
      TypeNarrowingConditionVisitor visitor = createTypeNarrowingConditionVisitor();
      visitor.ifTruthy(lhs);

      // For 'or' the second child only gets evaluated if node 0 is falsy.  So apply the negative
      // assertions.
      substitutions.addAll(visitor.negativeTypeConstraints);
      ExprNode rhs = node.getChild(1);
      visit(rhs);
      if (SoyTreeUtils.isConstantExpr(rhs)) {
        errorReporter.warn(
            node.getOperatorLocation(), OR_OPERATOR_HAS_CONSTANT_OPERAND, rhs.toSourceString());
      }

      // Restore substitutions to previous state
      substitutions.restore(savedSubstitutionState);
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      if (node.getOperator() == Operator.LEGACY_NULL_COALESCING) {
        errorReporter.warn(
            node.getOperatorLocation(),
            PluginResolver.DEPRECATED_PLUGIN,
            "The ?: operator",
            "Use ?? instead.");
      }

      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = createTypeNarrowingConditionVisitor();
      visitor.ifNonNullish(node.getChild(0));

      // For the null-coalescing operator, the node 1 only gets evaluated
      // if node 0 is nullish. Use the negative substitutions for this case.
      substitutions.addAll(visitor.negativeTypeConstraints);
      visit(node.getChild(1));

      // Restore substitutions to previous state
      substitutions.restore(savedSubstitutionState);

      // If the LHS is of type attributes and the RHS is empty string, the empty string can be
      // coerced to attributes so the node should have attributes type.
      if (node.getChild(1) instanceof StringNode
          && ((StringNode) node.getChild(1)).getValue().isEmpty()
          && tryRemoveNullish(node.getChild(0).getType()).getKind() == Kind.ATTRIBUTES) {
        node.setType(tryRemoveNullish(node.getChild(0).getType()));
      } else {
        SoyType resultType = node.getChild(1).getType();
        if (!isNullOrUndefined(node.getChild(0).getType())) {
          resultType =
              SoyTypes.computeLowestCommonType(
                  typeRegistry, tryRemoveNullish(node.getChild(0).getType()), resultType);
        }
        node.setType(resultType);
      }
      tryApplySubstitution(node);
    }

    @Override
    protected void visitConditionalOpNode(ConditionalOpNode node) {
      visit(node.getChild(0));

      // Save the state of substitutions.
      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();

      // Visit the conditional expression to compute which types can be narrowed.
      TypeNarrowingConditionVisitor visitor = createTypeNarrowingConditionVisitor();
      visitor.ifTruthy(node.getChild(0));

      // Modify the current set of type substitutions for the 'true' branch
      // of the conditional.
      substitutions.addAll(visitor.positiveTypeConstraints);
      visit(node.getChild(1));

      // Rewind the substitutions back to the state before the expression.
      // Add in the negative substitutions, which will affect the 'false'
      // branch.
      substitutions.restore(savedSubstitutionState);
      substitutions.addAll(visitor.negativeTypeConstraints);
      visit(node.getChild(2));

      // Restore substitutions to previous state
      substitutions.restore(savedSubstitutionState);

      // For a conditional node, it will return either child 1 or 2.
      // If one side is of type attributes and the other is empty string, the empty string can be
      // coerced to attributes so the node should have attributes type.
      if (node.getChild(1) instanceof StringNode
          && ((StringNode) node.getChild(1)).getValue().isEmpty()
          && tryRemoveNullish(node.getChild(2).getType()).getKind() == Kind.ATTRIBUTES) {
        node.setType(node.getChild(2).getType());
      } else if (node.getChild(2) instanceof StringNode
          && ((StringNode) node.getChild(2)).getValue().isEmpty()
          && tryRemoveNullish(node.getChild(1).getType()).getKind() == Kind.ATTRIBUTES) {
        node.setType(node.getChild(1).getType());
      } else {
        node.setType(
            SoyTypes.computeLowestCommonType(
                typeRegistry, node.getChild(1).getType(), node.getChild(2).getType()));
      }
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
    @Nullable
    private ResolvedSignature getOrCreateFunctionSignature(
        Signature signature, String className, ErrorReporter errorReporter) {
      ResolvedSignature resolvedSignature = signatureMap.get(signature);
      if (resolvedSignature != null) {
        return resolvedSignature;
      }
      ImmutableList.Builder<SoyType> paramTypes = ImmutableList.builder();
      SourceFilePath classFilePath = SourceFilePath.create(className);
      for (String paramTypeString : signature.parameterTypes()) {
        TypeNode paramType = SoyFileParser.parseType(paramTypeString, classFilePath, errorReporter);
        if (paramType == null) {
          return null;
        }
        paramTypes.add(pluginTypeConverter.getOrCreateType(paramType));
      }
      TypeNode returnType =
          SoyFileParser.parseType(signature.returnType(), classFilePath, errorReporter);
      if (returnType == null) {
        return null;
      }
      resolvedSignature =
          ResolvedSignature.create(
              paramTypes.build(), pluginTypeConverter.getOrCreateType(returnType));
      signatureMap.put(signature, resolvedSignature);
      return resolvedSignature;
    }

    private boolean maybeSetExtern(FunctionNode node, List<ExternRef> externTypes) {
      List<ExternRef> matching =
          externTypes.stream()
              .filter(t -> paramsMatchFunctionType(node.getParams(), t.signature()))
              .collect(Collectors.toList());
      if (matching.size() == 1) {
        ExternRef ref = matching.get(0);
        node.setAllowedParamTypes(
            ref.signature().getParameters().stream().map(Parameter::getType).collect(toList()));
        node.setType(ref.signature().getReturnType());
        node.setSoyFunction(ref);
        return true;
      }
      return false;
    }

    private boolean paramsMatchFunctionType(
        List<ExprNode> providedParams, FunctionType functionType) {
      ImmutableList<Parameter> functParams = functionType.getParameters();
      if (functParams.size() != providedParams.size()) {
        return false;
      }

      for (int i = 0; i < providedParams.size(); ++i) {
        SoyType providedType = providedParams.get(i).getType();
        SoyType paramType = functParams.get(i).getType();
        if (!paramType.isAssignableFromLoose(providedType)
            && providedType != UnknownType.getInstance()) {
          return false;
        }
      }

      return true;
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (!node.hasStaticName()
          && !node.allowedToInvokeAsFunction()
          && (node.getNameExpr().getType() instanceof TemplateImportType
              || node.getNameExpr().getType() instanceof TemplateType)) {
        if (node.getParent() instanceof FunctionNode
            && ((FunctionNode) node.getParent()).allowedToInvokeAsFunction()) {
          // Recursively allow short form calls, e.g. {tmpl1(p: tmpl2())}
          node.setAllowedToInvokeAsFunction(true);
        } else {
          node.setType(UnknownType.getInstance());
          errorReporter.report(node.getSourceLocation(), MUST_USE_TEMPLATES_IMMEDIATELY);
          // Suppress a followup error that this is unknown.
          node.setAllowedToInvokeAsFunction(true);
          visitChildren(node);
          return;
        }
      }
      visitChildren(node);
      if (!node.hasStaticName()) {
        visit(node.getNameExpr());
        if (node.getNameExpr().getType().getKind() == Kind.TEMPLATE_TYPE) {
          node.setType(
              SanitizedType.getTypeForContentKind(
                  ((TemplateImportType) node.getNameExpr().getType())
                      .getBasicTemplateType()
                      .getContentKind()
                      .getSanitizedContentKind()));
          return;
        } else if (node.getNameExpr().getType().getKind() == Kind.TEMPLATE) {
          node.setType(
              SanitizedType.getTypeForContentKind(
                  ((TemplateType) node.getNameExpr().getType())
                      .getContentKind()
                      .getSanitizedContentKind()));
          return;
        } else if (node.getNameExpr().getType().getKind() == Kind.FUNCTION) {
          if (node.getParamsStyle() == ParamsStyle.NAMED) {
            errorReporter.report(node.getFunctionNameLocation(), INCORRECT_ARG_STYLE);
            node.setSoyFunction(FunctionNode.UNRESOLVED);
          } else {
            String functionName = ((VarRefNode) node.getNameExpr()).getName();
            SourceFilePath filePath = currentFile.getFilePath();
            VarDefn defn = ((VarRefNode) node.getNameExpr()).getDefnDecl();
            if (defn.kind() == VarDefn.Kind.IMPORT_VAR) {
              filePath = ((ImportedVar) defn).getSourceFilePath();
              functionName = ((ImportedVar) defn).getSymbol();
            }
            List<ExternRef> externTypes = externsTypeLookup.getRefs(filePath, functionName);
            if (maybeSetExtern(node, externTypes)) {
              visitInternalExtern(node);
              tryApplySubstitution(node);
              return;
            } else if (!externTypes.isEmpty()) {
              String providedParamTypes =
                  "'"
                      + node.getParams().stream()
                          .map(e -> e.getType().toString())
                          .collect(joining(", "))
                      + "'";
              String allowedTypes =
                  externTypes.stream()
                      .map(
                          t ->
                              t.signature().getParameters().stream()
                                  .map(p -> p.getType().toString())
                                  .collect(joining(", ")))
                      .collect(joining("', '", "'", "'"));

              errorReporter.report(
                  node.getSourceLocation(),
                  externTypes.size() == 1 ? NO_SUCH_EXTERN_OVERLOAD_1 : NO_SUCH_EXTERN_OVERLOAD_N,
                  providedParamTypes,
                  allowedTypes);
              node.setSoyFunction(FunctionNode.UNRESOLVED);
            }
          }
        }
      }
      if (!node.isResolved() || node.getSoyFunction() == FunctionNode.UNRESOLVED) {
        node.setType(UnknownType.getInstance());
        return;
      }
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
      if (node.getParamsStyle() == ParamsStyle.POSITIONAL && node.getAllowedParamTypes() == null) {
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
          if (!signature.deprecatedWarning().isEmpty()) {
            errorReporter.warn(
                node.getFunctionNameLocation(),
                PluginResolver.DEPRECATED_PLUGIN,
                fnSignature.name(),
                signature.deprecatedWarning());
          }
          break;
        }
      }
      if (matchedSignature == null) {
        node.setType(UnknownType.getInstance());
        return;
      }
      if (node.getParamsStyle() == ParamsStyle.NAMED) {
        errorReporter.report(node.getFunctionNameLocation(), INCORRECT_ARG_STYLE);
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
          listArg = UnknownType.getInstance();
        } else {
          listArg = UnknownType.getInstance();
        }
        listType = typeRegistry.getOrCreateListType(listArg);
      }
      node.setType(listType);
    }

    private void visitLegacyObjectMapToMapFunction(FunctionNode node) {
      SoyType argType = node.getChild(0).getType();
      if (argType.equals(LegacyObjectMapType.EMPTY_MAP)) {
        node.setType(MapType.EMPTY_MAP);
      } else if (argType == UnknownType.getInstance()) {
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

    private void visitProtoInitFunction(FunctionNode node) {
      SoyType soyType = node.getNameExpr().getType();
      if (soyType.getKind() != Kind.PROTO_TYPE) {
        errorReporter.report(node.getNameExpr().getSourceLocation(), NOT_PROTO_MESSAGE);
        node.setType(UnknownType.getInstance());
        return;
      }
      ProtoImportType type = (ProtoImportType) node.getNameExpr().getType();
      String protoFqn = type.toString();
      if (SAFE_PROTO_TO_SANITIZED_TYPE.containsKey(protoFqn)) {
        errorReporter.report(
            node.getSourceLocation(),
            TypeNodeConverter.SAFE_PROTO_TYPE,
            SAFE_PROTO_TO_SANITIZED_TYPE.get(protoFqn),
            protoFqn);
        node.setType(UnknownType.getInstance());
        return;
      }

      SoyProtoType protoType =
          (SoyProtoType) typeRegistry.getProtoRegistry().getProtoType(protoFqn);
      node.setType(protoType);

      if (node.getParamsStyle() == ParamsStyle.POSITIONAL) {
        errorReporter.report(node.getSourceLocation(), INCORRECT_ARG_STYLE);
        return;
      }

      // TODO(user): Consider writing a soyProtoTypeImpl.getRequiredFields()
      Set<String> givenParams = new HashSet<>();
      ImmutableSet<String> fields = protoType.getFieldNames();

      boolean hasAliasedParams = false;
      List<Identifier> resolvedIdentifiers = new ArrayList<>();

      // Resolve aliases for the given field names of the proto.
      Checkpoint checkpoint = errorReporter.checkpoint();
      for (Identifier id : node.getParamNames()) {
        String originalName = id.identifier();
        boolean hasOriginal = fields.contains(originalName);
        boolean hasOriginalExt =
            hasOriginal && protoType.getFieldDescriptor(originalName).isExtension();
        Identifier resolvedName = typeRegistry.resolve(id);

        if (resolvedName == null) {
          if (hasOriginalExt) {
            // FQN extension names are not allowed.
            errorReporter.report(id.location(), PROTO_EXT_FQN);
          }
        } else if (!resolvedName.identifier().equals(originalName)) {
          // Check that the aliased name does not conflict with a field in the proto as we cannot
          // determine whether the intended field to instantiate is the regular field or the
          // aliased value.
          if (hasOriginal && !hasOriginalExt) {
            errorReporter.report(
                id.location(),
                PROTO_FIELD_NAME_IMPORT_CONFLICT,
                originalName,
                protoType.getDescriptor().getName());
            node.setType(UnknownType.getInstance());
            continue;
          }
          hasAliasedParams = true;
          id = resolvedName;
        }
        resolvedIdentifiers.add(id);
        givenParams.add(id.identifier());
      }

      // Replace the proto init node to have a list of the resolved param names.
      if (hasAliasedParams && !errorReporter.errorsSince(checkpoint)) {
        // This transformation means that resolving the type of this node may not be idempotent.
        FunctionNode resolvedNode =
            CallableExprBuilder.builder(node).setParamNames(resolvedIdentifiers).buildFunction();
        resolvedNode.setSoyFunction(node.getSoyFunction());
        resolvedNode.setType(node.getType());
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
        if (argType.isNullOrUndefined()) {
          errorReporter.report(
              expr.getSourceLocation(), PROTO_NULL_ARG_TYPE, fieldName.identifier());
        }

        SoyType fieldType = protoType.getFieldType(fieldName.identifier());

        // Same for List<?>, for repeated fields
        if (fieldType.getKind() == Kind.LIST && argType.getKind() == Kind.LIST) {
          SoyType argElementType = ((ListType) argType).getElementType();
          if (argElementType == null || argElementType.equals(UnknownType.getInstance())) {
            continue;
          }
        }

        SoyType expectedType = SoyTypes.makeNullish(fieldType);
        if (!expectedType.isAssignableFromLoose(argType)) {
          argType =
              RuntimeTypeCoercion.maybeCoerceType(
                  expr,
                  expectedType instanceof UnionType
                      ? ((UnionType) expectedType).getMembers()
                      : ImmutableList.of(expectedType));
        }
        if (!expectedType.isAssignableFromLoose(argType)) {
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
    protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
      // Template literals are not legal as default values without a declared type. This is because
      // we don't have enough information to resolve the type at the time this pass is run. For
      // example, two templates may have each other as default parameters, which would create a
      // circular dependency for the type resolution.
      if (inferringParam) {
        errorReporter.report(
            node.getSourceLocation(), TEMPLATE_TYPE_PARAMETERS_CANNOT_USE_INFERRED_TYPES);
        // Placeholder type to prevent further confusing errors.
        node.setType(UnknownType.getInstance());
        return;
      }

      visitChildren(node);

      SoyType existingType = node.getType();
      if (existingType.getKind() == Kind.TEMPLATE_TYPE) {
        TemplateType basicType = ((TemplateImportType) existingType).getBasicTemplateType();
        node.setType(
            Preconditions.checkNotNull(basicType, "No type for %s", node.getResolvedName()));
      }
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
        errorReporter.report(
            node.getOperatorLocation(),
            INCOMPATIBLE_ARITHMETIC_OP,
            node.getOperator().getTokenString(),
            left,
            right);
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
              return UnknownType.getInstance();
            }
          }

          // TODO(b/291166619): Delete the PROTO block sometime around EOY 2023.
        case PROTO:
          {
            SoyProtoType protoType = (SoyProtoType) baseType;
            SoyType fieldType = protoType.getFieldType(fieldName);
            if (fieldType != null) {
              emitProtoFieldError(
                  protoType, fieldName, sourceLocation, /* isSuggestedFieldName= */ false);
            } else {
              String suggestedName = getSuggestedProtoFieldName(protoType, fieldName);
              if (suggestedName != null) {
                fieldType = protoType.getFieldType(suggestedName);
                emitProtoFieldError(
                    protoType, suggestedName, sourceLocation, /* isSuggestedFieldName= */ true);
              } else {
                // no matches are close enough.
                emitDefaultFieldNotFoundError(baseType, fieldName, sourceLocation);
                fieldType = UnknownType.getInstance();
              }
            }
            return tryRemoveNullish(fieldType);
          }

        case LEGACY_OBJECT_MAP:
          {
            errorReporter.report(
                sourceLocation, DOT_ACCESS_NOT_SUPPORTED_CONSIDER_RECORD, baseType);
            return UnknownType.getInstance();
          }

        case UNION:
          {
            // If it's a union, then do the field type calculation for each member of
            // the union and combine the result.
            ErrorReporter.Checkpoint cp = errorReporter.checkpoint();
            UnionType unionType = (UnionType) baseType;
            List<SoyType> fieldTypes = new ArrayList<>(unionType.getMembers().size());
            for (SoyType unionMember : unionType.getMembers()) {
              // TODO:(b/246982549): Remove this if-statement, as is this means you can freely
              // dereference nullish types without the compiler complaining.
              if (unionMember.isNullOrUndefined()) {
                continue;
              }
              SoyType fieldType = getFieldType(unionMember, fieldName, sourceLocation);
              // If this member's field type resolved to an error, bail out to avoid spamming
              // the user with multiple error messages for the same line.
              if (errorReporter.errorsSince(cp)) {
                return fieldType;
              }
              fieldTypes.add(fieldType);
            }
            return SoyTypes.computeLowestCommonType(typeRegistry, fieldTypes);
          }

        case TEMPLATE_TYPE:
        case TEMPLATE_MODULE:
        case PROTO_TYPE:
        case PROTO_EXTENSION:
          // May not be erased if other errors are present.
          return UnknownType.getInstance();

        default:
          emitDefaultFieldNotFoundError(baseType, fieldName, sourceLocation);
          return UnknownType.getInstance();
      }
    }

    private String getSuggestedProtoFieldName(SoyProtoType protoType, String fieldName) {
      if (protoType.getFieldNames().contains(fieldName + "List")) {
        return fieldName + "List";
      } else if (protoType.getFieldNames().contains(fieldName + "Map")) {
        return fieldName + "Map";
      }
      return SoyErrors.getClosest(protoType.getFieldNames(), fieldName);
    }

    private void emitDefaultFieldNotFoundError(
        SoyType baseType, String fieldName, SourceLocation sourceLocation) {
      ImmutableSet<String> allFields = fieldRegistry.getAllFieldNames(tryRemoveNullish(baseType));
      String didYouMean =
          allFields.isEmpty() ? "" : SoyErrors.getDidYouMeanMessage(allFields, fieldName);
      errorReporter.report(sourceLocation, NO_SUCH_FIELD, fieldName, baseType, didYouMean);
    }

    private void emitProtoFieldError(
        SoyProtoType baseType,
        String fieldName,
        SourceLocation sourceLocation,
        boolean isSuggestedFieldName) {
    }

    /** Given a base type and an item key type, compute the item value type. */
    private SoyType getItemTypeForAccessNode(
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
            return UnknownType.getInstance();
          }

          // For lists, the key type must either be unknown or assignable to integer.
          if (!IntType.getInstance().isAssignableFromLoose(keyType)) {
            errorReporter.report(keyLocation, BAD_INDEX_TYPE, keyType, baseType);
            // fall through and report the element type.  This will allow more later type checks to
            // be evaluated.
          }
          return listType.getElementType();

        case LEGACY_OBJECT_MAP:
          {
            AbstractMapType mapType = (AbstractMapType) baseType;
            if (mapType.equals(LegacyObjectMapType.EMPTY_MAP)
                || mapType.equals(MapType.EMPTY_MAP)) {
              errorReporter.report(baseLocation, EMPTY_MAP_ACCESS);
              return UnknownType.getInstance();
            }

            // For maps, the key type must either be unknown or assignable to the declared key type.
            if (!mapType.getKeyType().isAssignableFromLoose(keyType)) {
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
            ErrorReporter.Checkpoint cp = errorReporter.checkpoint();
            UnionType unionType = (UnionType) baseType;
            List<SoyType> itemTypes = new ArrayList<>(unionType.getMembers().size());
            for (SoyType unionMember : unionType.getMembers()) {
              // Skips null types for now.
              if (unionMember.isNullOrUndefined()) {
                continue;
              }
              SoyType itemType =
                  getItemTypeForAccessNode(
                      unionMember, keyType, isNullSafe, baseLocation, keyLocation);
              // If this member's item type resolved to an error, bail out to avoid spamming
              // the user with multiple error messages for the same line.
              if (errorReporter.errorsSince(cp)) {
                return itemType;
              }
              itemTypes.add(itemType);
            }
            // If this is a nullable union type but the operation is not null-safe, report an error.
            if (isNullish(unionType) && !isNullSafe) {
              errorReporter.report(baseLocation, BRACKET_ACCESS_NULLABLE_UNION);
              return UnknownType.getInstance();
            }
            return SoyTypes.computeLowestCommonType(typeRegistry, itemTypes);
          }

        case ANY:
        case NULL:
        case UNDEFINED:
        case BOOL:
        case INT:
        case FLOAT:
        case STRING:
        case MAP:
        case ELEMENT:
        case HTML:
        case ATTRIBUTES:
        case JS:
        case CSS:
        case URI:
        case TRUSTED_RESOURCE_URI:
        case RECORD:
        case PROTO:
        case PROTO_ENUM:
        case TEMPLATE:
        case VE:
        case VE_DATA:
        case MESSAGE:
        case CSS_TYPE:
        case CSS_MODULE:
        case PROTO_TYPE:
        case PROTO_ENUM_TYPE:
        case PROTO_EXTENSION:
        case PROTO_MODULE:
        case TEMPLATE_TYPE:
        case TEMPLATE_MODULE:
        case FUNCTION:
          errorReporter.report(baseLocation, BRACKET_ACCESS_NOT_SUPPORTED, baseType);
          return UnknownType.getInstance();
      }
      throw new AssertionError("unhandled kind: " + baseType.getKind());
    }

    private void tryApplySubstitution(AbstractParentExprNode parentNode) {
      SoyType newType = substitutions.getTypeSubstitution(parentNode);
      if (newType != null) {
        if (!parentNode.getType().isAssignableFromStrict(newType)) {
          errorReporter.report(
              parentNode.getSourceLocation(),
              INVALID_TYPE_SUBSTITUTION,
              parentNode.getType(),
              newType);
        }
        parentNode.setType(newType);
      }
    }

    /**
     * Private helper that checks types of the arguments and tries to set the return type for some
     * built-in functions.
     */
    private void visitBuiltinFunction(BuiltinFunction builtinFunction, FunctionNode node) {
      switch (builtinFunction) {
        case CHECK_NOT_NULL:
          SoyType type = node.getChild(0).getType();
          if (type.isNullOrUndefined()) {
            errorReporter.report(
                node.getSourceLocation(), CHECK_NOT_NULL_ON_COMPILE_TIME_NULL, "call checkNotNull");
          } else {
            // Same type as its child but with nulls removed
            node.setType(tryRemoveNullish(type));
          }
          break;
        case IS_PRIMARY_MSG_IN_USE:
          // don't bother checking the args, they are only ever set by the MsgIdFunctionPass
          node.setType(BoolType.getInstance());
          break;
        case CSS:
          checkArgIsStringLiteralWithNoSpaces(node, node.numChildren() - 1, builtinFunction);
          node.setType(StringType.getInstance());
          break;
        case SOY_SERVER_KEY:
        case XID:
          // arg validation is already handled by the XidPass
          node.setType(StringType.getInstance());
          break;
        case UNKNOWN_JS_GLOBAL:
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
        case VE_DEF:
          if (node.numChildren() >= 3) {
            node.setType(VeType.of(node.getChild(2).getType().toString()));
          } else {
            node.setType(VeType.NO_DATA);
          }
          break;
        case TO_FLOAT: // is added to the AST after this pass
        case REMAINDER:
          node.setType(IntType.getInstance());
          break;
        case MSG_WITH_ID:
          node.setType(
              RecordType.of(
                  ImmutableList.of(
                      RecordType.memberOf("id", false, StringType.getInstance()),
                      RecordType.memberOf(
                          "msg",
                          false,
                          node.numChildren() > 0
                              ? node.getChild(0).getType()
                              : UnknownType.getInstance()))));
          break;
        case LEGACY_DYNAMIC_TAG:
          node.setType(StringType.getInstance());
          break;
        case PROTO_INIT:
          visitProtoInitFunction(node);
          break;
        case UNDEFINED_TO_NULL:
        case UNDEFINED_TO_NULL_SSR:
          visit(node.getChild(0));
          node.setType(SoyTypes.undefinedToNull(node.getChild(0).getType()));
          break;
        case EMPTY_TO_NULL:
          throw new AssertionError("impossible, this is only used by desuraging passes: " + node);
      }
    }

    /** Private helper that reports an error if the css() argument is not literal or has spaces. */
    private void checkArgIsStringLiteralWithNoSpaces(
        FunctionNode node, int childIndex, BuiltinFunction funcName) {
      StringNode stringNode = checkArgIsStringLiteral(node, childIndex, funcName);
      if (stringNode != null && Pattern.compile("\\s+").matcher(stringNode.getValue()).find()) {
        errorReporter.report(node.getSourceLocation(), BAD_CLASS_STRING);
      }
    }

    /**
     * Private helper that reports an error if the argument is not a string literal. Returns the
     * StringNode if it is a literal, otherwise null.
     */
    @CanIgnoreReturnValue
    @Nullable
    private StringNode checkArgIsStringLiteral(
        FunctionNode node, int childIndex, BuiltinFunction funcName) {
      if (childIndex < 0 || childIndex >= node.numChildren()) {
        return null;
      }

      ExprNode arg = node.getChild(childIndex);
      if (!(arg instanceof StringNode)) {
        errorReporter.report(arg.getSourceLocation(), STRING_LITERAL_REQUIRED, funcName.getName());
        return null;
      }
      return (StringNode) arg;
    }

    private void visitInternalExtern(FunctionNode node) {
      ExternRef externRef = (ExternRef) node.getSoyFunction();
      if (externRef.path().path().endsWith("java/soy/plugins/functions.soy")
          && externRef.name().equals("unpackAny")) {
        ExprNode secondParam = node.getChild(1);
        node.setType(secondParam.getType());
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
      } else if (fn instanceof ConcatListsFunction) {
        node.setType(getGenericListType(node.getChildren()));
      } else if (fn instanceof LoggingFunction) {
        // LoggingFunctions always return string.
        node.setType(StringType.getInstance());
      } else if (fn instanceof MaxFunction || fn instanceof MinFunction) {
        // Merge types of the two arguments.
        if (node.getChildren().size() > 1) {
          node.setType(
              SoyTypes.computeLowestCommonType(
                  typeRegistry, node.getChild(0).getType(), node.getChild(1).getType()));
        }
      } else if (node.getType() == null) {
        // We have no way of knowing the return type of a function.
        // TODO: think about adding function type declarations.
        // TODO(b/70946095): at the very least we could hard code types for standard functions for
        // example, everything in the BasicFunctionsModule.
        node.setType(UnknownType.getInstance());
      }
    }

    private SoyType getGenericListType(Iterable<ExprNode> intersectionOf) {
      ImmutableSet.Builder<SoyType> elementTypesBuilder = ImmutableSet.builder();
      for (ExprNode childNode : intersectionOf) {
        // If one of the types isn't a list, we can't compute the intersection. Return UnknownType
        // and assume the caller is already reporting an error for bad args.
        if (!(childNode.getType() instanceof ListType)) {
          return UnknownType.getInstance();
        }
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

    private SoyType getGenericMapType(Iterable<ExprNode> intersectionOf) {
      ImmutableSet.Builder<SoyType> keyTypesBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<SoyType> valueTypesBuilder = ImmutableSet.builder();
      for (ExprNode childNode : intersectionOf) {
        // If one of the types isn't a list, we can't compute the intersection. Return UnknownType
        // and assume the caller is already reporting an error for bad args.
        if (!(childNode.getType() instanceof MapType)) {
          return UnknownType.getInstance();
        }
        MapType mapType = ((MapType) childNode.getType());
        if (mapType.getKeyType() != null) {
          keyTypesBuilder.add(mapType.getKeyType());
        }
        if (mapType.getValueType() != null) {
          valueTypesBuilder.add(mapType.getValueType());
        }
      }

      ImmutableSet<SoyType> keys = keyTypesBuilder.build();
      ImmutableSet<SoyType> values = valueTypesBuilder.build();
      return MapType.of(
          keys.isEmpty() ? UnknownType.getInstance() : typeRegistry.getOrCreateUnionType(keys),
          values.isEmpty() ? UnknownType.getInstance() : typeRegistry.getOrCreateUnionType(values));
    }

    /** Checks the argument type. Returns false if an incorrect arg type error was reported. */
    private boolean checkArgType(ExprNode arg, SoyType expectedType, FunctionNode node) {
      return checkArgType(arg, expectedType, node, UnknownPolicy.ALLOWED);
    }

    /** Checks the argument type. Returns false if an incorrect arg type error was reported. */
    private boolean checkArgType(
        ExprNode arg, SoyType expectedType, FunctionNode node, UnknownPolicy policy) {
      if (!expectedType.isAssignableFromLoose(arg.getType())
          || (policy == UnknownPolicy.DISALLOWED && arg.getType() == UnknownType.getInstance())) {
        errorReporter.report(
            arg.getSourceLocation(),
            INCORRECT_ARG_TYPE,
            node.getStaticFunctionName(),
            arg.getType(),
            expectedType);
        return false;
      }
      return true;
    }
  }

  /**
   * Disallow many types of expressions in ConstNode value initializers, as a security policy. Some
   * of these things aren't proven to be dangerous and could be allowed in the future if requested.
   */
  private final class ResolveTypesConstNodeVisitor extends ResolveTypesExprVisitor {
    ResolveTypesConstNodeVisitor() {
      super(false);
    }

    private void notAllowed(ExprNode node) {
      errorReporter.report(node.getSourceLocation(), NOT_ALLOWED_IN_CONSTANT_VALUE);
    }

    @Override
    protected void visitFieldAccessNode(FieldAccessNode node) {
      notAllowed(node);
      super.visitFieldAccessNode(node);
    }

    @Override
    protected void visitItemAccessNode(ItemAccessNode node) {
      notAllowed(node);
      super.visitItemAccessNode(node);
    }

    @Override
    protected void visitNullNode(NullNode node) {
      if (node.getParent() instanceof ExprRootNode) {
        notAllowed(node);
      }
      super.visitNullNode(node);
    }

    @Override
    protected void visitUndefinedNode(UndefinedNode node) {
      if (node.getParent() instanceof ExprRootNode) {
        notAllowed(node);
      }
      super.visitUndefinedNode(node);
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (node.isResolved()
          && node.getSoyFunction() != BuiltinFunction.PROTO_INIT
          && node.getSoyFunction() != BuiltinFunction.CSS
          && node.getSoyFunction() != BuiltinFunction.XID
          && node.getSoyFunction() != BuiltinFunction.VE_DEF) {
        notAllowed(node);
      }
      super.visitFunctionNode(node);
    }

    @Override
    protected void visitMethodCallNode(MethodCallNode node) {
      super.visitMethodCallNode(node);
      if (!node.isMethodResolved() || node.getSoyMethod() != BuiltinMethod.BIND) {
        notAllowed(node);
      }
    }

    @Override
    protected void visitAssertNonNullOpNode(AssertNonNullOpNode node) {
      notAllowed(node);
      super.visitAssertNonNullOpNode(node);
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      notAllowed(node);
      super.visitNullCoalescingOpNode(node);
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
      notAllowed(node);
      super.visitNullSafeAccessNode(node);
    }
  }

  /** Whether or not we allow unknown values to be accepted implicitly. */
  private enum UnknownPolicy {
    ALLOWED,
    DISALLOWED
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
                new CacheLoader<>() {
                  @Override
                  public ImmutableList<SoySourceFunctionMethod> load(String methodName) {
                    return plugins.lookupSoyMethods(methodName).stream()
                        .flatMap(
                            function -> {
                              SoyMethodSignature methodSig =
                                  function.getClass().getAnnotation(SoyMethodSignature.class);
                              SourceFilePath fakeFunctionPath =
                                  SourceFilePath.create(function.getClass().getName());
                              SoyType baseType = parseType(methodSig.baseType(), fakeFunctionPath);
                              return Arrays.stream(methodSig.value())
                                  .map(
                                      signature -> {
                                        SoyType returnType =
                                            parseType(signature.returnType(), fakeFunctionPath);
                                        ImmutableList<SoyType> argTypes =
                                            Arrays.stream(signature.parameterTypes())
                                                .map(s -> parseType(s, fakeFunctionPath))
                                                .collect(toImmutableList());

                                        return new SoySourceFunctionMethod(
                                            function,
                                            baseType,
                                            returnType,
                                            argTypes,
                                            methodSig.name());
                                      });
                            })
                        .collect(toImmutableList());
                  }
                });

    PluginMethodRegistry(PluginResolver plugins) {
      this.plugins = plugins;
    }

    @Override
    public ImmutableList<SoySourceFunctionMethod> matchForNameAndBase(
        String methodName, SoyType baseType) {
      Preconditions.checkArgument(!isNullish(baseType));
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

  private SoyType parseType(String t, SourceFilePath path) {
    TypeNode typeNode = SoyFileParser.parseType(t, path, errorReporter);
    return typeNode != null
        ? pluginTypeConverter.getOrCreateType(typeNode)
        : UnknownType.getInstance();
  }

  private final class FieldRegistry {

    private final PluginResolver plugins;
    private final LoadingCache<String, ImmutableList<SoySourceFunctionMethod>> methodCache =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<>() {
                  @Override
                  public ImmutableList<SoySourceFunctionMethod> load(String methodName) {
                    return plugins.lookupSoyFields(methodName).stream()
                        .map(
                            f -> {
                              SoyFieldSignature fieldSig =
                                  f.getClass().getAnnotation(SoyFieldSignature.class);
                              SourceFilePath fakeFunctionPath =
                                  SourceFilePath.create(f.getClass().getName());
                              SoyType baseType = parseType(fieldSig.baseType(), fakeFunctionPath);
                              SoyType returnType =
                                  parseType(fieldSig.returnType(), fakeFunctionPath);
                              return new SoySourceFunctionMethod(
                                  f, baseType, returnType, ImmutableList.of(), fieldSig.name());
                            })
                        .collect(toImmutableList());
                  }
                });

    FieldRegistry(PluginResolver plugins) {
      this.plugins = plugins;
    }

    @Nullable
    public SoySourceFunctionMethod findField(String fieldName, SoyType baseType) {
      Preconditions.checkArgument(baseType.isNullOrUndefined() || !isNullish(baseType));
      return methodCache.getUnchecked(fieldName).stream()
          .filter(method -> method.appliesToBase(baseType))
          .findFirst()
          .orElse(null);
    }

    public ImmutableSet<String> getAllFieldNames(SoyType baseType) {
      return plugins.getAllFieldNames().stream()
          .flatMap(methodName -> methodCache.getUnchecked(methodName).stream())
          .filter(method -> method.appliesToBase(baseType))
          .map(SoySourceFunctionMethod::getMethodName)
          .collect(toImmutableSet());
    }
  }

  private static class ConstantsTypeIndex {
    private final Supplier<FileSetMetadata> deps;
    private final Table<SourceFilePath, String, ConstNode> sources = HashBasedTable.create();

    public ConstantsTypeIndex(Supplier<FileSetMetadata> deps) {
      this.deps = deps;
    }

    @Nullable
    SoyType get(SourceFilePath path, String name) {
      ConstNode fromSources = sources.get(path, name);
      if (fromSources != null) {
        return fromSources.getVar().type();
      }
      FileMetadata fromDeps = deps.get().getFile(path);
      if (fromDeps != null) {
        Constant c = fromDeps.getConstant(name);
        if (c != null) {
          return c.getType();
        }
      }
      return null;
    }

    void put(ConstNode node) {
      SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
      sources.put(file.getFilePath(), node.getVar().name(), node);
    }
  }

  private static class ExternsTypeIndex {
    private final Supplier<FileSetMetadata> deps;
    private final Table<SourceFilePath, String, List<ExternNode>> sources = HashBasedTable.create();

    public ExternsTypeIndex(Supplier<FileSetMetadata> deps) {
      this.deps = deps;
    }

    List<ExternRef> getRefs(SourceFilePath path, String name) {
      return get(path, name).stream().map(type -> ExternRef.of(path, name, type)).collect(toList());
    }

    List<FunctionType> get(SourceFilePath path, String name) {
      List<ExternNode> fromSources = sources.get(path, name);
      if (fromSources != null) {
        return fromSources.stream().map(ExternNode::getType).collect(toList());
      }
      FileMetadata fromDeps = deps.get().getFile(path);
      if (fromDeps != null) {
        List<? extends FileMetadata.Extern> e = fromDeps.getExterns(name);
        return e.stream().map(FileMetadata.Extern::getSignature).collect(toList());
      }
      return ImmutableList.of();
    }

    void put(ExternNode node) {
      SourceFilePath path = node.getNearestAncestor(SoyFileNode.class).getFilePath();
      String name = node.getVar().name();
      List<ExternNode> nodes = sources.get(path, name);
      if (nodes != null) {
        nodes.add(node);
      } else {
        nodes = new ArrayList<>();
        nodes.add(node);
        sources.put(path, name, nodes);
      }
    }
  }
}
