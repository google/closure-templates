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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.Expression.arrowFunction;
import static com.google.template.soy.jssrc.dsl.Expression.construct;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.not;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.dsl.Expression.operation;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
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
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_EQUALS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_FILTER_AND_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAKE_ARRAY;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAP_POPULATE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_NEWMAPS_TRANSFORM_VALUES;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoBytesPackToByteStringFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoConstructor;
import static com.google.template.soy.passes.ContentSecurityPolicyNonceInjectionPass.CSP_NONCE_VARIABLE_NAME;
import static com.google.template.soy.passes.ContentSecurityPolicyNonceInjectionPass.CSP_STYLE_NONCE_VARIABLE_NAME;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.ProtoCall;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
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
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

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
 *       recognizing input coercion, for example between {@code goog.html.type.SafeHtml} and Soy's
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
  private final SoyToJsVariableMappings variableMappings;

  private final JavaScriptValueFactoryImpl javascriptValueFactory;
  private final ErrorReporter errorReporter;
  private final CodeChunk.Generator codeGenerator;
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
    this.variableMappings = translationContext.soyToJsVariableMappings();
    this.codeGenerator = translationContext.codeGenerator();
    this.templateAliases = templateAliases;
    this.dataSource = dataSource;
  }

  public Expression getDataSource() {
    return dataSource;
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
    if (varDefn.isInjected()) {
      // Special case for csp_nonce. It is created by the compiler itself, and users should not need
      // to set it. So, instead of generating opt_ij_data.csp_nonce, we generate opt_ij_data &&
      // opt_ij_data.csp_nonce.
      // TODO(lukes): we only need to generate this logic if there aren't any other ij params
      if (paramName.equals(CSP_NONCE_VARIABLE_NAME)
          || paramName.equals(CSP_STYLE_NONCE_VARIABLE_NAME)) {
        return IJ_DATA.and(IJ_DATA.dotAccess(paramName), codeGenerator);
      }
      source = IJ_DATA;
    } else if (varDefn.kind() == VarDefn.Kind.STATE) {
      return genCodeForStateAccess(paramName, (TemplateStateVar) varDefn);
    }
    if (varDefn instanceof TemplateParam && ((TemplateParam) varDefn).isImplicit()) {
      // implicit params are not in the type declaration for the opt_data parameter, so we need to
      // cast as ? to access implicit params
      source = source.castAs("?");
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
    return Expression.id(paramName);
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
      return Expression.arrayLiteral(ImmutableList.of());
    }
    return SOY_MAKE_ARRAY.call(visitChildren(node));
  }

  @Override
  protected Expression visitListComprehensionNode(ListComprehensionNode node) {
    Expression base = visit(node.getListExpr());
    String listIterVarTranslation =
        "list_comp_" + node.getNodeId() + "_" + node.getListIterVar().name();
    variableMappings.put(node.getListIterVar(), id(listIterVarTranslation));
    String indexVarTranslation =
        node.getIndexVar() == null
            ? null
            : "list_comp_" + node.getNodeId() + "_" + node.getIndexVar().name();
    if (node.getIndexVar() != null) {
      variableMappings.put(node.getIndexVar(), id(indexVarTranslation));
    }
    SoyType listType = SoyTypes.tryRemoveNull(node.getListExpr().getType());
    SoyType elementType =
        listType.getKind() == SoyType.Kind.LIST ? ((ListType) listType).getElementType() : null;
    // elementType can be null if it is the special EMPTY_LIST or if it isn't a known list type.
    elementType = elementType == null ? UnknownType.getInstance() : elementType;
    JsDoc doc =
        node.getIndexVar() == null
            ? JsDoc.builder()
                .addParam(listIterVarTranslation, jsTypeFor(elementType).typeExpr())
                .build()
            : JsDoc.builder()
                .addParam(listIterVarTranslation, jsTypeFor(elementType).typeExpr())
                .addParam(indexVarTranslation, "number")
                .build();

    Expression filterAndIndexBase = null;
    if (node.getFilterExpr() != null && node.getIndexVar() != null) {
      filterAndIndexBase =
          SOY_FILTER_AND_MAP.call(
              base,
              arrowFunction(
                  doc,
                  maybeCoerceToBoolean(
                      node.getFilterExpr().getType(),
                      visit(node.getFilterExpr()),
                      /*force=*/ false)),
              arrowFunction(doc, visit(node.getListItemTransformExpr())));
    }
    if (node.getFilterExpr() != null) {
      base =
          base.dotAccess("filter")
              .call(
                  arrowFunction(
                      doc,
                      maybeCoerceToBoolean(
                          node.getFilterExpr().getType(),
                          visit(node.getFilterExpr()),
                          /*force=*/ false)));
    }
    // handle a special case for trivial transformations
    if (node.getListItemTransformExpr().getKind() == ExprNode.Kind.VAR_REF_NODE) {
      VarRefNode transformNode = (VarRefNode) node.getListItemTransformExpr();
      if (transformNode.getName().equals(node.getListIterVar().refName())) {
        return base;
      }
    }
    base = base.dotAccess("map").call(arrowFunction(doc, visit(node.getListItemTransformExpr())));
    return filterAndIndexBase == null ? base : filterAndIndexBase;
  }

  @Override
  protected Expression visitRecordLiteralNode(RecordLiteralNode node) {
    LinkedHashMap<String, Expression> objLiteral = new LinkedHashMap<>();

    // Process children
    for (int i = 0; i < node.numChildren(); i++) {
      objLiteral.put(node.getKey(i).identifier(), visit(node.getChild(i)));
    }

    // Build the record literal
    return Expression.objectLiteral(objLiteral);
  }

  @Override
  protected Expression visitMapLiteralNode(MapLiteralNode node) {
    Expression map = Expression.constructMap();
    if (node.getType() != MapType.EMPTY_MAP) {
      map = map.castAs(JsType.forJsSrc(node.getType()).typeExpr());
    }

    for (int i = 0; i < node.numChildren(); i += 2) {
      ExprNode keyNode = node.getChild(i);
      // Constructing a map literal with a null key is a runtime error.
      Expression key = SOY_CHECK_NOT_NULL.call(genMapKeyCode(keyNode));
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
      return Expression.constructMap();
    }
    if (listType.equals(ListType.EMPTY_LIST)) {
      // If the list is empty, trying to infer the type of the dummyVar is futile. So we create a
      // special case and directly return an empty list.
      return Expression.constructMap();
    }

    String dummyVar = "list_to_map_constructor_" + node.getNodeId();
    JsDoc doc =
        JsDoc.builder()
            .addParam(dummyVar, jsTypeFor(((ListType) listType).getElementType()).typeExpr())
            .build();

    Expression body =
        Expression.arrayLiteral(
            Arrays.asList(
                id(dummyVar).dotAccess(MapLiteralFromListNode.KEY_STRING),
                id(dummyVar).dotAccess(MapLiteralFromListNode.VALUE_STRING)));

    Expression nestedList = list.dotAccess("map").call(arrowFunction(doc, body));
    return Expression.constructMap(nestedList);
  }

  private Expression genMapKeyCode(ExprNode keyNode) {
    return visit(keyNode);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected Expression visitVarRefNode(VarRefNode node) {
    Expression translation = variableMappings.maybeGet(node.getName());
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
    return accumulateDataAccess(node).result(codeGenerator);
  }

  @Override
  protected Expression visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
    NullSafeAccumulator accumulator = new NullSafeAccumulator(visit(nullSafeAccessNode.getBase()));
    ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
    while (dataAccess.getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
      NullSafeAccessNode nullSafe = (NullSafeAccessNode) dataAccess;
      accumulator =
          accumulateNullSafeDataAccess(
              (DataAccessNode) nullSafe.getBase(), accumulator, /* assertNonNull= */ false);
      dataAccess = nullSafe.getDataAccess();
    }
    return accumulateNullSafeDataAccessTail((AccessChainComponentNode) dataAccess, accumulator)
        .result(codeGenerator);
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  protected Expression sanitizedContentToProtoConverterFunction(Descriptor messageType) {
    return JS_TO_PROTO_PACK_FN.get(messageType.getFullName());
  }

  private NullSafeAccumulator accumulateNullSafeDataAccessTail(
      AccessChainComponentNode dataAccess, NullSafeAccumulator accumulator) {
    boolean foundAssertNonNull = false;
    if (dataAccess.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
      AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccess;
      dataAccess = (AccessChainComponentNode) assertNonNull.getChild(0);
      foundAssertNonNull = true;
    }
    return accumulateNullSafeDataAccess(
        (DataAccessNode) dataAccess, accumulator, foundAssertNonNull);
  }

  private NullSafeAccumulator accumulateNullSafeDataAccess(
      DataAccessNode dataAccessNode, NullSafeAccumulator accumulator, boolean assertNonNull) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!dataAccessNode.isNullSafe());
    boolean accessChain = false;
    if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
      // This is an access chain (e.g. $foo.bar.baz), so recurse through the base expression
      // ($foo.bar) first.
      accumulator =
          accumulateNullSafeDataAccess(
              (DataAccessNode) dataAccessNode.getBaseExprChild(),
              accumulator,
              /* assertNonNull= */ false);
      accessChain = true;
    }
    return accumulateDataAccess(dataAccessNode, accumulator, !accessChain, assertNonNull);
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
    return accumulateDataAccess(
        dataAccessNode, accumulator, /* nullSafe= */ false, /* assertNonNull= */ false);
  }

  private NullSafeAccumulator accumulateDataAccess(
      DataAccessNode dataAccessNode,
      NullSafeAccumulator accumulator,
      boolean nullSafe,
      boolean assertNonNull) {
    switch (dataAccessNode.getKind()) {
      case FIELD_ACCESS_NODE:
        FieldAccessNode fieldAccess = (FieldAccessNode) dataAccessNode;
        FieldAccess access =
            genCodeForFieldAccess(
                fieldAccess.getBaseExprChild().getType(),
                fieldAccess.getAccessSourceLocation(),
                fieldAccess.getFieldName());
        return accumulator.dotAccess(access, nullSafe, assertNonNull);
      case ITEM_ACCESS_NODE:
        ItemAccessNode itemAccess = (ItemAccessNode) dataAccessNode;
        ExprNode keyNode = itemAccess.getKeyExprChild();
        SoyType baseType = itemAccess.getBaseExprChild().getType();
        return SoyTypes.isKindOrUnionOfKind(SoyTypes.removeNull(baseType), Kind.MAP) // soy.Map
            ? accumulator.mapGetAccess(genMapKeyCode(keyNode), nullSafe, assertNonNull)
            : accumulator.bracketAccess(
                // The key type may not match JsCompiler's type system (passing number as enum, or
                // nullable proto field).  I could instead cast this to the map's key type.
                visit(keyNode).castAs("?"), nullSafe, assertNonNull); // vanilla bracket access
      case METHOD_CALL_NODE:
        MethodCallNode methodCall = (MethodCallNode) dataAccessNode;
        return genCodeForMethodCall(accumulator, methodCall, nullSafe, assertNonNull);
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
    // For unions, attempt to generate the field access code for each member
    // type, and then see if they all agree.
    if (baseType.getKind() == SoyType.Kind.UNION) {
      // TODO(msamuel): We will need to generate fallback code for each variant.
      UnionType unionType = (UnionType) baseType;
      FieldAccess fieldAccess = null;
      for (SoyType memberType : unionType.getMembers()) {
        if (memberType.getKind() != SoyType.Kind.NULL) {
          FieldAccess fieldAccessForType =
              genCodeForFieldAccess(memberType, sourceLocation, fieldName);
          if (fieldAccess == null) {
            fieldAccess = fieldAccessForType;
          } else if (!fieldAccess.equals(fieldAccessForType)) {
            errorReporter.report(sourceLocation, UNION_ACCESSOR_MISMATCH, fieldName, baseType);
          }
        }
      }
      return fieldAccess;
    }

    if (baseType.getKind() == SoyType.Kind.PROTO) {
      SoyProtoType protoType = (SoyProtoType) baseType;
      FieldDescriptor desc = protoType.getFieldDescriptor(fieldName);
      Preconditions.checkNotNull(
          desc,
          "Error in proto %s, field not found: %s",
          protoType.getDescriptor().getFullName(),
          fieldName);
      return FieldAccess.protoCall(fieldName, desc);
    }

    return FieldAccess.id(fieldName);
  }

  /**
   * Generates the code for a method, e.g. {@code .getExtension(foo)}}.
   *
   * @param base The base expression of the method.
   * @param methodCallNode The method call node.
   */
  private NullSafeAccumulator genCodeForMethodCall(
      NullSafeAccumulator base,
      MethodCallNode methodCallNode,
      boolean nullSafe,
      boolean assertNonNull) {
    Preconditions.checkArgument(methodCallNode.isMethodResolved());

    SoyMethod soyMethod = methodCallNode.getSoyMethod();
    if (soyMethod instanceof BuiltinMethod) {
      BuiltinMethod builtinMethod = (BuiltinMethod) soyMethod;
      SoyType baseType = methodCallNode.getBaseType(nullSafe);
      switch (builtinMethod) {
        case GET_EXTENSION:
          String extName = BuiltinMethod.getProtoExtensionIdFromMethodCall(methodCallNode);
          return base.dotAccess(
              ProtoCall.getField(extName, ((SoyProtoType) baseType).getFieldDescriptor(extName)),
              nullSafe,
              assertNonNull);
        case HAS_PROTO_FIELD:
          String fieldName = BuiltinMethod.getProtoFieldNameFromMethodCall(methodCallNode);
          return base.dotAccess(
              ProtoCall.hasField(
                  fieldName, ((SoyProtoType) baseType).getFieldDescriptor(fieldName)),
              nullSafe,
              assertNonNull);
          // When adding new built-in methods it may be necessary to assert that the base expression
          // is not null in order to prevent a method call on a null instance from ever succeeding.
        case BIND:
          return base.functionCall(
              nullSafe,
              assertNonNull,
              (baseExpr) ->
                  genCodeForBind(baseExpr, visit(methodCallNode.getParams().get(0)), baseType));
      }
      throw new AssertionError(builtinMethod);
    } else if (soyMethod instanceof SoySourceFunctionMethod) {
      SoySourceFunctionMethod sourceMethod = (SoySourceFunctionMethod) soyMethod;

      return base.functionCall(
          nullSafe,
          assertNonNull,
          baseExpr -> {
            List<Expression> args = new ArrayList<>();
            args.add(baseExpr);
            methodCallNode.getParams().forEach(n -> args.add(visit(n)));
            return javascriptValueFactory.applyFunction(
                methodCallNode.getSourceLocation(),
                methodCallNode.getMethodName().identifier(),
                (SoyJavaScriptSourceFunction) sourceMethod.getImpl(),
                args,
                codeGenerator);
          });
    } else {
      throw new AssertionError(soyMethod.getClass());
    }
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
    return Expression.dottedIdNoRequire(node.getName());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected Expression visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    List<Expression> operands = visitChildren(node);
    Expression consequent = operands.get(0);
    Expression alternate = operands.get(1);
    // if the consequent isn't trivial we should store the intermediate result in a new temporary
    if (!consequent.isCheap()) {
      consequent = codeGenerator.declarationBuilder().setRhs(consequent).build().ref();
    }
    return Expression.ifExpression(consequent.doubleNotEquals(Expression.LITERAL_NULL), consequent)
        .setElse(alternate)
        .build(codeGenerator);
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
    return lhChunk.and(rhChunk, codeGenerator);
  }

  @Override
  protected Expression visitOrOpNode(OrOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    ExprNode lhOperand = node.getChild(0);
    ExprNode rhOperand = node.getChild(1);
    // See comments in visitAndOpNode for why explicit coercions are required.
    Expression lhChunk = maybeCoerceToBoolean(lhOperand.getType(), visit(lhOperand), true);
    Expression rhChunk = maybeCoerceToBoolean(rhOperand.getType(), visit(rhOperand), true);
    return lhChunk.or(rhChunk, codeGenerator);
  }

  @Override
  protected Expression visitConditionalOpNode(ConditionalOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 3);
    return codeGenerator.conditionalExpression(
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
    // TODO(lukes): we can have smarter logic here,  most types have trivial boolean coercions
    // we only need to be careful about ?,any and the sanitized types
    if (force && type.getKind() != Kind.BOOL) {
      return SOY_COERCE_TO_BOOLEAN.call(chunk);
    }
    return chunk;
  }

  @Override
  protected Expression visitOperatorNode(OperatorNode node) {
    return operation(node.getOperator(), visitChildren(node));
  }

  private static final ImmutableSet<SoyType.Kind> CAN_USE_EQUALS =
      Sets.immutableEnumSet(
          SoyType.Kind.INT, SoyType.Kind.FLOAT, SoyType.Kind.PROTO_ENUM, Kind.BOOL, Kind.STRING);

  private Expression visitEqualNodeHelper(OperatorNode node, Operator eq) {
    boolean needsSoyEquals = false;
    boolean neverSoyEquals = false;

    for (ExprNode c : node.getChildren()) {
      SoyType type = c.getType();
      if (type.getKind() == SoyType.Kind.NULL) {
        // If either operand is null always use ===.
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
    return assertNonNull(node.getChild(0));
  }

  protected Expression visitProtoInitFunction(FunctionNode node) {
    SoyProtoType type = (SoyProtoType) node.getType();
    Expression proto = construct(protoConstructor(type));
    for (int i = 0; i < node.numChildren(); i++) {
      String fieldName = node.getParamName(i).identifier();
      FieldDescriptor fieldDesc = type.getFieldDescriptor(fieldName);
      Expression fieldValue = visit(node.getChild(i));
      if (ProtoUtils.isSanitizedContentField(fieldDesc)) {
        Expression sanitizedContentPackFn =
            sanitizedContentToProtoConverterFunction(fieldDesc.getMessageType());
        fieldValue =
            fieldDesc.isRepeated()
                ? GOOG_ARRAY_MAP.call(fieldValue, sanitizedContentPackFn)
                : sanitizedContentPackFn.call(fieldValue);
      }
      if (fieldDesc.getType() == FieldDescriptor.Type.BYTES) {
        fieldValue =
            fieldDesc.isRepeated()
                ? GOOG_ARRAY_MAP.call(fieldValue, protoBytesPackToByteStringFunction())
                : protoBytesPackToByteStringFunction().call(fieldValue);
      }
      if (fieldDesc.getType() == FieldDescriptor.Type.ENUM && !fieldDesc.isRepeated()) {
        fieldValue =
            fieldValue.castAs("!" + ProtoUtils.calculateJsEnumName(fieldDesc.getEnumType()));
      }

      if (fieldDesc.isExtension()) {
        Expression extInfo = extensionField(fieldDesc);
        proto = proto.dotAccess("setExtension").call(extInfo, fieldValue);
      } else if (fieldDesc.isMapField()) {
        // Protocol buffer in JS does not generate setters for map fields. To construct a proto map
        // field, we first save a reference to the empty instance using the getter,  and then load
        // it with the contents of the SoyMap.
        String getFn = "get" + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
        Expression protoVar = codeGenerator.declarationBuilder().setRhs(proto).build().ref();
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
        // JSCompiler cannot infer that jspb.Map and soy.Map or Map are the same.
        proto =
            SOY_MAP_POPULATE.call(
                protoVar,
                protoVar.dotAccess(getFn).call().castAs("!soy.map.Map<?,?>"),
                fieldValue.castAs("!soy.map.Map<?,?>"));
      } else {
        String setFn = "set" + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
        proto = proto.dotAccess(setFn).call(fieldValue);
      }
    }

    return proto;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override
  protected Expression visitFunctionNode(FunctionNode node) {
    Object soyFunction = node.getSoyFunction();

    if (soyFunction instanceof BuiltinFunction) {
      switch ((BuiltinFunction) soyFunction) {
        case IS_PARAM_SET:
          return visitIsSetFunction(node);
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node);
        case CSS:
          return visitCssFunction(node);
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
          return visit(node.getChild(0));
        case DEBUG_SOY_TEMPLATE_INFO:
          // TODO(lukes): does this need a goog.debug guard? it exists in the runtime
          return GOOG_DEBUG.and(JsRuntime.SOY_DEBUG_SOY_TEMPLATE_INFO.call(), codeGenerator);
        case VE_DATA:
          return visitVeDataFunction(node);
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
          visitChildren(node),
          codeGenerator);
    } else if (soyFunction instanceof ExternRef) {
      ExternRef ref = (ExternRef) soyFunction;
      return variableMappings.get(ref.name()).call(visitChildren(node));
    } else {
      if (!(soyFunction instanceof SoyJsSrcFunction)) {
        errorReporter.report(
            node.getSourceLocation(),
            SOY_JS_SRC_FUNCTION_NOT_FOUND,
            node.getStaticFunctionName(),
            soyFunction == null ? "missing implementation" : soyFunction.getClass().getName());
        // use a fake function and keep going
        soyFunction = getUnknownFunction(node.getStaticFunctionName(), node.numChildren());
      }

      return SoyJsPluginUtils.applySoyFunction(
          (SoyJsSrcFunction) soyFunction,
          visitChildren(node),
          node.getSourceLocation(),
          errorReporter);
    }
  }

  protected JsType jsTypeFor(SoyType type) {
    return JsType.forJsSrcStrict(type);
  }

  private Expression visitCheckNotNullFunction(FunctionNode node) {
    return assertNonNull(node.getChild(0));
  }

  private Expression assertNonNull(ExprNode expr) {
    return SOY_CHECK_NOT_NULL
        .call(visit(expr))
        // It is impossible to make a Closure template function that takes T|null and returns T.  To
        // avoid JSCompiler errors when passing checkNotNull to a function that doesn't accept null,
        // we manually cast away the nullness.
        .castAs(jsTypeFor(SoyTypes.tryRemoveNull(expr.getType())).typeExpr());
  }

  private Expression visitIsSetFunction(FunctionNode node) {
    Expression expression = visit(node.getChild(0));
    return expression.tripleNotEquals(Expression.LITERAL_UNDEFINED);
  }

  private Expression visitCssFunction(FunctionNode node) {
    return GOOG_GET_CSS_NAME.call(visitChildren(node));
  }

  private Expression visitXidFunction(FunctionNode node) {
    return XID.call(visitChildren(node));
  }

  private Expression visitSoyServerKeyFunction(FunctionNode node) {
    return SERIALIZE_KEY.call(visit(node.getChildren().get(0)));
  }

  private Expression visitIsPrimaryMsgInUseFunction(FunctionNode node) {
    // we need to find the msgfallbackgroupnode that we are referring to.  It is a bit tedious to
    // navigate the AST, but we know that all these checks will succeed because it is validated by
    // the MsgWithIdFunctionPass
    MsgFallbackGroupNode msgNode =
        (MsgFallbackGroupNode)
            ((LetContentNode)
                    ((LocalVar) ((VarRefNode) node.getChild(0)).getDefnDecl()).declaringNode())
                .getChild(0);

    return variableMappings.isPrimaryMsgInUse(msgNode);
  }

  private Expression visitUnknownJsGlobal(FunctionNode node) {
    StringNode expr = (StringNode) node.getChild(0);
    return codeGenerator
        .declarationBuilder()
        .setRhs(Expression.dottedIdNoRequire(expr.getValue()))
        .setJsDoc(
            JsDoc.builder()
                // Unknown globals are not type safe by definition and the feature is deprecated.
                // suppress everything.
                .addParameterizedAnnotation(
                    "suppress",
                    "missingRequire,undefinedNames,undefinedVars,strictMissingProperties")
                .build())
        .build()
        .ref();
  }

  private Expression visitVeDataFunction(FunctionNode node) {
    return construct(SOY_VISUAL_ELEMENT_DATA, visit(node.getChild(0)), visit(node.getChild(1)));
  }

  private static SoyJsSrcFunction getUnknownFunction(final String name, final int argSize) {
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
  protected Expression visitVeLiteralNode(VeLiteralNode node) {
    ValidatedLoggableElement element = node.getLoggableElement();
    Expression metadata;
    if (element.hasMetadata()) {
      metadata =
          GoogRequire.create(element.getJsPackage())
              .googModuleGet()
              .dotAccess(element.getClassName())
              .dotAccess(element.getGeneratedVeMetadataMethodName())
              .call();
    } else {
      metadata = Expression.LITERAL_UNDEFINED;
    }
    return Expression.ifExpression(
            GOOG_DEBUG,
            construct(
                SOY_VISUAL_ELEMENT,
                Expression.number(node.getId()),
                metadata,
                Expression.stringLiteral(node.getName().identifier())))
        .setElse(construct(SOY_VISUAL_ELEMENT, Expression.number(node.getId()), metadata))
        .build(codeGenerator);
  }

  @Override
  protected Expression visitTemplateLiteralNode(TemplateLiteralNode node) {
    // TODO(b/80597216): remove the call to dottedIdNoRequire here by calculating the goog.require
    // this will require knowing the current require strategy and whether or not the template is
    // defined in this file.
    Expression templateLiteral =
        Expression.dottedIdNoRequire(templateAliases.get(node.getResolvedName()));
    return MARK_TEMPLATE.call(templateLiteral, Expression.stringLiteral(node.getResolvedName()));
  }
}
