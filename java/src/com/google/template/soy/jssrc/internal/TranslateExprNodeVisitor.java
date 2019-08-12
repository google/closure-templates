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
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.Expression.arrayLiteral;
import static com.google.template.soy.jssrc.dsl.Expression.construct;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.not;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.dsl.Expression.operation;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ARRAY_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_DEBUG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_CSS_NAME;
import static com.google.template.soy.jssrc.internal.JsRuntime.JS_TO_PROTO_PACK_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_IJ_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_CHECK_NOT_NULL;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_EQUALS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_MAP_POPULATE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_NEWMAPS_TRANSFORM_VALUES;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_VISUAL_ELEMENT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoConstructor;
import static com.google.template.soy.passes.ContentSecurityPolicyNonceInjectionPass.CSP_NONCE_VARIABLE_NAME;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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
 *
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

  /**
   * The current replacement JS expressions for the local variables (and foreach-loop special
   * functions) current in scope.
   */
  private final SoyToJsVariableMappings variableMappings;

  private final JavaScriptValueFactoryImpl javascriptValueFactory;
  private final ErrorReporter errorReporter;
  private final CodeChunk.Generator codeGenerator;

  public TranslateExprNodeVisitor(
      JavaScriptValueFactoryImpl javascriptValueFactory,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {
    this.javascriptValueFactory = javascriptValueFactory;
    this.errorReporter = errorReporter;
    this.variableMappings = translationContext.soyToJsVariableMappings();
    this.codeGenerator = translationContext.codeGenerator();
  }

  /**
   * Method that returns code to access a named parameter.
   *
   * @param paramName the name of the parameter.
   * @param varDefn The variable definition of the parameter
   * @return The code to access the value of that parameter.
   */
  Expression genCodeForParamAccess(String paramName, VarDefn varDefn) {
    Expression source = OPT_DATA;
    if (varDefn.isInjected()) {
      // Special case for csp_nonce. It is created by the compiler itself, and users should not need
      // to set it. So, instead of generating opt_ij_data.csp_nonce, we generate opt_ij_data &&
      // opt_ij_data.csp_nonce.
      // TODO(lukes): we only need to generate this logic if there aren't any other ij params
      if (paramName.equals(CSP_NONCE_VARIABLE_NAME)) {
        return OPT_IJ_DATA.and(OPT_IJ_DATA.dotAccess(paramName), codeGenerator);
      }
      source = OPT_IJ_DATA;
    } else if (varDefn.kind() == VarDefn.Kind.STATE) {
      return genCodeForStateAccess(paramName, (TemplateStateVar) varDefn);
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

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected Expression visitListLiteralNode(ListLiteralNode node) {
    return arrayLiteral(visitChildren(node));
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
    Expression map =
        codeGenerator.declarationBuilder().setRhs(Expression.construct(id("Map"))).build().ref();
    ImmutableList.Builder<Statement> setCalls = ImmutableList.builder();
    for (int i = 0; i < node.numChildren(); i += 2) {
      ExprNode keyNode = node.getChild(i);
      // Constructing a map literal with a null key is a runtime error.
      Expression key = SOY_CHECK_NOT_NULL.call(genMapKeyCode(keyNode));
      Expression value = visit(node.getChild(i + 1));
      setCalls.add(map.dotAccess("set").call(key, value).asStatement());
    }
    return map.withInitialStatements(setCalls.build());
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
    } else {
      // Case 2: Data reference.
      return genCodeForParamAccess(node.getName(), node.getDefnDecl());
    }
  }

  @Override
  protected Expression visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node).result(codeGenerator);
  }

  /** Returns a function that can 'unpack' safe proto types into sanitized content types.. */
  protected Expression sanitizedContentToProtoConverterFunction(Descriptor messageType) {
    return JS_TO_PROTO_PACK_FN.get(messageType.getFullName());
  }

  /** See {@link NullSafeAccumulator} for discussion. */
  private NullSafeAccumulator visitNullSafeNode(ExprNode node) {
    switch (node.getKind()) {
      case FIELD_ACCESS_NODE:
        {
          FieldAccessNode fieldAccess = (FieldAccessNode) node;
          NullSafeAccumulator base = visitNullSafeNode(fieldAccess.getBaseExprChild());
          FieldAccess access =
              genCodeForFieldAccess(
                  fieldAccess.getBaseExprChild().getType(),
                  fieldAccess,
                  fieldAccess.getFieldName());
          return base.dotAccess(access, fieldAccess.isNullSafe());
        }

      case ITEM_ACCESS_NODE:
        {
          ItemAccessNode itemAccess = (ItemAccessNode) node;
          NullSafeAccumulator base = visitNullSafeNode(itemAccess.getBaseExprChild());
          ExprNode keyNode = itemAccess.getKeyExprChild();
          SoyType baseType = itemAccess.getBaseExprChild().getType();
          return SoyTypes.isKindOrUnionOfKind(SoyTypes.removeNull(baseType), Kind.MAP)
              ? base.mapGetAccess(genMapKeyCode(keyNode), itemAccess.isNullSafe()) // soy.Map
              : base.bracketAccess(
                  // The key type may not match JsCompiler's type system (passing number as enum, or
                  // nullable proto field).  I could instead cast this to the map's key type.
                  visit(keyNode).castAs("?"), itemAccess.isNullSafe()); // vanilla bracket access
        }

      default:
        return new NullSafeAccumulator(visit(node));
    }
  }

  /**
   * Generates the code for a field access, e.g. {@code .foo} or {@code .getFoo()}.
   *
   * @param baseType The type of the object that contains the field.
   * @param fieldAccessNode The field access node.
   * @param fieldName The field name.
   */
  private FieldAccess genCodeForFieldAccess(
      SoyType baseType, FieldAccessNode fieldAccessNode, String fieldName) {
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
              genCodeForFieldAccess(memberType, fieldAccessNode, fieldName);
          if (fieldAccess == null) {
            fieldAccess = fieldAccessForType;
          } else if (!fieldAccess.equals(fieldAccessForType)) {
            errorReporter.report(
                fieldAccessNode.getSourceLocation(), UNION_ACCESSOR_MISMATCH, fieldName, baseType);
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

  @Override
  protected Expression visitGlobalNode(GlobalNode node) {
    if (node.isResolved()) {
      return visit(node.getValue());
    }
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
    Expression lhChunk = Truthiness.maybeCoerce(lhOperand.getType(), visit(lhOperand));
    Expression rhChunk = Truthiness.maybeCoerce(rhOperand.getType(), visit(rhOperand));
    return lhChunk.and(rhChunk, codeGenerator);
  }

  @Override
  protected Expression visitOrOpNode(OrOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    ExprNode lhOperand = node.getChild(0);
    ExprNode rhOperand = node.getChild(1);
    // See comments in visitAndOpNode for why explicit coercions are required.
    Expression lhChunk = Truthiness.maybeCoerce(lhOperand.getType(), visit(lhOperand));
    Expression rhChunk = Truthiness.maybeCoerce(rhOperand.getType(), visit(rhOperand));
    return lhChunk.or(rhChunk, codeGenerator);
  }

  @Override
  protected Expression visitConditionalOpNode(ConditionalOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 3);
    return codeGenerator.conditionalExpression(
        visit(node.getChild(0)), visit(node.getChild(1)), visit(node.getChild(2)));
  }

  @Override
  protected Expression visitOperatorNode(OperatorNode node) {
    return operation(node.getOperator(), visitChildren(node));
  }

  private Expression visitEqualNodeHelper(OperatorNode node) {
    for (ExprNode c : node.getChildren()) {
      SoyType type = c.getType();
      // A runtime directive needs to be used if operands are anything but booleans and
      // numbers.
      if ((!SoyTypes.isNumericPrimitive(type) && type.getKind() != SoyType.Kind.BOOL)
          || type.getKind() == SoyType.Kind.UNKNOWN
          || type.getKind() == SoyType.Kind.ANY) {
        return SOY_EQUALS.call(visitChildren(node));
      }
    }

    return operation(Operator.EQUAL, visitChildren(node));
  }

  @Override
  protected Expression visitEqualOpNode(EqualOpNode node) {
    return visitEqualNodeHelper(node);
  }

  @Override
  protected Expression visitNotEqualOpNode(NotEqualOpNode node) {
    return not(visitEqualNodeHelper(node));
  }

  @Override
  protected Expression visitProtoInitNode(ProtoInitNode node) {
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

      if (fieldDesc.getType() == FieldDescriptor.Type.ENUM && !fieldDesc.isRepeated()) {
        // At runtime, this may be null, but we can't tell if this is a non-nullable proto3 field.
        fieldValue =
            fieldValue.castAs(
                (fieldDesc.getFile().getSyntax() == Syntax.PROTO3 ? "!" : "?")
                    + ProtoUtils.calculateJsEnumName(fieldDesc.getEnumType()));
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
        }
        proto = SOY_MAP_POPULATE.call(protoVar, protoVar.dotAccess(getFn).call(), fieldValue);
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
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node);
        case CSS:
          return visitCssFunction(node);
        case XID:
          return visitXidFunction(node);
        case UNKNOWN_JS_GLOBAL:
          return visitUnknownJsGlobal(node);
        case V1_EXPRESSION:
          return visitV1ExpressionFunction(node);
        case IS_PRIMARY_MSG_IN_USE:
          return visitIsPrimaryMsgInUseFunction(node);
        case TO_FLOAT:
          // this is a no-op in js
          return visit(node.getChild(0));
        case DEBUG_SOY_TEMPLATE_INFO:
          return GOOG_DEBUG.and(JsRuntime.SOY_DEBUG_SOY_TEMPLATE_INFO, codeGenerator);
        case VE_DATA:
          return visitVeDataFunction(node);
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
          node.getFunctionName(),
          (SoyJavaScriptSourceFunction) soyFunction,
          visitChildren(node),
          codeGenerator);
    } else {
      if (!(soyFunction instanceof SoyJsSrcFunction)) {
        errorReporter.report(
            node.getSourceLocation(),
            SOY_JS_SRC_FUNCTION_NOT_FOUND,
            node.getFunctionName(),
            soyFunction == null ? "missing implementation" : soyFunction.getClass().getName());
        // use a fake function and keep going
        soyFunction = getUnknownFunction(node.getFunctionName(), node.numChildren());
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
    return SOY_CHECK_NOT_NULL
        .call(visit(node.getChild(0)))
        // It is impossible to make a Closure template function that takes T|null and returns T.  To
        // avoid JSCompiler errors when passing checkNotNull to a function that doesn't accept null,
        // we manually cast away the nullness.
        .castAs(jsTypeFor(SoyTypes.tryRemoveNull(node.getChild(0).getType())).typeExpr());
  }

  private Expression visitIsFirstFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__isFirst");
  }

  private Expression visitIsLastFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__isLast");
  }

  private Expression visitIndexFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__index");
  }

  private Expression visitCssFunction(FunctionNode node) {
    return GOOG_GET_CSS_NAME.call(visitChildren(node));
  }

  private Expression visitXidFunction(FunctionNode node) {
    return XID.call(visitChildren(node));
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

  private Expression visitV1ExpressionFunction(FunctionNode node) {
    StringNode expr = (StringNode) node.getChild(0);
    JsExpr jsExpr =
        V1JsExprTranslator.translateToJsExpr(
            expr.getValue(), expr.getSourceLocation(), variableMappings, errorReporter);
    return Expression.fromExpr(jsExpr, ImmutableList.of());
  }

  private Expression visitUnknownJsGlobal(FunctionNode node) {
    StringNode expr = (StringNode) node.getChild(0);
    return codeGenerator
        .declarationBuilder()
        .setRhs(Expression.dottedIdNoRequire(expr.getValue()))
        .setJsDoc(JsDoc.builder().addParameterizedAnnotation("suppress", "missingRequire").build())
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
      public Set<Integer> getValidArgsSizes() {
        return ImmutableSet.of(argSize);
      }
    };
  }

  @Override
  protected Expression visitVeLiteralNode(VeLiteralNode node) {
    return Expression.ifExpression(
            GOOG_DEBUG,
            construct(
                SOY_VISUAL_ELEMENT,
                Expression.number(node.getId()),
                Expression.stringLiteral(node.getName().identifier())))
        .setElse(construct(SOY_VISUAL_ELEMENT, Expression.number(node.getId())))
        .build(codeGenerator);
  }
}
