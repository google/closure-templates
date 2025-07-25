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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.template.soy.passes.CheckTemplateCallsPass.ARGUMENT_TYPE_MISMATCH;
import static com.google.template.soy.passes.RuntimeTypeCoercion.maybeCoerceType;
import static com.google.template.soy.types.SoyTypes.SAFE_PROTO_TO_SANITIZED_TYPE;
import static com.google.template.soy.types.SoyTypes.computeLowestCommonType;
import static com.google.template.soy.types.SoyTypes.excludeNull;
import static com.google.template.soy.types.SoyTypes.excludeNullish;
import static com.google.template.soy.types.SoyTypes.excludeUndefined;
import static com.google.template.soy.types.SoyTypes.getMapKeysType;
import static com.google.template.soy.types.SoyTypes.getMapValuesType;
import static com.google.template.soy.types.SoyTypes.isNullOrUndefined;
import static com.google.template.soy.types.SoyTypes.isNullish;
import static com.google.template.soy.types.SoyTypes.tryExcludeNullish;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
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
import com.google.template.soy.basicfunctions.ListIncludesFunction;
import com.google.template.soy.basicfunctions.ListIndexOfFunction;
import com.google.template.soy.basicfunctions.ListReverseMethod;
import com.google.template.soy.basicfunctions.ListSliceMethod;
import com.google.template.soy.basicfunctions.ListUniqMethod;
import com.google.template.soy.basicfunctions.MapEntriesMethod;
import com.google.template.soy.basicfunctions.MapKeysFunction;
import com.google.template.soy.basicfunctions.MapToLegacyObjectMapFunction;
import com.google.template.soy.basicfunctions.MapValuesMethod;
import com.google.template.soy.basicfunctions.MaxFunction;
import com.google.template.soy.basicfunctions.MinFunction;
import com.google.template.soy.basicfunctions.MutableArrayMethods.Pop;
import com.google.template.soy.basicfunctions.MutableArrayMethods.Push;
import com.google.template.soy.basicfunctions.MutableArrayMethods.Shift;
import com.google.template.soy.basicfunctions.MutableArrayMethods.Splice;
import com.google.template.soy.basicfunctions.MutableArrayMethods.Unshift;
import com.google.template.soy.basicfunctions.NumberListSortMethod;
import com.google.template.soy.basicfunctions.SortMethod;
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
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.NumberNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
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
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftLeftOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftRightOpNode;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.TypeLiteralNode;
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
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.EvalNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.Metadata;
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
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.AbstractMapType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.FunctionType.Parameter;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.IterableType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.MutableListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.ProtoImportType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.RecordType.Member;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SetType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UndefinedType;
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
final class ResolveExpressionTypesPass extends AbstractTopologicallyOrderedPass {
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
  private static final SoyErrorKind RECORD_LITERAL_NOT_ALLOWED =
      SoyErrorKind.of(
          "Record literal is not allowed as an argument here. "
              + "Object identity equality is used so this will never match anything in the list.");
  private static final SoyErrorKind BRACKET_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Type {0} does not support bracket access.");
  private static final SoyErrorKind BRACKET_ACCESS_NULLABLE_UNION =
      SoyErrorKind.of(
          "Union type that is nullable cannot use bracket access. To access this value, "
              + "first check for null or use null-safe (\"?[\") operations.");
  private static final SoyErrorKind CHECK_NOT_NULL_ON_COMPILE_TIME_NULL =
      SoyErrorKind.of("Cannot {0} on a value with a static type of ''null'' or ''undefined''.");
  private static final SoyErrorKind NULLISH_FIELD_ACCESS =
      SoyErrorKind.of("Field access not allowed on nullable type.");
  private static final SoyErrorKind NO_SUCH_FIELD =
      SoyErrorKind.of(
          "Field ''{0}'' does not exist on type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind METHOD_REFERENCE =
      SoyErrorKind.of("References to methods are not allowed.", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DOT_ACCESS_NOT_SUPPORTED_CONSIDER_RECORD =
      SoyErrorKind.of("Type {0} does not support dot access (consider record instead of map).");
  private static final SoyErrorKind NO_SUCH_EXTERN_OVERLOAD_1 =
      SoyErrorKind.of("Parameter types, {0}, do not satisfy the function signature, {1}.");
  private static final SoyErrorKind NO_SUCH_EXTERN_OVERLOAD_N =
      SoyErrorKind.of(
          "Parameter types, {0}, do not uniquely satisfy one of the function signatures [{1}].");
  private static final SoyErrorKind INVALID_INSTANCE_OF =
      SoyErrorKind.of("Not a valid instanceof type operand.");
  private static final SoyErrorKind UNNECESSARY_CAST =
      SoyErrorKind.of(
          "This `as` expression is unnecessary, it does not change the type of the expression.");
  private static final SoyErrorKind SUSPECT_CAST =
      SoyErrorKind.of(
          "Conversion of type {0} to {1} may be a mistake. If this is intentional cast to `any`"
              + " first.");
  private static final SoyErrorKind DUPLICATE_KEY_IN_MAP_LITERAL =
      SoyErrorKind.of("Map literals with duplicate keys are not allowed.  Duplicate key: ''{0}''");
  private static final SoyErrorKind KEYS_PASSED_MAP =
      SoyErrorKind.of(
          "Use the ''mapKeys'' function instead of ''keys'' for objects of type ''map''.");
  private static final SoyErrorKind EMPTY_MAP_ACCESS =
      SoyErrorKind.of("Accessing item in empty map.");
  private static final SoyErrorKind INVALID_TYPE_SUBSTITUTION =
      SoyErrorKind.of("Cannot narrow expression of type ''{0}'' to ''{1}''.");
  private static final SoyErrorKind MISSING_SOY_TYPE =
      SoyErrorKind.of("Missing Soy type for node {0}.");
  private static final SoyErrorKind NOT_PROTO_INIT =
      SoyErrorKind.of("Expected a protocol buffer for the second argument.");
  private static final SoyErrorKind UNDEFINED_FIELD_FOR_RECORD_TYPE =
      SoyErrorKind.of(
          "Undefined field ''{0}'' for record type {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind PROTO_FIELD_DOES_NOT_EXIST =
      SoyErrorKind.of(
          "Proto field ''{0}'' does not exist in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
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
  private static final SoyErrorKind CONSTANTS_DECLARED_MISMATCH =
      SoyErrorKind.of("Inferred type, {0}, is not assignable to declared type {1}.");
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
          "The ''kind'' attribute can be omitted only if the let contains only calls with matching "
              + "kind and the control structures if/switch/for.");
  static final SoyErrorKind TEMPLATE_CALL_NULLISH =
      SoyErrorKind.of(
          "Template call expressions must be non-nullish. Try guarding with an '{'if'}' command.");
  private static final SoyErrorKind INVALID_SPREAD_VALUE =
      SoyErrorKind.of("Value of type ''{0}'' may not be spread here.");
  private static final SoyErrorKind INVALID_ASSIGNMENT_TYPES =
      SoyErrorKind.of("Cannot set a variable of type ''{0}'' to a value of type ''{1}''.");
  private static final SoyErrorKind GENERIC_PARAM_NOT_ASSIGNABLE =
      SoyErrorKind.of("Argument of type ''{0}'' is not assignable to type ''{1}''.");

  private final ErrorReporter errorReporter;

  private final SoyMethod.Registry methodRegistry;
  private final boolean rewriteShortFormCalls;

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
  private SoyFileNode currentFile;
  private boolean inAutoExtern;

  ResolveExpressionTypesPass(
      ErrorReporter errorReporter,
      PluginResolver pluginResolver,
      boolean allowMissingSoyDeps,
      boolean rewriteShortFormCalls,
      Supplier<FileSetMetadata> templateRegistryFromDeps) {
    super(templateRegistryFromDeps);
    this.errorReporter = errorReporter;
    this.pluginResolutionMode =
        allowMissingSoyDeps
            ? PluginResolver.Mode.ALLOW_UNDEFINED
            : (pluginResolver == null
                ? PluginResolver.Mode.REQUIRE_DEFINITIONS
                : pluginResolver.getPluginResolutionMode());
    this.rewriteShortFormCalls = rewriteShortFormCalls;
    this.methodRegistry =
        new CompositeMethodRegistry(
            ImmutableList.of(BuiltinMethod.REGISTRY, new PluginMethodRegistry(pluginResolver)));
    this.fieldRegistry = new FieldRegistry(pluginResolver);
    this.exprEquivalence = new ExprEquivalence();
  }

  @Override
  void run(SoyFileNode file, IdGenerator nodeIdGen) {
    prepFile(file);
    new TypeAssignmentSoyVisitor().exec(file);
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

  private final class TypeAssignmentSoyVisitor extends AbstractSoyNodeVisitor<Void> {

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      // Visit in order of what types of nodes can reference other types of nodes.
      // Alternately, we could do a topological traversal to allow more types of references.
      node.getImports().forEach(this::visit);
      node.getTypeDefs().forEach(this::visit);
      node.getExterns().forEach(this::calculateExternType);

      // The following ordering means that we have decided to allow constants to reference templates
      // in the same file, at the cost of having to disallow params from omitting a type and having
      // a default value that's a const in the same file.
      node.getTemplates().forEach(this::calculateTemplateType);
      node.getConstants().forEach(this::visit);

      node.getExterns().forEach(this::visit);
      node.getTemplates().forEach(this::visit);
    }

    @Override
    protected void visitImportNode(ImportNode node) {
      if (node.getImportType() == ImportType.UNKNOWN) {
        node.visitVars(var -> var.setType(UnknownType.getInstance()));
      } else if (node.getImportType() != ImportType.TEMPLATE) {
        return;
      }
      node.visitVars(
          (var) -> {
            if (var.getSymbolKind() == SymbolKind.TEMPLATE) {
              if (var.hasType()
                  && ((TemplateImportType) var.type()).getBasicTemplateType() != null) {
                return;
              }
            } else if (var.hasType()) {
              return;
            }
            TemplateImportProcessor.setSymbolType(var, getFileMetadata(var.getSourceFilePath()));
          });
    }

    private void calculateTemplateType(TemplateNode node) {
      // We only need to visit params for which we will infer the type from the default value. These
      // params have a default value and no type declaration. Because we never infer types from
      // template types this is safe to do without regards to topological ordering of calls.
      node.getHeaderParams().stream()
          .filter(headerVar -> headerVar.hasDefault() && !headerVar.hasType())
          .forEach(
              headerVar -> {
                paramInfExprVisitor.exec(headerVar.defaultValue());
                headerVar.setType(headerVar.defaultValue().getRoot().getType());
              });
      ((TemplateImportType) node.asVarDefn().type())
          .setBasicTemplateType(Metadata.buildTemplateType(node));
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
        if (headerVar.getTypeNode() != null && SoyTypes.isNullOrUndefined(headerVar.type())) {
          errorReporter.report(headerVar.getTypeNode().sourceLocation(), EXPLICIT_NULL);
        }
        if (!headerVar.hasDefault()) {
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
          .filter(headerVar -> headerVar.hasDefault() && headerVar.getTypeNode() != null)
          .forEach(
              headerVar -> {
                exprVisitor.exec(headerVar.defaultValue());
                SoyType actualType = headerVar.defaultValue().getRoot().getType();

                SoyType declaredType = headerVar.authoredType();
                if (!declaredType.isAssignableFromStrict(actualType)) {
                  actualType = maybeCoerceType(headerVar.defaultValue().getRoot(), declaredType);
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
    protected void visitEvalNode(EvalNode node) {
      visitSoyNode(node);
    }

    private void calculateExternType(ExternNode node) {
      node.getVar().setType(node.getType());
    }

    @Override
    protected void visitConstNode(ConstNode node) {
      constExprVisitor.exec(node.getExpr());
      SoyType inferredType = node.getExpr().getType();
      if (isNullish(inferredType)) {
        errorReporter.report(node.getSourceLocation(), CONSTANTS_CANT_BE_NULLABLE, inferredType);
      }
      if (node.getTypeNode() != null) {
        SoyType declaredType = node.getTypeNode().getResolvedType();
        if (!declaredType.isAssignableFromStrict(inferredType)) {
          errorReporter.report(
              node.getExpr().getSourceLocation(),
              CONSTANTS_DECLARED_MISMATCH,
              inferredType,
              declaredType);
        }
        inferredType = declaredType;
      }
      node.getVar().setType(inferredType);
    }

    @Override
    protected void visitAutoImplNode(AutoImplNode node) {
      ResolveExpressionTypesPass.this.inAutoExtern = true;
      super.visitAutoImplNode(node);
      ResolveExpressionTypesPass.this.inAutoExtern = false;
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

    private final ImmutableSet<Kind> templateKinds =
        ImmutableSet.of(Kind.TEMPLATE, Kind.TEMPLATE_TYPE);

    private void allowShortFormCall(ExprRootNode rootNode) {
      if (rootNode.getRoot() instanceof FunctionNode) {
        FunctionNode fnNode = (FunctionNode) rootNode.getRoot();
        if (!fnNode.hasStaticName()) {
          SoyType nameExprType = excludeNullish(fnNode.getNameExpr().getType());
          if (SoyTypes.isKindOrUnionOfKinds(nameExprType, templateKinds)) {
            fnNode.setAllowedToInvokeAsFunction(true);
          }
        }
      }
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitSoyNode(node);
      if (node.isImplicitContentKind()) {
        SanitizedContentKind inferredKind =
            SoyTreeUtils.inferSanitizedContentKindFromChildren(node);
        if (inferredKind == null) {
          if (rewriteShortFormCalls) {
            // Be permissive when running fixer.
            errorReporter.report(node.getSourceLocation(), CAN_OMIT_KIND_ONLY_FOR_SINGLE_CALL);
          }
          // Avoid duplicate errors later.
          node.setContentKind(SanitizedContentKind.HTML);
        } else {
          node.setContentKind(inferredKind);
        }
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

    private final SoyType allowedSwitchTypes =
        UnionType.of(
            BoolType.getInstance(),
            NumberType.getInstance(),
            StringType.getInstance(),
            NullType.getInstance(),
            UndefinedType.getInstance());

    private boolean isAllowedSwitchExprType(SoyType type) {
      return type.isOfKind(Kind.ANY)
          || (!SoyTypes.isNullOrUndefined(type) && allowedSwitchTypes.isAssignableFromLoose(type));
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      visitExpressions(node);

      TypeSubstitutions.Checkpoint savedSubstitutionState = substitutions.checkpoint();
      ExprNode switchExpr = node.getExpr().getRoot();
      SoyType switchExprType = switchExpr.getType();
      boolean exprTypeError = false;
      if (!isAllowedSwitchExprType(switchExprType)) {
        errorReporter.report(
            switchExpr.getSourceLocation(), ILLEGAL_SWITCH_EXPRESSION_TYPE, switchExprType);
        exprTypeError = true;
      } else if (excludeNullish(switchExprType).isOfKind(Kind.PROTO_ENUM)) {
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
              switchExprNarrowedType = excludeNull(switchExprNarrowedType);
            } else if (expr.getRoot().getKind() == ExprNode.Kind.UNDEFINED_NODE) {
              switchExprNarrowedType = excludeUndefined(switchExprNarrowedType);
            }

            if (!exprTypeError
                && !type.isEffectivelyEqual(UnknownType.getInstance())
                && !SoyTypes.isNullOrUndefined(type)) {
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

          if (!switchExpr.getType().isEffectivelyEqual(switchExprNarrowedType)) {
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
        SoyType notNullable = excludeNullish(exprType);
        if (!notNullable.isEffectivelyEqual(exprType) && SoyTypes.isIntFloatOrNumber(notNullable)) {
          errorReporter.warn(node.getExpr().getSourceLocation(), PLURAL_EXPR_NULLABLE, exprType);
        } else {
          errorReporter.report(node.getExpr().getSourceLocation(), PLURAL_EXPR_TYPE, exprType);
        }
      }
    }

    private final SoyType allowedVariantTypes =
        UnionType.of(StringType.getInstance(), IntType.getInstance());

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
          || !allowedVariantTypes.isAssignableFromStrict(excludeNullish(variantType))) {
        errorReporter.report(location, BAD_DELCALL_VARIANT_TYPE, variantType);
      }

      // Do some sanity checks on the variant expression.
      if (variant.getRoot().getKind() == ExprNode.Kind.STRING_NODE) {
        // If the variant is a fixed string, it evaluates to an identifier.
        String variantStr = ((StringNode) variant.getRoot()).getValue();
        if (!BaseUtils.isIdentifier(variantStr)) {
          errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variantStr);
        }
      } else if (variant.getRoot() instanceof NumberNode) {
        long variantInt = ((NumberNode) variant.getRoot()).longValue();
        if (variantInt < 0) {
          errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variant.toSourceString());
        }
      }
    }

    @Override
    protected void visitAssignmentNode(AssignmentNode node) {
      super.visitAssignmentNode(node);
      ExprNode lhs = node.getLhs().getRoot();
      if (lhs instanceof VarRefNode) {
        SoyType to = ((VarRefNode) lhs).getDefnDecl().type();
        SoyType from = maybeCoerceType(node.getRhs().getRoot(), to);
        if (!to.isAssignableFromStrict(from)) {
          errorReporter.report(node.getSourceLocation(), INVALID_ASSIGNMENT_TYPES, to, from);
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
    collectionType = collectionType.getEffectiveType();
    switch (collectionType.getKind()) {
      case UNKNOWN:
        // If we don't know anything about the base type, then make no assumptions
        // about the field type.
        return UnknownType.getInstance();

      case ITERABLE:
      case LIST:
      case SET:
        AbstractIterableType iterableType = (AbstractIterableType) collectionType;
        if (iterableType.isEmpty()) {
          return UnknownType.getInstance();
        }
        return iterableType.getElementType();

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
          return computeLowestCommonType(typeRegistry, fieldTypes);
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
      if (SoyTypes.isNullOrUndefined(type)) {
        errorReporter.report(
            node.getSourceLocation(),
            CHECK_NOT_NULL_ON_COMPILE_TIME_NULL,
            "use the non-null assertion operator ('!')");
        node.setType(UnknownType.getInstance());
      } else {
        node.setType(excludeNullish(type));
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
        if (child.getKind() == ExprNode.Kind.SPREAD_OP_NODE) {
          SoyType spreadType = child.getType();
          if (IterableType.ANY_ITERABLE.isAssignableFromStrict(spreadType)) {
            SoyTypes.flattenUnion(spreadType)
                .map(AbstractIterableType.class::cast)
                .forEach(t -> elementTypes.add(t.getElementType()));
          } else {
            errorReporter.report(child.getSourceLocation(), INVALID_SPREAD_VALUE, spreadType);
          }
        } else {
          elementTypes.add(child.getType());
        }
      }
      // Special case for empty list.
      SoyType listType;
      if (elementTypes.isEmpty()) {
        if (ResolveExpressionTypesPass.this.inAutoExtern) {
          listType = MutableListType.empty();
        } else {
          if (inferringParam) {
            errorReporter.report(
                node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty list");
          }
          listType = ListType.empty();
        }
      } else {
        SoyType elementType = computeLowestCommonType(typeRegistry, elementTypes);
        if (ResolveExpressionTypesPass.this.inAutoExtern) {
          listType = typeRegistry.intern(MutableListType.of(elementType));
        } else {
          listType = typeRegistry.getOrCreateListType(elementType);
        }
      }
      node.setType(listType);
      tryApplySubstitution(node);
    }

    @Override
    protected void visitListComprehensionNode(ListComprehensionNode node) {

      // Resolve the listExpr in "[itemMapExpr for $var, $index in listExpr if filterExpr]".
      visit(node.getListExpr());

      SoyType listExprType = node.getListExpr().getType();

      // Report an error if listExpr did not actually evaluate to a list.
      if (!IterableType.ANY_ITERABLE.isAssignableFromLoose(listExprType)) {
        errorReporter.report(
            node.getListExpr().getSourceLocation(),
            BAD_LIST_COMP_TYPE,
            node.getListExpr().toSourceString(),
            listExprType);
        node.getListIterVar().setType(UnknownType.getInstance());
      } else if (listExprType.isEffectivelyEqual(UnknownType.getInstance())) {
        node.getListIterVar().setType(UnknownType.getInstance());
      } else {
        if (listExprType instanceof AbstractIterableType
            && ((AbstractIterableType) listExprType).isEmpty()) {
          node.getListIterVar().setType(UnknownType.getInstance());
        } else {
          // Otherwise, use the list element type to set the type of the iterator ($var in this
          // example).
          SoyType listElementType = SoyTypes.getIterableElementType(typeRegistry, listExprType);
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

      SoyType elementType = node.getListItemTransformExpr().getType();
      SoyType listType =
          ResolveExpressionTypesPass.this.inAutoExtern
              ? typeRegistry.intern(MutableListType.of(elementType))
              : typeRegistry.getOrCreateListType(elementType);
      node.setType(listType);

      // Return the type substitutions to their state before narrowing the list item type.
      substitutions.restore(savedSubstitutions);

      tryApplySubstitution(node);
    }

    @Override
    protected void visitRecordLiteralNode(RecordLiteralNode node) {
      visitChildren(node);

      int numChildren = node.numChildren();
      checkState(numChildren == node.getKeys().size());

      LinkedHashMap<String, RecordType.Member> members = new LinkedHashMap<>();
      int i = 0;
      for (ExprNode child : node.getChildren()) {
        String key = node.getKey(i).identifier();
        if (child.getKind() == ExprNode.Kind.SPREAD_OP_NODE) {
          SoyType spreadType = child.getType();
          if (spreadType instanceof RecordType) {
            for (Member member : ((RecordType) spreadType).getMembers()) {
              members.put(member.name(), member);
            }
          } else {
            errorReporter.report(child.getSourceLocation(), INVALID_SPREAD_VALUE, spreadType);
          }
        } else {
          members.put(key, RecordType.memberOf(key, false, node.getChild(i).getType()));
        }

        i++;
      }
      node.setType(typeRegistry.getOrCreateRecordType(members.values()));

      tryApplySubstitution(node);
    }

    @Override
    protected void visitMapLiteralNode(MapLiteralNode node) {
      visitChildren(node);

      int numChildren = node.numChildren();
      checkState(numChildren % 2 == 0);
      if (numChildren == 0) {
        node.setType(MapType.empty());
        if (inferringParam) {
          errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty map");
        }
        return;
      }

      Set<String> duplicateKeyErrors = new HashSet<>();
      Map<String, SoyType> recordFieldTypes = new LinkedHashMap<>();
      List<SoyType> keyTypes = new ArrayList<>(numChildren / 2);
      List<SoyType> valueTypes = new ArrayList<>(numChildren / 2);
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
        if (!MapType.isAllowedKeyValueType(key.getType())) {
          errorReporter.report(
              key.getSourceLocation(),
              CheckDeclaredTypesPass.BAD_MAP_OR_SET_KEY_TYPE,
              key.getType());
        }
        valueTypes.add(value.getType());
      }
      SoyType commonKeyType = computeLowestCommonType(typeRegistry, keyTypes);
      SoyType commonValueType = computeLowestCommonType(typeRegistry, valueTypes);
      node.setType(typeRegistry.getOrCreateMapType(commonKeyType, commonValueType));

      tryApplySubstitution(node);
    }

    private boolean isListOfKeyValueRecords(SoyType type) {
      return MapLiteralFromListNode.LIST_TYPE.isAssignableFromStrict(type);
    }

    @Override
    protected void visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      // Resolve the listExpr in "map(listExpr)".
      visit(node.getListExpr());
      SoyType listExprType = node.getListExpr().getType();
      if (listExprType instanceof AbstractIterableType
          && ((AbstractIterableType) listExprType).isEmpty()) {
        node.setType(MapType.empty());
        if (inferringParam) {
          errorReporter.report(node.getSourceLocation(), AMBIGUOUS_INFERRED_TYPE, "an empty map");
        }
        return;
      }

      if (!isListOfKeyValueRecords(listExprType)) {
        errorReporter.report(
            node.getListExpr().getSourceLocation(),
            BAD_MAP_LITERAL_FROM_LIST_TYPE,
            node.getListExpr().toSourceString(),
            listExprType);
        node.setType(MapType.empty());
        return;
      }

      SoyType keyType =
          listExprType
              .asType(ListType.class)
              .getElementType()
              .asType(RecordType.class)
              .getMemberType(MapLiteralFromListNode.KEY_STRING);
      SoyType valueType =
          listExprType
              .asType(ListType.class)
              .getElementType()
              .asType(RecordType.class)
              .getMemberType(MapLiteralFromListNode.VALUE_STRING);
      if (!MapType.isAllowedKeyValueType(keyType)) {
        errorReporter.report(
            node.getSourceLocation(), CheckDeclaredTypesPass.BAD_MAP_OR_SET_KEY_TYPE, keyType);
      }
      // TODO: Catch duplicate keys whenever possible. This is important to support when we make the
      // map from list constructor syntax less clunky (e.g. by supporting tuples, see b/182212609).
      node.setType(typeRegistry.getOrCreateMapType(keyType, valueType));
      tryApplySubstitution(node);
    }

    @Override
    protected void visitVarRefNode(VarRefNode varRef) {
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
    protected void visitAsOpNode(AsOpNode node) {
      visitChildren(node);
      SoyType originalType = node.getChild(0).getType();
      SoyType explicitType = node.getChild(1).getType();
      if (explicitType.isEffectivelyEqual(originalType)) {
        errorReporter.warn(node.getSourceLocation(), UNNECESSARY_CAST);
        node.getParent().replaceChild(node, node.getChild(0));
      } else if (!originalType.isAssignableFromLoose(explicitType)
          && !explicitType.isAssignableFromLoose(originalType)) {
        errorReporter.report(node.getSourceLocation(), SUSPECT_CAST, originalType, explicitType);
      }
      node.setType(explicitType);
    }

    @Override
    protected void visitInstanceOfOpNode(InstanceOfOpNode node) {
      visitChildren(node);
      TypeLiteralNode typeNode = (TypeLiteralNode) node.getChild(1);
      SoyType opType = typeNode.getType();
      if (!SoyTypes.isValidInstanceOfOperand(opType)) {
        // Avoid double error.
        if (!opType.isOfKind(Kind.UNKNOWN)) {
          errorReporter.report(typeNode.getSourceLocation(), INVALID_INSTANCE_OF);
        }
      }
      node.setType(BoolType.getInstance());
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
      visit(nullSafeAccessNode.getBase());
      visitNullSafeAccessNodeRecurse(nullSafeAccessNode);

      // All of the NSAN will have the same type, since they (il)-logically all refer to the same
      // thing. e.g., a?.b?.c?.d will have 3 NSANs, that express a?.b?.c?.d,
      // (placeholder).b?.c?.d, and (placeholder).c?.d. But only the root can be rebuilt to the full
      // access chain to query type substitutions.
      SoyType maybeSubstitutedRootType =
          substitutions.getTypeSubstitution(nullSafeAccessNode.asNormalizedAccessChain());
      if (maybeSubstitutedRootType != null) {
        // Propagate substitution to all NSANs in the chain.
        ExprNode curr = nullSafeAccessNode;
        while (curr instanceof NullSafeAccessNode) {
          ((NullSafeAccessNode) curr).setType(maybeSubstitutedRootType);
          curr = ((NullSafeAccessNode) curr).getDataAccess();
        }
      }
    }

    private void visitNullSafeAccessNodeRecurse(NullSafeAccessNode nullSafeAccessNode) {
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
        type = SoyTypes.unionWithUndefined(type);
      }
      nullSafeAccessNode.setType(type);
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
                new UndefinedNode(base.getSourceLocation()),
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

      // Note that nsBaseExpr is normalized to all non-null safe accesses.
      SoyType maybeSubstitutedType =
          substitutions.getTypeSubstitution(
              NullSafeAccessNode.copyAndGraftPlaceholders(
                  dataAccess, ImmutableList.of(nsBaseExpr)));
      if (maybeSubstitutedType != null) {
        dataAccess.setType(maybeSubstitutedType);
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
        baseType = excludeNullish(baseType);
      }

      SoyType nonNullType = excludeNullish(baseType);
      SoySourceFunctionMethod fieldImpl = fieldRegistry.findField(node.getFieldName(), nonNullType);
      if (fieldImpl != null) {
        if (!nonNullType.isEffectivelyEqual(baseType)) {
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

      SoyType baseType = node.getBaseType(nullSafe).getEffectiveType();
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
          if (baseType instanceof AbstractMapType && ((AbstractMapType) baseType).isEmpty()) {
            node.setType(ListType.empty());
          } else {
            node.setType(ListType.of(getMapKeysType(baseType)));
          }
        } else if (sourceFunction instanceof MapValuesMethod) {
          if (baseType instanceof AbstractMapType && ((AbstractMapType) baseType).isEmpty()) {
            node.setType(ListType.empty());
          } else {
            node.setType(ListType.of(getMapValuesType(baseType)));
          }
        } else if (sourceFunction instanceof MapEntriesMethod) {
          if (baseType instanceof AbstractMapType && ((AbstractMapType) baseType).isEmpty()) {
            node.setType(ListType.empty());
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
            || sourceFunction instanceof ListUniqMethod
            || sourceFunction instanceof NumberListSortMethod
            || sourceFunction instanceof SortMethod) {
          // list<T>.slice(...), list<T>.uniq(), and list<T>.reverse() return list<T>
          node.setType(baseType);
        } else if (sourceFunction instanceof Push || sourceFunction instanceof Unshift) {
          SoyType elementType =
              SoyTypes.getIterableElementType(typeRegistry, node.getBaseType(false));
          for (ExprNode param : node.getParams()) {
            if (!elementType.isAssignableFromStrict(param.getType())) {
              errorReporter.report(
                  param.getSourceLocation(),
                  GENERIC_PARAM_NOT_ASSIGNABLE,
                  param.getType(),
                  elementType);
            }
          }
          node.setType(sourceMethod.getReturnType());
        } else if (sourceFunction instanceof Pop || sourceFunction instanceof Shift) {
          node.setType(SoyTypes.getIterableElementType(typeRegistry, baseType));
        } else if (sourceFunction instanceof Splice) {
          SoyType elementType =
              SoyTypes.getIterableElementType(typeRegistry, node.getBaseType(false));
          for (int i = 2; i < node.getParams().size(); i++) {
            ExprNode param = node.getParam(i);
            if (!elementType.isAssignableFromStrict(param.getType())) {
              errorReporter.report(
                  param.getSourceLocation(),
                  GENERIC_PARAM_NOT_ASSIGNABLE,
                  param.getType(),
                  elementType);
            }
          }
          node.setType(baseType);
        } else if (sourceFunction instanceof ListFlatMethod) {
          // Determine type for common cases:
          // list<X>.flat() -> list<X> (X not list)
          // list<list<X>>.flat() or list<list<X>>.flat(1) -> list<X>
          // list<list<list<X>>>.flat(2) etc -> list<X>
          int maxDepth;
          if (node.numParams() == 1) {
            // This will only work for int literal in the source code.
            if (node.getParam(0) instanceof NumberNode) {
              maxDepth = ((NumberNode) node.getParam(0)).intValue();
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
            if (returnType.getElementType() instanceof ListType) {
              returnType = (ListType) returnType.getElementType();
            } else if (returnType.getElementType() instanceof UnionType) {
              UnionType unionType = (UnionType) returnType.getElementType();
              if (unionType.isAssignableFromStrict(ListType.empty())) {
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
        } else if (sourceFunction instanceof ListIncludesFunction
            || sourceFunction instanceof ListIndexOfFunction) {
          node.setType(sourceMethod.getReturnType());
          if (node.getParam(0) instanceof RecordLiteralNode) {
            errorReporter.report(node.getParam(0).getSourceLocation(), RECORD_LITERAL_NOT_ALLOWED);
          }
          SoyType listElementType = ((ListType) baseType).getElementType();
          if (node.getParam(0).getType() != null) {
            // Remove null from the arg to allow eg list<string>.includes(null|string). We can make
            // it work in TS by adding the `!` operator in Soy->Tsx.
            SoyType argType = tryExcludeNullish(node.getParam(0).getType());
            if (!listElementType.isAssignableFromLoose(argType)
                && !(SoyTypes.isNumericPrimitive(excludeNullish(listElementType))
                    && SoyTypes.isNumericPrimitive(argType))) {
              errorReporter.report(
                  node.getParam(0).getSourceLocation(),
                  INCORRECT_ARG_TYPE,
                  sourceMethod.getMethodName(),
                  argType,
                  listElementType.toString());
            }
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
          matchNameAndType.stream().filter(m -> m.acceptsArgCount(numParams)).collect(toList());

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
          andMatchArgCount.stream()
              .filter(m -> appliesToArgs(m, baseType, argTypes))
              .collect(toImmutableList());

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
        String expected = Joiner.on(", ").join(getParamTypes(andMatchArgCount.get(0), baseType));
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

    private List<SoyType> getParamTypes(SoyMethod method, SoyType baseType) {
      if (method instanceof SoySourceFunctionMethod) {
        SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) method;
        // Hand-coded support for generic param types.
        if (sourceMethod.getImpl() instanceof SortMethod) {
          // Change list<T>.toSorted((?, ?) => int) to list<T>.toSorted((T, T) => int)
          FunctionType arg = (FunctionType) sourceMethod.getParamTypes().get(0);
          SoyType itemType = ((AbstractIterableType) baseType).getElementType();
          arg =
              FunctionType.of(
                  arg.getParameters().stream()
                      .map(p -> FunctionType.Parameter.of(p.getName(), itemType))
                      .collect(toImmutableList()),
                  arg.getReturnType());
          return ImmutableList.of(arg);
        }
        return sourceMethod.getParamTypes();
      }
      return ImmutableList.of();
    }

    private boolean appliesToArgs(SoyMethod method, SoyType baseType, List<SoyType> argTypes) {
      if (method instanceof SoySourceFunctionMethod) {
        List<SoyType> allowedTypes = getParamTypes(method, baseType);
        Preconditions.checkArgument(argTypes.size() == allowedTypes.size());
        for (int i = 0; i < argTypes.size(); i++) {
          if (!allowedTypes.get(i).isAssignableFromStrict(argTypes.get(i))) {
            return false;
          }
        }
        return true;
      } else {
        return true;
      }
    }

    @Override
    protected void visitGlobalNode(GlobalNode node) {
      // Do nothing, global nodes already have type information.
    }

    @Override
    protected void visitNegativeOpNode(NegativeOpNode node) {
      visitChildren(node);
      SoyType childType = SoyTypes.excludeNull(node.getChild(0).getType());
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
      SoyType left = excludeNullish(node.getChild(0).getType());
      SoyType right = excludeNullish(node.getChild(1).getType());
      if ((!left.isOfKind(Kind.INT) && !left.isOfKind(Kind.NUMBER))
          || (!right.isOfKind(Kind.INT) && !right.isOfKind(Kind.NUMBER))) {
        errorReporter.report(
            node.getOperatorLocation(),
            INCOMPATIBLE_ARITHMETIC_OP,
            node.getOperator().getTokenString(),
            node.getChild(0).getType(),
            node.getChild(1).getType());
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
    protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
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

      node.setType(
          computeLowestCommonType(
              typeRegistry, node.getChild(0).getType(), node.getChild(1).getType()));
    }

    @Override
    protected void visitBarBarOpNode(BarBarOpNode node) {
      ExprNode lhs = node.getChild(0);
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

      // Restore substitutions to previous state
      substitutions.restore(savedSubstitutionState);

      setTypeNullCoalesceNodeOrNode(node);
    }

    @Override
    protected void visitSpreadOpNode(SpreadOpNode node) {
      visit(node.getChild(0));
      node.setType(node.getChild(0).getType()); // Must be spread in context to be valid.
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
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
      setTypeNullCoalesceNodeOrNode(node);
      tryApplySubstitution(node);
    }

    private boolean isNullishAttributes(SoyType type) {
      return AttributesType.getInstance().isAssignableFromStrict(excludeNullish(type));
    }

    private void setTypeNullCoalesceNodeOrNode(AbstractOperatorNode node) {
      // If the LHS is of type attributes and the RHS is empty string, the empty string can be
      // coerced to attributes so the node should have attributes type.
      if (node.getChild(1) instanceof StringNode
          && ((StringNode) node.getChild(1)).getValue().isEmpty()
          && isNullishAttributes(node.getChild(0).getType())) {
        node.setType(excludeNullish(node.getChild(0).getType()));
      } else {
        SoyType resultType = node.getChild(1).getType();
        if (!isNullOrUndefined(node.getChild(0).getType())) {
          resultType =
              computeLowestCommonType(
                  typeRegistry, excludeNullish(node.getChild(0).getType()), resultType);
        }
        node.setType(resultType);
      }
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
          && isNullishAttributes(node.getChild(2).getType())) {
        node.setType(node.getChild(2).getType());
      } else if (node.getChild(2) instanceof StringNode
          && ((StringNode) node.getChild(2)).getValue().isEmpty()
          && isNullishAttributes(node.getChild(1).getType())) {
        node.setType(node.getChild(1).getType());
      } else {
        node.setType(
            computeLowestCommonType(
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
      SourceFilePath classFilePath = SourceFilePath.create(className, className);
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

    private boolean maybeSetExtern(FunctionNode node, List<? extends Extern> externTypes) {
      List<ExprNode> params = node.getParams();
      List<Extern> matching =
          externTypes.stream()
              .filter(t -> paramsMatchFunctionType(params, t.getSignature()))
              .collect(Collectors.toList());
      if (matching.size() == 1) {
        Extern ref = matching.get(0);
        for (int i = 0; i < params.size(); i++) {
          // The available runtime coercions are all between assignable types. So there's no need
          // to re-match externs on the coerced types.
          maybeCoerceType(params.get(i), ref.getSignature().getParameters().get(i).getType());
        }
        node.setAllowedParamTypes(
            ref.getSignature().getParameters().stream().map(Parameter::getType).collect(toList()));
        node.setType(ref.getSignature().getReturnType());
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
      SoyType nameExprType =
          node.hasStaticName() ? null : excludeNullish(node.getNameExpr().getType());
      if (!node.hasStaticName()
          && !node.allowedToInvokeAsFunction()
          && (nameExprType instanceof TemplateImportType || nameExprType instanceof TemplateType)) {
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
        if (nameExprType instanceof TemplateImportType) {
          node.setType(
              SanitizedType.getTypeForContentKind(
                  ((TemplateImportType) nameExprType)
                      .getBasicTemplateType()
                      .getContentKind()
                      .getSanitizedContentKind()));
          return;
        } else if (nameExprType instanceof TemplateType) {
          node.setType(
              SanitizedType.getTypeForContentKind(
                  ((TemplateType) nameExprType).getContentKind().getSanitizedContentKind()));
          if (SoyTypes.isNullish(node.getNameExpr().getType())) {
            errorReporter.report(node.getNameExpr().getSourceLocation(), TEMPLATE_CALL_NULLISH);
          }
          return;
        } else if (SoyTypes.isKindOrUnionOfKind(nameExprType, Kind.FUNCTION)) {
          if (node.getParamsStyle() == ParamsStyle.NAMED) {
            errorReporter.report(node.getFunctionNameLocation(), INCORRECT_ARG_STYLE);
            node.setSoyFunction(FunctionNode.UNRESOLVED);
          } else {
            VarDefn defn = ((VarRefNode) node.getNameExpr()).getDefnDecl();
            List<? extends Extern> externTypes;

            if (defn.kind() == VarDefn.Kind.SYMBOL && ((SymbolVar) defn).isImported()) {
              externTypes =
                  getFileMetadata(((SymbolVar) defn).getSourceFilePath())
                      .getExterns(((SymbolVar) defn).getSymbol());
            } else if (defn.kind() == VarDefn.Kind.LOCAL_VAR || defn.kind() == VarDefn.Kind.PARAM) {
              node.setSoyFunction(FunctionNode.FUNCTION_POINTER);
              node.setType(SoyTypes.getFunctionReturnType(nameExprType));
              return;
            } else {
              externTypes =
                  currentFile.getExterns().stream()
                      .filter(
                          e ->
                              e.getVar().name().equals(((VarRefNode) node.getNameExpr()).getName()))
                      .map(Metadata::forAst)
                      .collect(toImmutableList());
            }

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
                              t.getSignature().getParameters().stream()
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
        node.setAllowedParamTypes(Collections.nCopies(node.numParams(), UnknownType.getInstance()));
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
        if (signature.parameterTypes().length == node.numParams()) {
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
      for (int i = 0; i < node.numParams(); ++i) {
        SoyType paramType = matchedSignature.parameterTypes().get(i);
        maybeCoerceType(node.getParam(i), paramType);
        checkArgType(node.getParam(i), paramType, node);
      }
      node.setAllowedParamTypes(matchedSignature.parameterTypes());
      node.setType(matchedSignature.returnType());
    }

    private void visitKeysFunction(FunctionNode node) {
      ListType listType;
      SoyType argType = node.getParam(0).getType();
      if (argType instanceof AbstractMapType && ((AbstractMapType) argType).isEmpty()) {
        listType = ListType.empty();
      } else {
        SoyType listArg;
        if (argType instanceof LegacyObjectMapType) {
          listArg = ((LegacyObjectMapType) argType).getKeyType(); // pretty much just string
        } else if (ListType.ANY_LIST.isAssignableFromStrict(argType)) {
          listArg = IntType.getInstance();
        } else if (MapType.ANY_MAP.isAssignableFromStrict(argType)) {
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
      SoyType argType = node.getParam(0).getType().getEffectiveType();
      if (argType instanceof AbstractMapType && ((AbstractMapType) argType).isEmpty()) {
        node.setType(MapType.empty());
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
      MapType argType = node.getParam(0).getType().asType(MapType.class);
      if (argType.isEmpty()) {
        node.setType(LegacyObjectMapType.empty());
      } else {
        node.setType(
            typeRegistry.getOrCreateLegacyObjectMapType(
                // Converting a map to a legacy object map coerces all the keys to strings
                StringType.getInstance(), argType.getValueType()));
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

      for (int i = 0; i < node.numParams(); i++) {
        Identifier fieldName = node.getParamNames().get(i);
        ExprNode expr = node.getParam(i);

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
        if (SoyTypes.isNullOrUndefined(argType)) {
          errorReporter.report(
              expr.getSourceLocation(), PROTO_NULL_ARG_TYPE, fieldName.identifier());
        }

        SoyType fieldType = protoType.getFieldSetterType(fieldName.identifier());

        // Same for List<?>, for repeated fields
        if (ListType.ANY_LIST.isAssignableFromStrict(fieldType)
            && argType instanceof AbstractIterableType) {
          SoyType argElementType = ((AbstractIterableType) argType).getElementType();
          if (argElementType.isOfKind(Kind.UNKNOWN)) {
            continue;
          }
        }

        SoyType expectedType = SoyTypes.unionWithNullish(fieldType);
        if (!expectedType.isAssignableFromLoose(argType)) {
          argType = maybeCoerceType(expr, expectedType);
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
      if (existingType instanceof TemplateImportType) {
        TemplateType basicType = ((TemplateImportType) existingType).getBasicTemplateType();
        node.setType(
            Preconditions.checkNotNull(
                basicType,
                "No type for %s (%s)",
                node.getResolvedName(),
                node.getSourceLocation()));
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
      // We should probably flag 'undefined' here for all arithmetic operators, as it results in an
      //  exception in JbcSrc. In JavaScript it always turns into NaN, which we don't have in Soy.
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
      SoyType effectiveType = baseType.getEffectiveType();
      switch (effectiveType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the field type.
          return UnknownType.getInstance();

        case RECORD:
          {
            RecordType recordType = (RecordType) effectiveType;
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
            UnionType unionType = (UnionType) effectiveType;
            List<SoyType> fieldTypes = new ArrayList<>(unionType.getMembers().size());
            for (SoyType unionMember : unionType.getMembers()) {
              // TODO:(b/246982549): Remove this if-statement, as is this means you can freely
              // dereference nullish types without the compiler complaining.
              if (SoyTypes.isNullOrUndefined(unionMember)) {
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
            return computeLowestCommonType(typeRegistry, fieldTypes);
          }

        case TEMPLATE_TYPE:
        case PROTO_TYPE:
        case PROTO_EXTENSION:
          // May not be erased if other errors are present.
          return UnknownType.getInstance();

        default:
          emitDefaultFieldNotFoundError(baseType, fieldName, sourceLocation);
          return UnknownType.getInstance();
      }
    }

    private void emitDefaultFieldNotFoundError(
        SoyType baseType, String fieldName, SourceLocation sourceLocation) {
      if (!methodRegistry.matchForNameAndBase(fieldName, baseType).isEmpty()) {
        errorReporter.report(sourceLocation, METHOD_REFERENCE);
      } else {
        ImmutableSet<String> allFields = fieldRegistry.getAllFieldNames(excludeNullish(baseType));
        String didYouMean =
            allFields.isEmpty() ? "" : SoyErrors.getDidYouMeanMessage(allFields, fieldName);
        errorReporter.report(sourceLocation, NO_SUCH_FIELD, fieldName, baseType, didYouMean);
      }
    }

    /** Given a base type and an item key type, compute the item value type. */
    private SoyType getItemTypeForAccessNode(
        SoyType baseType,
        SoyType keyType,
        boolean isNullSafe,
        SourceLocation baseLocation,
        SourceLocation keyLocation) {
      baseType = baseType.getEffectiveType();
      keyType = keyType.getEffectiveType();

      switch (baseType.getKind()) {
        case UNKNOWN:
          // If we don't know anything about the base type, then make no assumptions
          // about the item type.
          return UnknownType.getInstance();

        case LIST:
          ListType listType = (ListType) baseType;

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
            if (mapType.isEmpty()) {
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
              if (SoyTypes.isNullOrUndefined(unionMember)) {
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
            return computeLowestCommonType(typeRegistry, itemTypes);
          }

        default:
          errorReporter.report(baseLocation, BRACKET_ACCESS_NOT_SUPPORTED, baseType);
          return UnknownType.getInstance();
      }
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

    private void validateBuiltinArgTypes(BuiltinFunction builtinFunction, FunctionNode node) {
      builtinFunction
          .getValidArgTypes()
          .ifPresent(
              typeList -> {
                int arity = getOnlyElement(builtinFunction.getValidArgsSizes());
                if (arity != node.numParams()) {
                  return; // previously reported error.
                }
                node.setAllowedParamTypes(typeList);
                for (int i = 0; i < typeList.size(); i++) {
                  if (!typeList.get(i).isAssignableFromStrict(node.getParam(i).getType())) {
                    errorReporter.report(
                        node.getParam(i).getSourceLocation(),
                        INCORRECT_ARG_TYPE,
                        builtinFunction.getName(),
                        node.getParam(i).getType(),
                        typeList.get(i));
                  }
                }
              });
    }

    /**
     * Private helper that checks types of the arguments and tries to set the return type for some
     * built-in functions.
     */
    private void visitBuiltinFunction(BuiltinFunction builtinFunction, FunctionNode node) {
      validateBuiltinArgTypes(builtinFunction, node);
      switch (builtinFunction) {
        case CHECK_NOT_NULL:
          SoyType type = node.getParam(0).getType();
          if (SoyTypes.isNullOrUndefined(type)) {
            errorReporter.report(
                node.getSourceLocation(), CHECK_NOT_NULL_ON_COMPILE_TIME_NULL, "call checkNotNull");
          } else {
            // Same type as its child but with nulls removed
            node.setType(excludeNullish(type));
          }
          break;
        case IS_PRIMARY_MSG_IN_USE:
          // don't bother checking the args, they are only ever set by the MsgIdFunctionPass
          node.setType(BoolType.getInstance());
          break;
        case CSS:
          checkArgIsStringLiteralWithNoSpaces(node, node.numParams() - 1, builtinFunction);
          node.setType(StringType.getInstance());
          break;
        case SOY_SERVER_KEY:
        case XID:
        case RECORD_JS_ID:
          // arg validation is already handled by the XidPass
          node.setType(StringType.getInstance());
          break;
        case UNKNOWN_JS_GLOBAL:
          checkArgIsStringLiteral(node, 0, builtinFunction);
          node.setType(UnknownType.getInstance());
          break;
        case VE_DATA:
          // Arg validation is already handled by the VeLogValidationPass
          node.setType(VeDataType.getInstance());
          break;
        case VE_DEF:
          if (node.numParams() >= 3) {
            node.setType(VeType.of(node.getParam(2).getType().toString()));
          } else {
            node.setType(VeType.NO_DATA);
          }
          break;
        case TO_NUMBER:
        case INT_TO_NUMBER:
          Preconditions.checkState(node.getType() != null);
          break;
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
                          node.numParams() > 0
                              ? node.getParam(0).getType()
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
          visit(node.getParam(0));
          node.setType(SoyTypes.undefinedToNull(node.getParam(0).getType()));
          break;
        case EVAL_TOGGLE:
        case DEBUG_SOY_TEMPLATE_INFO:
        case BOOLEAN:
        case HAS_CONTENT:
        case IS_TRUTHY_NON_EMPTY:
          node.setType(BoolType.getInstance());
          break;
        case NEW_SET:
          visit(node.getParam(0));
          SoyType listType = node.getParam(0).getType();
          if (listType instanceof AbstractIterableType) {
            if (((AbstractIterableType) listType).isEmpty()) {
              node.setType(SetType.empty());
            } else {
              SoyType keyType = ((AbstractIterableType) listType).getElementType();
              if (SoyTypes.flattenUnion(keyType).anyMatch(t -> !MapType.isAllowedKeyValueType(t))) {
                errorReporter.report(
                    node.getParam(0).getSourceLocation(),
                    CheckDeclaredTypesPass.BAD_MAP_OR_SET_KEY_TYPE,
                    keyType);
              }
              node.setType(
                  typeRegistry.getOrCreateSetType(
                      ((AbstractIterableType) listType).getElementType()));
            }
          } else {
            node.setType(UnknownType.getInstance());
          }
          break;
        case EMPTY_TO_UNDEFINED:
        case FLUSH_PENDING_LOGGING_ATTRIBUTES:
          throw new AssertionError("impossible, this is only used by later passes: " + node);
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
      if (childIndex < 0 || childIndex >= node.numParams()) {
        return null;
      }

      ExprNode arg = node.getParam(childIndex);
      if (!(arg instanceof StringNode)) {
        errorReporter.report(arg.getSourceLocation(), STRING_LITERAL_REQUIRED, funcName.getName());
        return null;
      }
      return (StringNode) arg;
    }

    private void visitInternalExtern(FunctionNode node) {
      Extern externRef = (Extern) node.getSoyFunction();
      if (externRef.getPath().path().endsWith("java/soy/plugins/functions.soy")
          && externRef.getName().equals("unpackAny")) {
        ExprNode secondParam = node.getParam(1);
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
        if (checkArgType(node.getParam(0), LegacyObjectMapType.ANY_MAP, node)) {
          visitLegacyObjectMapToMapFunction(node);
        } else {
          node.setType(UnknownType.getInstance());
        }
      } else if (fn instanceof MapToLegacyObjectMapFunction) {
        // If argument type is incorrect, do not try to create a return type. Instead, set the
        // return type to unknown.
        // We disallow unknown for this function in order to ensure that maps remain strongly typed
        if (checkArgType(node.getParam(0), MapType.ANY_MAP, node, UnknownPolicy.DISALLOWED)) {
          visitMapToLegacyObjectMapFunction(node);
        } else {
          node.setType(UnknownType.getInstance());
        }
      } else if (fn instanceof KeysFunction) {
        visitKeysFunction(node);
      } else if (fn instanceof ConcatListsFunction) {
        node.setType(getGenericListType(node.getParams()));
      } else if (fn instanceof LoggingFunction) {
        // LoggingFunctions always return string.
        node.setType(StringType.getInstance());
      } else if (fn instanceof MaxFunction || fn instanceof MinFunction) {
        // Merge types of the two arguments.
        if (node.numParams() > 1) {
          node.setType(
              computeLowestCommonType(
                  typeRegistry, node.getParam(0).getType(), node.getParam(1).getType()));
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
      SoyType paramsUnionType =
          UnionType.of(
              Streams.stream(intersectionOf)
                  .map(n -> n.getType().getEffectiveType())
                  .collect(toImmutableSet()));
      SoyType elementType = SoyTypes.getIterableElementType(typeRegistry, paramsUnionType);
      return typeRegistry.getOrCreateListType(elementType);
    }

    private SoyType getGenericMapType(Iterable<ExprNode> intersectionOf) {
      ImmutableSet.Builder<SoyType> keyTypesBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<SoyType> valueTypesBuilder = ImmutableSet.builder();
      for (ExprNode childNode : intersectionOf) {
        SoyType childType = childNode.getType().getEffectiveType();
        // If one of the types isn't a list, we can't compute the intersection. Return UnknownType
        // and assume the caller is already reporting an error for bad args.
        if (!(childType instanceof MapType)) {
          return UnknownType.getInstance();
        }
        MapType mapType = ((MapType) childType);
        if (mapType.isEmpty()) {
          continue;
        }
        keyTypesBuilder.add(mapType.getKeyType());
        valueTypesBuilder.add(mapType.getValueType());
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
          && node.getSoyFunction() != BuiltinFunction.NEW_SET
          && node.getSoyFunction() != BuiltinFunction.PROTO_INIT
          && node.getSoyFunction() != BuiltinFunction.CSS
          && node.getSoyFunction() != BuiltinFunction.XID
          && node.getSoyFunction() != BuiltinFunction.RECORD_JS_ID
          && node.getSoyFunction() != BuiltinFunction.VE_DEF
          && node.getSoyFunction() != BuiltinFunction.EVAL_TOGGLE) {
        notAllowed(node);
      }
      super.visitFunctionNode(node);
    }

    @Override
    protected void visitMethodCallNode(MethodCallNode node) {
      super.visitMethodCallNode(node);
      if (node.isMethodResolved()
          && (node.getSoyMethod() == BuiltinMethod.BIND
              || node.getSoyMethod() == BuiltinMethod.FUNCTION_BIND)) {
        return;
      }
      notAllowed(node);
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
                                  SourceFilePath.create(
                                      function.getClass().getName(), function.getClass().getName());
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
                  if (m.appliesToBase(baseType) && m.acceptsArgCount(argTypes.size())) {
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
                                  SourceFilePath.create(
                                      f.getClass().getName(), f.getClass().getName());
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
      Preconditions.checkArgument(SoyTypes.isNullOrUndefined(baseType) || !isNullish(baseType));
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
}
