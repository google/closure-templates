/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * d`tributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_UNDEFINED;
import static com.google.template.soy.jssrc.dsl.Expressions.arrowFunction;
import static com.google.template.soy.jssrc.dsl.Expressions.construct;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.not;
import static com.google.template.soy.jssrc.dsl.Expressions.number;
import static com.google.template.soy.jssrc.dsl.Expressions.operation;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.BIND_TEMPLATE_PARAMS;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ARRAY_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_DEBUG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_CSS_NAME;
import static com.google.template.soy.jssrc.internal.JsRuntime.IJ_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.JS_TO_PROTO_PACK_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.MARK_TEMPLATE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SERIALIZE_KEY;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_CHECK_NOT_NULL;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_COERCE_TO_BOOLEAN;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_EMPTY_TO_NULL;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_EQUALS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_FILTER_AND_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_HAS_CONTENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_IS_TRUTHY_NON_EMPTY;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAKE_ARRAY;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_NEWMAPS_TRANSFORM_VALUES;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.getToggleRef;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoBytesPackToByteStringFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoConstructor;
import static com.google.template.soy.passes.ContentSecurityPolicyNonceInjectionPass.CSP_NONCE_VARIABLE_NAME;
import static com.google.template.soy.passes.ContentSecurityPolicyNonceInjectionPass.CSP_STYLE_NONCE_VARIABLE_NAME;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.FunctionNode.ExternRef;
import com.google.template.soy.exprtree.GlobalNode;
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
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.ProtoCall;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.IterableType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnknownType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an equivalent
 * chunk of JavaScript code.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <h3>Types and Dependencies</h3>
 *
 * Types are used to allow reflective access to protobuf values even after JSCompiler has rewritten
 * field names.
 *
 * <p>For example, one might normally access field foo on a protocol buffer by calling
 *
 * <pre>my_pb.getFoo()</pre>
 *
 * A Soy author can access the same by writing
 *
 * <pre>{$my_pb.foo}</pre>
 *
 * But the relationship between "foo" and "getFoo" is not preserved by JSCompiler's renamer.
 *
 * <p>To avoid adding many spurious dependencies on all protocol buffers compiled with a Soy
 * template, we make type-unsound (see CAVEAT below) assumptions:
 *
 * <ul>
 *   <li>That the top-level inputs, opt_data and opt_ijData, do not need conversion.
 *   <li>That we can enumerate the concrete types of a container when the default field lookup
 *       strategy would fail. For example, if an instance of {@code my.Proto} can be passed to the
 *       param {@code $my_pb}, then {@code $my_pb}'s static type is a super-type of {@code
 *       my.Proto}.
 *   <li>That the template contains enough information to determine types that need to be converted.
 *       <br>
 *       Pluggable {@link com.google.template.soy.types.SoyTypeRegistry SoyTypeRegistries} allow
 *       recognizing input coercion, for example between {@code safevalues.SafeHtml} and Soy's
 *       {@code html} string sub-type. <br>
 *       When the converted type is a protocol-buffer type, we assume that the expression to be
 *       converted can be fully-typed by expressionTypesVisitor.
 * </ul>
 *
 * <p>CAVEAT: These assumptions are unsound, but necessary to be able to deploy JavaScript binaries
 * of acceptable size.
 *
 * <p>Type-failures are correctness issues but do not lead to increased exposure to XSS or otherwise
 * compromise security or privacy since a failure to unpack a type leads to a value that coerces to
 * a trivial value like {@code undefined} or {@code "[Object]"}.
 */
public class TranslateExprNodeVisitor extends AbstractReturningExprNodeVisitor<Expression> {

  private static final Joiner COMMA_JOINER = Joiner.on(", ");

  private static final SoyErrorKind UNION_ACCESSOR_MISMATCH =
      SoyErrorKind.of(
          "Cannot access field ''{0}'' of type ''{1}'', "
              + "because the different union member types have different access methods.");

  private static final SoyErrorKind SOY_JS_SRC_FUNCTION_NOT_FOUND =
      SoyErrorKind.of(
          "Function ''{0}'' implemented by ''{1}'' does not have a JavaScript implementation.");

  private static final SoyErrorKind SOY_JS_SRC_BAD_LIST_TO_MAP_CONSTRUCTOR =
      SoyErrorKind.of("List to map constructor encloses ''{0}'', which is not a list.");

  /**
   * The current replacement JS expressions for the local variables (and foreach-loop special
   * functions) current in scope.
   */
  private final TranslationContext translationContext;

  private final JavaScriptValueFactoryImpl javascriptValueFactory;
  private final ErrorReporter errorReporter;
  private final TemplateAliases templateAliases;

  /**
   * An expression that represents the data parameter to read params from. Defaults to {@code
   * OPT_DATA}.
   */
  private final Expression dataSource;

  public TranslateExprNodeVisitor(
      JavaScriptValueFactoryImpl javascriptValueFactory,
      TranslationContext translationContext,
      TemplateAliases templateAliases,
      ErrorReporter errorReporter,
      Expression dataSource) {
    this.javascriptValueFactory = javascriptValueFactory;
    this.errorReporter = errorReporter;
    this.translationContext = translationContext;
    this.templateAliases = templateAliases;
    this.dataSource = dataSource;
  }

  public Expression getDataSource() {
    return dataSource;
  }

  @Override
  protected Expression visit(ExprNode node) {
    return checkNotNull(super.visit(node), "visit(%s) returned null", node);
  }

  /**
   * Method that returns code to access a named parameter.
   *
   * @param paramName the name of the parameter.
   * @param varDefn The variable definition of the parameter
   * @return The code to access the value of that parameter.
   */
  Expression genCodeForParamAccess(String paramName, VarDefn varDefn) {
    Expression source = dataSource;
    checkState(
        varDefn instanceof TemplateParam || varDefn instanceof TemplateStateVar,
        "expected a parameter type, got, %s",
        varDefn);
    if (varDefn.isInjected()) {
      // Special case for csp_nonce. It is created by the compiler itself, and users should not need
      // to set it. So, instead of generating opt_ij_data.csp_nonce, we generate opt_ij_data &&
      // opt_ij_data.csp_nonce.
      // TODO(lukes): we only need to generate this logic if there aren't any other ij params
      if (paramName.equals(CSP_NONCE_VARIABLE_NAME)
          || paramName.equals(CSP_STYLE_NONCE_VARIABLE_NAME)) {
        return IJ_DATA.and(IJ_DATA.dotAccess(paramName), translationContext.codeGenerator());
      }
      source = IJ_DATA;
    } else if (varDefn.kind() == VarDefn.Kind.STATE) {
      return genCodeForStateAccess(paramName, (TemplateStateVar) varDefn);
    }
    if (varDefn instanceof TemplateParam && ((TemplateParam) varDefn).isImplicit()) {
      // implicit params are not in the type declaration for the opt_data parameter, so we need to
      // cast as ? to access implicit params
      source = source.castAsUnknown();
    }
    return source.dotAccess(paramName);
  }

  /**
   * Method that returns code to access a named state parameter.
   *
   * @param paramName The name of the state parameter.
   * @param stateVar The variable definition of the state parameter
   * @return The code to access the value of that parameter.
   */
  protected Expression genCodeForStateAccess(String paramName, TemplateStateVar stateVar) {
    return Expressions.id(paramName);
  }

  @Override
  protected Expression visitExprRootNode(ExprRootNode node) {
    // ExprRootNode is some indirection to make it easier to replace expressions. All we need to do
    // is visit the only child
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override
  protected Expression visitBooleanNode(BooleanNode node) {
    return node.getValue() ? LITERAL_TRUE : LITERAL_FALSE;
  }

  @Override
  protected Expression visitFloatNode(FloatNode node) {
    return number(node.getValue());
  }

  @Override
  protected Expression visitIntegerNode(IntegerNode node) {
    return number(node.getValue());
  }

  @Override
  protected Expression visitNullNode(NullNode node) {
    return LITERAL_NULL;
  }

  @Override
  protected Expression visitUndefinedNode(UndefinedNode node) {
    return LITERAL_UNDEFINED;
  }

  @Override
  protected Expression visitStringNode(StringNode node) {
    return stringLiteral(node.getValue());
  }

  @Override
  protected Expression visitProtoEnumValueNode(ProtoEnumValueNode node) {
    return number(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected Expression visitListLiteralNode(ListLiteralNode node) {
    if (node.numChildren() == 0) {
      return Expressions.arrayLiteral(ImmutableList.of());
    }
    return SOY_MAKE_ARRAY.call(visitChildren(node));
  }

  @Override
  protected Expression visitListComprehensionNode(ListComprehensionNode node) {
    Expression base = visit(node.getListExpr());
    String listIterVarTranslation =
        "list_comp_" + node.getNodeId() + "_" + node.getListIterVar().name();
    // List comprehensions create a sub-scope, so create a new mappings object and restore it when
    // we return.
    try (var unused = translationContext.enterSoyScope()) {
      translationContext
          .soyToJsVariableMappings()
          .put(node.getListIterVar(), id(listIterVarTranslation));
      String indexVarTranslation =
          node.getIndexVar() == null
              ? null
              : "list_comp_" + node.getNodeId() + "_" + node.getIndexVar().name();
      if (node.getIndexVar() != null) {
        translationContext
            .soyToJsVariableMappings()
            .put(node.getIndexVar(), id(indexVarTranslation));
      }
      SoyType listType = SoyTypes.tryRemoveNullish(node.getListExpr().getType());
      // elementType can be unknown if it is the special EMPTY_LIST or if it isn't a known list
      // type.
      SoyType elementType =
          listType instanceof IterableType
              ? ((IterableType) listType).getElementType()
              : UnknownType.getInstance();
      JsType elementJsType = jsTypeForStrict(elementType);
      JsDoc doc =
          node.getIndexVar() == null
              ? JsDoc.builder()
                  .addParam(listIterVarTranslation, elementJsType.typeExpr())
                  .addGoogRequires(elementJsType.getGoogRequires())
                  .build()
              : JsDoc.builder()
                  .addParam(listIterVarTranslation, elementJsType.typeExpr())
                  .addParam(indexVarTranslation, "number")
                  .addGoogRequires(elementJsType.getGoogRequires())
                  .build();

      if (node.getFilterExpr() != null && node.getIndexVar() != null) {
        return SOY_FILTER_AND_MAP.call(
            base,
            arrowFunction(
                doc,
                maybeCoerceToBoolean(
                    node.getFilterExpr().getType(),
                    visit(node.getFilterExpr()),
                    /* force= */ false)),
            arrowFunction(doc, visit(node.getListItemTransformExpr())));
      }
      if (node.getFilterExpr() != null) {
        // Cast the receiver type to ReadonlyArray to fix poor type inference on filter callback
        // functions when the type is Array<X>|
        base =
            JsRuntime.SOY_AS_READONLY
                .call(base)
                .dotAccess("filter")
                .call(
                    arrowFunction(
                        doc,
                        maybeCoerceToBoolean(
                            node.getFilterExpr().getType(),
                            visit(node.getFilterExpr()),
                            /* force= */ false)));
      }
      // handle a special case for trivial transformations
      if (node.getListItemTransformExpr().getKind() == ExprNode.Kind.VAR_REF_NODE) {
        VarRefNode transformNode = (VarRefNode) node.getListItemTransformExpr();
        if (transformNode.getDefnDecl().equals(node.getListIterVar())) {
          return base;
        }
      }
      // Cast the receiver type to ReadonlyArray to fix poor type inference on map callback
      // functions
      base =
          JsRuntime.SOY_AS_READONLY
              .call(base)
              .dotAccess("map")
              .call(arrowFunction(doc, visit(node.getListItemTransformExpr())));
      return base;
    }
  }

  @Override
  protected Expression visitRecordLiteralNode(RecordLiteralNode node) {
    LinkedHashMap<String, Expression> objLiteral = new LinkedHashMap<>();

    // Process children
    for (int i = 0; i < node.numChildren(); i++) {
      ExprNode child = node.getChild(i);
      String key =
          child.getKind() == ExprNode.Kind.SPREAD_OP_NODE
              ? Expressions.objectLiteralSpreadKey()
              : node.getKey(i).identifier();
      objLiteral.put(key, visit(node.getChild(i)));
    }

    // Build the record literal
    return Expressions.objectLiteral(objLiteral);
  }

  @Override
  protected Expression visitMapLiteralNode(MapLiteralNode node) {
    // Always construct maps as ES6 Maps so that we can call `set`.
    Expression map = Expressions.constructMap();
    if (node.getType() != MapType.EMPTY_MAP) {
      MapType mapType = (MapType) node.getType();
      JsType keyType = jsTypeFor(mapType.getKeyType());
      JsType valueType = jsTypeFor(mapType.getValueType());
      map =
          map.castAs(
              String.format("!Map<%s, %s>", keyType.typeExpr(), valueType.typeExpr()),
              ImmutableSet.<GoogRequire>builder()
                  .addAll(keyType.getGoogRequires())
                  .addAll(valueType.getGoogRequires())
                  .build());
    }

    // Populate the map.
    for (int i = 0; i < node.numChildren(); i += 2) {
      ExprNode keyNode = node.getChild(i);
      // Constructing a map literal with a null key is a runtime error.
      Expression key = assertNonNull(keyNode);
      Expression value = visit(node.getChild(i + 1));
      map = map.dotAccess("set").call(key, value);
    }

    return map;
  }

  @Override
  protected Expression visitMapLiteralFromListNode(MapLiteralFromListNode node) {
    // Generate the code "new Map(list.map(dummyVar => [dummyVar.key, dummyVar.value]))"
    // corresponding to the input "map(list)"
    Expression list = visit(node.getListExpr());
    SoyType listType = node.getListExpr().getType();
    if (!(listType instanceof ListType)) {
      errorReporter.report(node.getSourceLocation(), SOY_JS_SRC_BAD_LIST_TO_MAP_CONSTRUCTOR, list);
      return Expressions.constructMap();
    }
    if (listType.equals(ListType.EMPTY_LIST)) {
      // If the list is empty, trying to infer the type of the dummyVar is futile. So we create a
      // special case and directly return an empty list.
      return Expressions.constructMap();
    }

    String dummyVar = "list_to_map_constructor_" + node.getNodeId();
    JsDoc doc =
        JsDoc.builder()
            .addParam(dummyVar, jsTypeForStrict(((ListType) listType).getElementType()).typeExpr())
            .build();

    Expression body =
        Expressions.arrayLiteral(
            Arrays.asList(
                id(dummyVar).dotAccess(MapLiteralFromListNode.KEY_STRING),
                id(dummyVar).dotAccess(MapLiteralFromListNode.VALUE_STRING)));

    Expression nestedList = list.dotAccess("map").call(arrowFunction(doc, body));
    return Expressions.constructMap(nestedList);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected Expression visitVarRefNode(VarRefNode node) {
    Expression translation = translationContext.soyToJsVariableMappings().maybeGet(node.getName());
    if (translation != null) {
      // Case 1: In-scope local var.
      return translation;
    }

    // Case 2: Data reference.
    // TODO(lukes): I believe this case is only present for state vars in jssrc, everything else
    // should hit the above.
    return genCodeForParamAccess(node.getNameWithoutLeadingDollar(), node.getDefnDecl());
  }

  @Override
  protected Expression visitDataAccessNode(DataAccessNode node) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!node.isNullSafe());
    return accumulateDataAccess(node).result(translationContext.codeGenerator());
  }

  @Override
  protected Expression visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
    NullSafeAccumulator accumulator = new NullSafeAccumulator(visit(nullSafeAccessNode.getBase()));
    ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
    while (dataAccess.getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
      NullSafeAccessNode nullSafe = (NullSafeAccessNode) dataAccess;
      accumulator = accumulateNullSafeDataAccess((DataAccessNode) nullSafe.getBase(), accumulator);
      dataAccess = nullSafe.getDataAccess();
    }
    return accumulateNullSafeDataAccessTail((AccessChainComponentNode) dataAccess, accumulator)
        .result(translationContext.codeGenerator());
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  protected Expression sanitizedContentToProtoConverterFunction(Descriptor messageType) {
    return JS_TO_PROTO_PACK_FN.get(messageType.getFullName());
  }

  private NullSafeAccumulator accumulateNullSafeDataAccessTail(
      AccessChainComponentNode dataAccess, NullSafeAccumulator accumulator) {
    if (dataAccess.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
      AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccess;
      dataAccess = (AccessChainComponentNode) assertNonNull.getChild(0);
    }
    return accumulateNullSafeDataAccess((DataAccessNode) dataAccess, accumulator);
  }

  private NullSafeAccumulator accumulateNullSafeDataAccess(
      DataAccessNode dataAccessNode, NullSafeAccumulator accumulator) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!dataAccessNode.isNullSafe());
    boolean accessChain = false;
    if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
      // This is an access chain (e.g. $foo.bar.baz), so recurse through the base expression
      // ($foo.bar) first.
      accumulator =
          accumulateNullSafeDataAccess(
              (DataAccessNode) dataAccessNode.getBaseExprChild(), accumulator);
      accessChain = true;
    }
    return accumulateDataAccess(dataAccessNode, accumulator, !accessChain);
  }

  private NullSafeAccumulator accumulateDataAccess(DataAccessNode dataAccessNode) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!dataAccessNode.isNullSafe());
    NullSafeAccumulator accumulator;
    if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
      // This is an access chain (e.g. $foo.bar.baz), so recurse through the base expression
      // ($foo.bar) first.
      accumulator = accumulateDataAccess((DataAccessNode) dataAccessNode.getBaseExprChild());
    } else {
      // The base expression is not a DataAccessNode, so this is the base of an access chain.
      accumulator = new NullSafeAccumulator(visit(dataAccessNode.getBaseExprChild()));
    }
    return accumulateDataAccess(dataAccessNode, accumulator, /* nullSafe= */ false);
  }

  private NullSafeAccumulator accumulateDataAccess(
      DataAccessNode dataAccessNode, NullSafeAccumulator accumulator, boolean nullSafe) {
    switch (dataAccessNode.getKind()) {
      case FIELD_ACCESS_NODE:
        FieldAccessNode fieldAccess = (FieldAccessNode) dataAccessNode;
        SoySourceFunctionMethod sourceMethod = fieldAccess.getSoyMethod();
        if (sourceMethod != null) {
          return accumulator.functionCall(
              nullSafe,
              javascriptValueFactory.invocation(
                  fieldAccess.getSourceLocation(),
                  fieldAccess.getFieldName(),
                  (SoyJavaScriptSourceFunction) sourceMethod.getImpl(),
                  ImmutableList.of(),
                  translationContext.codeGenerator()));
        }

        FieldAccess access =
            genCodeForFieldAccess(
                fieldAccess.getBaseExprChild().getType(),
                fieldAccess.getAccessSourceLocation(),
                fieldAccess.getFieldName());
        return accumulator.dotAccess(access, nullSafe);
      case ITEM_ACCESS_NODE:
        ItemAccessNode itemAccess = (ItemAccessNode) dataAccessNode;
        ExprNode keyNode = itemAccess.getKeyExprChild();
        return accumulator.bracketAccess(
            // The key type may not match JsCompiler's type system (passing number as enum, or
            // nullable proto field).  I could instead cast this to the map's key type.
            visit(keyNode).castAsUnknown(), nullSafe); // vanilla bracket access
      case METHOD_CALL_NODE:
        MethodCallNode methodCall = (MethodCallNode) dataAccessNode;
        return genCodeForMethodCall(accumulator, methodCall, nullSafe);
      default:
        throw new AssertionError(dataAccessNode.getKind());
    }
  }

  /**
   * Generates the code for a field access, e.g. {@code .foo} or {@code .getFoo()}.
   *
   * @param baseType The type of the object that contains the field.
   * @param sourceLocation The location of the access.
   * @param fieldName The field name.
   */
  private FieldAccess genCodeForFieldAccess(
      SoyType baseType, SourceLocation sourceLocation, String fieldName) {
    Preconditions.checkNotNull(baseType);
    return genCodeForMaybeUnion(
        baseType,
        fieldName,
        sourceLocation,
        type -> {
          if (type.getKind() == SoyType.Kind.PROTO) {
            SoyProtoType protoType = (SoyProtoType) type;
            FieldDescriptor desc = protoType.getFieldDescriptor(fieldName);
            Preconditions.checkNotNull(
                desc,
                "Error in proto %s, field not found: %s",
                protoType.getDescriptor().getFullName(),
                fieldName);
            return FieldAccess.protoCall(fieldName, desc);
          }
          return FieldAccess.id(fieldName);
        });
  }

  /**
   * Generates the code for a method, e.g. {@code .getExtension(foo)}}.
   *
   * @param base The base expression of the method.
   * @param methodCallNode The method call node.
   */
  private NullSafeAccumulator genCodeForMethodCall(
      NullSafeAccumulator base, MethodCallNode methodCallNode, boolean nullSafe) {
    Preconditions.checkArgument(methodCallNode.isMethodResolved());

    SoyMethod soyMethod = methodCallNode.getSoyMethod();
    if (soyMethod instanceof BuiltinMethod) {
      BuiltinMethod builtinMethod = (BuiltinMethod) soyMethod;
      SoyType baseType = methodCallNode.getBaseType(nullSafe);
      SourceLocation sourceLocation = methodCallNode.getAccessSourceLocation();
      switch (builtinMethod) {
        case GET_READONLY_EXTENSION:
        case HAS_EXTENSION:
        case GET_EXTENSION:
          // Nullability has already been checked, but nonnull assertion operators are removed by
          // so the type may still appear nullable, in which case we can safely remove it.
          SoyProtoType protoBaseType = (SoyProtoType) SoyTypes.tryRemoveNullish(baseType);
          String extName = BuiltinMethod.getProtoExtensionIdFromMethodCall(methodCallNode);
          FieldDescriptor descriptor = protoBaseType.getFieldDescriptor(extName);
          return base.dotAccess(
              builtinMethod == BuiltinMethod.GET_READONLY_EXTENSION
                  ? ProtoCall.getReadonlyField(extName, descriptor)
                  : builtinMethod == BuiltinMethod.HAS_EXTENSION
                      ? ProtoCall.hasField(extName, descriptor)
                      : ProtoCall.getField(extName, descriptor),
              nullSafe);
        case HAS_PROTO_FIELD:
          String fieldName = BuiltinMethod.getProtoFieldNameFromMethodCall(methodCallNode);
          FieldAccess fieldAccess =
              genCodeForMaybeUnion(
                  baseType,
                  fieldName,
                  sourceLocation,
                  type ->
                      ProtoCall.hasField(
                          fieldName, ((SoyProtoType) type).getFieldDescriptor(fieldName)));
          return base.dotAccess(fieldAccess, nullSafe);
        case GET_PROTO_FIELD:
          fieldName = BuiltinMethod.getProtoFieldNameFromMethodCall(methodCallNode);
          fieldAccess =
              genCodeForMaybeUnion(
                  baseType,
                  fieldName,
                  sourceLocation,
                  type ->
                      ProtoCall.getField(
                          fieldName, ((SoyProtoType) type).getFieldDescriptor(fieldName)));
          return base.dotAccess(fieldAccess, nullSafe);
        case GET_PROTO_FIELD_OR_UNDEFINED:
          fieldName = BuiltinMethod.getProtoFieldNameFromMethodCall(methodCallNode);
          fieldAccess =
              genCodeForMaybeUnion(
                  baseType,
                  fieldName,
                  sourceLocation,
                  type ->
                      ProtoCall.getFieldOrUndefined(
                          fieldName, ((SoyProtoType) type).getFieldDescriptor(fieldName)));
          return base.dotAccess(fieldAccess, nullSafe);
        case GET_READONLY_PROTO_FIELD:
          fieldName = BuiltinMethod.getProtoFieldNameFromMethodCall(methodCallNode);
          fieldAccess =
              genCodeForMaybeUnion(
                  baseType,
                  fieldName,
                  sourceLocation,
                  type ->
                      ProtoCall.getReadonlyField(
                          fieldName, ((SoyProtoType) type).getFieldDescriptor(fieldName)));
          return base.dotAccess(fieldAccess, nullSafe);
          // When adding new built-in methods it may be necessary to assert that the base expression
          // is not null in order to prevent a method call on a null instance from ever succeeding.
        case MAP_GET:
          return base.mapGetAccess(visit(methodCallNode.getParam(0)), nullSafe);
        case BIND:
          return base.transform(
              nullSafe,
              (baseExpr) -> genCodeForBind(baseExpr, visit(methodCallNode.getParam(0)), baseType));
      }
      throw new AssertionError(builtinMethod);
    } else if (soyMethod instanceof SoySourceFunctionMethod) {
      SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) soyMethod;

      return base.functionCall(
          nullSafe,
          javascriptValueFactory.invocation(
              methodCallNode.getSourceLocation(),
              methodCallNode.getMethodName().identifier(),
              (SoyJavaScriptSourceFunction) sourceMethod.getImpl(),
              methodCallNode.getParams().stream().map(this::visit).collect(toImmutableList()),
              translationContext.codeGenerator()));
    } else {
      throw new AssertionError(soyMethod.getClass());
    }
  }

  /** Generates a FieldAccess for a base expression that might be a union of types. */
  private FieldAccess genCodeForMaybeUnion(
      SoyType baseType,
      String fieldName,
      SourceLocation sourceLocation,
      Function<SoyType, FieldAccess> memberGenerator) {
    if (baseType.isNullOrUndefined()) {
      // This is a special edge case since the loop below would be a no-op.
      return memberGenerator.apply(baseType);
    }
    FieldAccess fieldAccess = null;
    for (SoyType type : SoyTypes.expandUnions(baseType)) {
      if (type.isNullOrUndefined()) {
        continue;
      }
      FieldAccess fieldAccessForType = memberGenerator.apply(type);
      if (fieldAccess == null) {
        fieldAccess = fieldAccessForType;
      } else if (!fieldAccess.equals(fieldAccessForType)) {
        errorReporter.report(sourceLocation, UNION_ACCESSOR_MISMATCH, fieldName, baseType);
      }
    }
    return fieldAccess;
  }

  protected Expression genCodeForBind(
      Expression template, Expression paramRecord, SoyType templateType) {
    return BIND_TEMPLATE_PARAMS.call(template, paramRecord);
  }

  @Override
  protected Expression visitGlobalNode(GlobalNode node) {
    // jssrc supports unknown globals by plopping the global name directly into the output
    // NOTE: this may cause the jscompiler to emit warnings, users will need to whitelist them or
    // fix their use of unknown globals.
    return Expressions.dottedIdNoRequire(node.getName());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected Expression visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    List<Expression> operands = visitChildren(node);
    Expression consequent = operands.get(0);
    Expression alternate = operands.get(1);
    return consequent.nullishCoalesce(alternate, translationContext.codeGenerator());
  }

  @Override
  protected Expression visitAndOpNode(AndOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    ExprNode lhOperand = node.getChild(0);
    ExprNode rhOperand = node.getChild(1);
    // Explicit coercion is necessary for 2 reasons:
    // 1. Some soy values don't have proper js truthy semantics (SanitizedContent types) so we need
    //    an explicit coercion where we expect a boolean value.
    // 2. The soy 'and' operator is a boolean operator whereas the JS && operator has more complex
    //    semantics, so explicit coercions are necessary to ensure we get a boolean value.
    Expression lhChunk = maybeCoerceToBoolean(lhOperand.getType(), visit(lhOperand), true);
    Expression rhChunk = maybeCoerceToBoolean(rhOperand.getType(), visit(rhOperand), true);
    return lhChunk.and(rhChunk, translationContext.codeGenerator());
  }

  @Override
  protected Expression visitOrOpNode(OrOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    ExprNode lhOperand = node.getChild(0);
    ExprNode rhOperand = node.getChild(1);
    // See comments in visitAndOpNode for why explicit coercions are required.
    Expression lhChunk = maybeCoerceToBoolean(lhOperand.getType(), visit(lhOperand), true);
    Expression rhChunk = maybeCoerceToBoolean(rhOperand.getType(), visit(rhOperand), true);
    return lhChunk.or(rhChunk, translationContext.codeGenerator());
  }

  @Override
  protected Expression visitAmpAmpOpNode(AmpAmpOpNode node) {
    return visit(node.getChild(0)).and(visit(node.getChild(1)), translationContext.codeGenerator());
  }

  @Override
  protected Expression visitBarBarOpNode(BarBarOpNode node) {
    return visit(node.getChild(0)).or(visit(node.getChild(1)), translationContext.codeGenerator());
  }

  @Override
  protected Expression visitConditionalOpNode(ConditionalOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 3);
    return translationContext
        .codeGenerator()
        .conditionalExpression(
            maybeCoerceToBoolean(node.getChild(0).getType(), visit(node.getChild(0)), false),
            visit(node.getChild(1)),
            visit(node.getChild(2)));
  }

  @Override
  protected Expression visitNotOpNode(NotOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 1);
    ExprNode operand = node.getChild(0);
    return operation(
        node.getOperator(),
        Arrays.asList(maybeCoerceToBoolean(operand.getType(), visit(operand), false)));
  }

  /**
   * Wraps an arbitrary expression to be checked for truthiness.
   *
   * @param force {@code true} to always coerce to boolean; {@code false} to only coerce for idom.
   */
  protected Expression maybeCoerceToBoolean(SoyType type, Expression chunk, boolean force) {
    checkNotNull(type);
    checkNotNull(chunk);
    // TODO(lukes): we can have smarter logic here,  most types have trivial boolean coercions
    // we only need to be careful about ?,any and the sanitized types
    if (force && type.getKind() != Kind.BOOL) {
      return SOY_COERCE_TO_BOOLEAN.call(chunk);
    }
    return chunk;
  }

  protected Expression hasContent(Expression chunk) {
    return SOY_HAS_CONTENT.call(chunk);
  }

  protected Expression isTruthyNonEmpty(Expression chunk) {
    return SOY_IS_TRUTHY_NON_EMPTY.call(chunk);
  }

  @Override
  protected Expression visitOperatorNode(OperatorNode node) {
    return operation(node.getOperator(), visitChildren(node));
  }

  @Override
  protected Expression visitAsOpNode(AsOpNode node) {
    // TODO(b/156780590): Implement.
    return visit(node.getChild(0));
  }

  @Override
  protected Expression visitInstanceOfOpNode(InstanceOfOpNode node) {
    SoyType operand = node.getChild(1).getType();
    Expression value = visit(node.getChild(0));

    // Handle any operand type supported by SoyTypes#isValidInstanceOfOperand()
    switch (operand.getKind()) {
      case STRING:
        return value.typeOf().tripleEquals(stringLiteral("string"));
      case BOOL:
        return value.typeOf().tripleEquals(stringLiteral("boolean"));
      case UNION:
        if (operand.equals(SoyTypes.NUMBER_TYPE)) {
          return value.typeOf().tripleEquals(stringLiteral("number"));
        }
        break;
      case LIST:
        return JsRuntime.ARRAY_IS_ARRAY.call(value);
      case SET:
        return value.instanceOf(id("Set"));
      case MAP:
        return value.instanceOf(id("Map"));
      case PROTO:
        SoyProtoType protoType = (SoyProtoType) operand;
        return protoConstructor(protoType).dotAccess("hasInstance").call(value);
      case MESSAGE:
        return value.instanceOf(GoogRequire.create("jspb.Message").reference());
      case JS:
        return JsRuntime.IS_JS.call(value);
      case CSS:
        return JsRuntime.IS_CSS.call(value);
      case URI:
        return JsRuntime.IS_URI.call(value);
      case HTML:
        return JsRuntime.IS_HTML.call(value);
      case TRUSTED_RESOURCE_URI:
        return JsRuntime.IS_TRUSTED_RESOURCE_URI.call(value);
      case ATTRIBUTES:
        return JsRuntime.IS_ATTRIBUTE.call(value);
      default:
        break;
    }

    throw new AssertionError("Compiler should have disallowed: " + operand);
  }

  private static final ImmutableSet<SoyType.Kind> CAN_USE_EQUALS =
      Sets.immutableEnumSet(
          SoyType.Kind.INT, SoyType.Kind.FLOAT, SoyType.Kind.PROTO_ENUM, Kind.BOOL, Kind.STRING);

  private Expression visitEqualNodeHelper(OperatorNode node, Operator eq) {
    boolean needsSoyEquals = false;
    boolean neverSoyEquals = false;

    for (ExprNode c : node.getChildren()) {
      SoyType type = c.getType();
      if (type.isNullOrUndefined()) {
        // If either operand is null always use ==.
        neverSoyEquals = true;
      } else if (!SoyTypes.isKindOrUnionOfKinds(type, CAN_USE_EQUALS)) {
        // If either operand is not a JS primitive (number, string, bool) then use soy.$$equals.
        needsSoyEquals = true;
      }
    }

    Expression rv;
    if (needsSoyEquals && !neverSoyEquals) {
      rv = SOY_EQUALS.call(visitChildren(node));
      if (eq == Operator.NOT_EQUAL) {
        rv = not(rv);
      }
    } else {
      rv = operation(eq, visitChildren(node));
    }
    return rv;
  }

  @Override
  protected Expression visitEqualOpNode(EqualOpNode node) {
    return visitEqualNodeHelper(node, Operator.EQUAL);
  }

  @Override
  protected Expression visitNotEqualOpNode(NotEqualOpNode node) {
    return visitEqualNodeHelper(node, Operator.NOT_EQUAL);
  }

  @Override
  protected Expression visitAssertNonNullOpNode(AssertNonNullOpNode node) {
    return visit(Iterables.getOnlyElement(node.getChildren()));
  }

  private Expression protoInitFieldValue(
      FieldDescriptor fieldDesc, Expression fieldValue, boolean isRepeatedElement) {
    boolean isArray = fieldDesc.isRepeated() && !isRepeatedElement;
    if (ProtoUtils.isSanitizedContentField(fieldDesc)) {
      Expression sanitizedContentPackFn =
          sanitizedContentToProtoConverterFunction(fieldDesc.getMessageType());
      return isArray
          ? GOOG_ARRAY_MAP.call(fieldValue, sanitizedContentPackFn)
          : sanitizedContentPackFn.call(fieldValue);
    }
    if (fieldDesc.getType() == FieldDescriptor.Type.BYTES) {
      return isArray
          ? GOOG_ARRAY_MAP.call(fieldValue, protoBytesPackToByteStringFunction())
          : protoBytesPackToByteStringFunction().call(fieldValue);
    }
    if (fieldDesc.getType() == FieldDescriptor.Type.ENUM && !isArray) {
      // TODO(b/255452370): no cast should be necessary, but soy eagerly desugars enum literals
      // into numeric literals which drops type information.
      return fieldValue.castAsUnknown();
    }
    return fieldValue;
  }

  protected Expression visitProtoInitFunction(FunctionNode node) {
    SoyProtoType type = (SoyProtoType) node.getType();
    if (node.numParams() == 0) {
      // special case
      return JsRuntime.emptyProto(type);
    }
    Expression proto = construct(protoConstructor(type));
    for (int i = 0; i < node.numParams(); i++) {
      String fieldName = node.getParamName(i).identifier();
      FieldDescriptor fieldDesc = type.getFieldDescriptor(fieldName);
      ExprNode child = node.getParam(i);
      if (fieldDesc.isMapField() && child.getKind() == ExprNode.Kind.MAP_LITERAL_NODE) {
        // use .put-er functions
        // This saves allocating a map.
        MapLiteralNode mapLiteral = (MapLiteralNode) child;
        // Trim 'Map' from the field name
        String fnName =
            "put"
                + LOWER_CAMEL.to(UPPER_CAMEL, fieldName.substring(0, fieldName.length() - 3))
                + ProtoUtils.getJsFieldSpecificSuffix(fieldDesc);
        for (int j = 0; j < mapLiteral.numChildren(); j += 2) {
          Expression key = visit(mapLiteral.getChild(j));
          Expression value =
              protoInitFieldValue(
                  ProtoUtils.getMapValueFieldDescriptor(fieldDesc),
                  visit(mapLiteral.getChild(j + 1)),
                  false);
          proto = proto.dotAccess(fnName).call(key, value);
        }
      } else if (fieldDesc.isRepeated()
          && !fieldDesc.isExtension()
          && child.getKind() == ExprNode.Kind.LIST_LITERAL_NODE) {
        // use .add-er functions
        // This saves allocating an array and makes later calls to toImmutable cheaper
        ListLiteralNode listLiteral = (ListLiteralNode) child;
        String fnName =
            "add"
                + LOWER_CAMEL.to(UPPER_CAMEL, fieldName.substring(0, fieldName.length() - 4))
                + ProtoUtils.getJsFieldSpecificSuffix(fieldDesc);
        for (int j = 0; j < listLiteral.numChildren(); j++) {
          proto =
              proto
                  .dotAccess(fnName)
                  .call(protoInitFieldValue(fieldDesc, visit(listLiteral.getChild(j)), true));
        }
      } else {
        Expression fieldValue = protoInitFieldValue(fieldDesc, visit(child), false);
        if (fieldDesc.isExtension()) {
          Expression extInfo = extensionField(fieldDesc);
          proto = proto.dotAccess("setExtension").call(extInfo, fieldValue);
        } else if (fieldDesc.isMapField()) {
          if (ProtoUtils.isSanitizedContentMap(fieldDesc)) {
            Expression sanitizedContentPackFn =
                sanitizedContentToProtoConverterFunction(
                    ProtoUtils.getMapValueMessageType(fieldDesc));
            fieldValue = SOY_NEWMAPS_TRANSFORM_VALUES.call(fieldValue, sanitizedContentPackFn);
          } else if (ProtoUtils.getMapValueFieldDescriptor(fieldDesc).getType()
              == FieldDescriptor.Type.BYTES) {
            fieldValue =
                SOY_NEWMAPS_TRANSFORM_VALUES.call(fieldValue, protoBytesPackToByteStringFunction());
          }
          proto =
              proto
                  .dotAccess(
                      "putAll"
                          + LOWER_CAMEL
                              .to(UPPER_CAMEL, fieldName)
                              .substring(0, fieldName.length() - 3)
                          + ProtoUtils.getJsFieldSpecificSuffix(fieldDesc))
                  .call(fieldValue);
        } else {
          String setFn =
              "set"
                  + LOWER_CAMEL.to(UPPER_CAMEL, fieldName)
                  + ProtoUtils.getJsFieldSpecificSuffix(fieldDesc);
          proto = proto.dotAccess(setFn).call(fieldValue);
        }
      }
    }

    return JsRuntime.castAsReadonlyProto(
        JsRuntime.SOY.dotAccess("$$maybeMakeImmutableProto").call(proto), type);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override
  protected Expression visitFunctionNode(FunctionNode node) {
    Object soyFunction = node.getSoyFunction();

    if (soyFunction instanceof BuiltinFunction) {
      switch ((BuiltinFunction) soyFunction) {
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node);
        case CSS:
          return visitCssFunction(node);
        case EVAL_TOGGLE:
          return visitToggleFunction(node, /* useGoogModuleSyntax= */ false);
        case XID:
          return visitXidFunction(node);
        case SOY_SERVER_KEY:
          return visitSoyServerKeyFunction(node);
        case PROTO_INIT:
          return visitProtoInitFunction(node);
        case UNKNOWN_JS_GLOBAL:
          return visitUnknownJsGlobal(node);
        case IS_PRIMARY_MSG_IN_USE:
          return visitIsPrimaryMsgInUseFunction(node);
        case TO_FLOAT:
          // this is a no-op in js
          return visit(node.getParam(0));
        case DEBUG_SOY_TEMPLATE_INFO:
          // TODO(lukes): does this need a goog.debug guard? it exists in the runtime
          return GOOG_DEBUG.and(
              JsRuntime.SOY_DEBUG_SOY_TEMPLATE_INFO.call(), translationContext.codeGenerator());
        case VE_DATA:
          return visitVeDataFunction(node);
        case VE_DEF:
          return visitVeDefFunction(node);
        case EMPTY_TO_NULL:
          return visitEmptyToNullFunction(node);
        case UNDEFINED_TO_NULL:
          return visit(node.getParam(0))
              .nullishCoalesce(LITERAL_NULL, translationContext.codeGenerator());
        case UNDEFINED_TO_NULL_SSR:
          // CSR no-op
          return visit(node.getParam(0));
        case BOOLEAN:
          return maybeCoerceToBoolean(
              // Always coerce regardless of the argument type.
              AnyType.getInstance(), visit(node.getParam(0)), /* force= */ true);
        case IS_TRUTHY_NON_EMPTY:
          return isTruthyNonEmpty(visit(node.getParam(0)));
        case HAS_CONTENT:
          return hasContent(visit(node.getParam(0)));
        case NEW_SET:
          return visitNewSetFunction(node);
        case LEGACY_DYNAMIC_TAG:
        case REMAINDER:
        case MSG_WITH_ID:
          // should have been removed earlier in the compiler
          throw new AssertionError();
      }
      throw new AssertionError();

    } else if (soyFunction instanceof LoggingFunction) {
      return stringLiteral(((LoggingFunction) soyFunction).getPlaceholder());
    } else if (soyFunction instanceof SoyJavaScriptSourceFunction) {
      return javascriptValueFactory.applyFunction(
          node.getSourceLocation(),
          node.getStaticFunctionName(),
          (SoyJavaScriptSourceFunction) soyFunction,
          visitParams(node),
          translationContext.codeGenerator());
    } else if (soyFunction instanceof ExternRef) {
      ExternRef ref = (ExternRef) soyFunction;
      if (!translationContext.soyToJsVariableMappings().has(ref.name())) {
        // This extern doesn't have a jsimpl. An error has already been reported, but compilation
        // hasn't been halted yet. Return a placeholder value to keep going until errors get
        // reported.
        return LITERAL_UNDEFINED;
      }
      return translationContext.soyToJsVariableMappings().get(ref.name()).call(visitParams(node));
    } else {
      if (!(soyFunction instanceof SoyJsSrcFunction)) {
        errorReporter.report(
            node.getSourceLocation(),
            SOY_JS_SRC_FUNCTION_NOT_FOUND,
            node.getStaticFunctionName(),
            soyFunction == null ? "missing implementation" : soyFunction.getClass().getName());
        // use a fake function and keep going
        soyFunction = getUnknownFunction(node.getStaticFunctionName(), node.numParams());
      }

      return SoyJsPluginUtils.applySoyFunction(
          (SoyJsSrcFunction) soyFunction,
          visitParams(node),
          node.getSourceLocation(),
          errorReporter);
    }
  }

  protected JsType jsTypeForStrict(SoyType type) {
    return JsType.forJsSrcStrict(type);
  }

  protected JsType jsTypeFor(SoyType type) {
    return JsType.forJsSrc(type);
  }

  private Expression visitCheckNotNullFunction(FunctionNode node) {
    return assertNonNull(node.getParam(0));
  }

  private Expression assertNonNull(ExprNode expr) {
    Expression e = visit(expr);
    return e.isDefinitelyNotNull() ? e : SOY_CHECK_NOT_NULL.call(e);
  }

  private Expression visitCssFunction(FunctionNode node) {
    return GOOG_GET_CSS_NAME.call(visitParams(node));
  }

  /** Built-in function for generating JS code to import toggles. */
  protected Expression visitToggleFunction(FunctionNode node, boolean useGoogModuleSyntax) {
    StringNode pathName = (StringNode) node.getChild(0);
    StringNode toggleName = (StringNode) node.getChild(1);
    return getToggleRef(pathName.getValue(), toggleName.getValue(), useGoogModuleSyntax);
  }

  private Expression visitXidFunction(FunctionNode node) {
    return XID.call(visitParams(node));
  }

  private Expression visitSoyServerKeyFunction(FunctionNode node) {
    return SERIALIZE_KEY.call(visit(node.getParam(0)));
  }

  private Expression visitIsPrimaryMsgInUseFunction(FunctionNode node) {
    // we need to find the msgfallbackgroupnode that we are referring to.  It is a bit tedious to
    // navigate the AST, but we know that all these checks will succeed because it is validated by
    // the MsgWithIdFunctionPass
    MsgFallbackGroupNode msgNode =
        (MsgFallbackGroupNode)
            ((LetContentNode)
                    ((LocalVar) ((VarRefNode) node.getParam(0)).getDefnDecl()).declaringNode())
                .getChild(0);

    return translationContext.soyToJsVariableMappings().isPrimaryMsgInUse(msgNode);
  }

  private Expression visitUnknownJsGlobal(FunctionNode node) {
    StringNode expr = (StringNode) node.getParam(0);
    return translationContext
        .codeGenerator()
        .declarationBuilder()
        .setRhs(Expressions.dottedIdNoRequire(expr.getValue()))
        .setJsDoc(JsDoc.builder().addParameterizedAnnotation("suppress", "missingRequire").build())
        .build()
        .ref();
  }

  private Expression visitVeDataFunction(FunctionNode node) {
    return construct(SOY_VISUAL_ELEMENT_DATA, visit(node.getParam(0)), visit(node.getParam(1)));
  }

  private Expression visitVeDefFunction(FunctionNode node) {
    Expression metadataExpr = node.numParams() == 4 ? visit(node.getParam(3)) : LITERAL_UNDEFINED;
    Expression debugNameExpr =
        Expressions.ifExpression(GOOG_DEBUG, visit(node.getParam(0)))
            .setElse(Expressions.LITERAL_UNDEFINED)
            .build(translationContext.codeGenerator());
    return construct(SOY_VISUAL_ELEMENT, visit(node.getParam(1)), metadataExpr, debugNameExpr);
  }

  protected Expression visitEmptyToNullFunction(FunctionNode node) {
    return SOY_EMPTY_TO_NULL.call(visit(node.getParam(0)));
  }

  private Expression visitNewSetFunction(FunctionNode node) {
    return Expressions.construct(id("Set"), visit(node.getParam(0)));
  }

  private static SoyJsSrcFunction getUnknownFunction(String name, int argSize) {
    return new SoyJsSrcFunction() {
      @Override
      public JsExpr computeForJsSrc(List<JsExpr> args) {
        List<String> argStrings = new ArrayList<>();
        for (JsExpr arg : args) {
          argStrings.add(arg.getText());
        }
        return new JsExpr(name + "(" + COMMA_JOINER.join(argStrings) + ")", Integer.MAX_VALUE);
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public ImmutableSet<Integer> getValidArgsSizes() {
        return ImmutableSet.of(argSize);
      }
    };
  }

  @Override
  protected Expression visitTemplateLiteralNode(TemplateLiteralNode node) {
    // TODO(b/80597216): remove the call to dottedIdNoRequire here by calculating the goog.require
    // this will require knowing the current require strategy and whether or not the template is
    // defined in this file.
    Expression templateLiteral =
        Expressions.dottedIdNoRequire(templateAliases.get(node.getResolvedName()));
    return MARK_TEMPLATE.call(templateLiteral, Expressions.stringLiteral(node.getResolvedName()));
  }

  private List<Expression> visitParams(CallableExpr call) {
    List<Expression> results = new ArrayList<>(call.numParams());
    for (ExprNode child : call.getParams()) {
      results.add(visit(child));
    }
    return results;
  }
}
