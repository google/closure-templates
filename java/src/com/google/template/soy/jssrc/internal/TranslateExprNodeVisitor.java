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
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.CodeChunk.arrayLiteral;
import static com.google.template.soy.jssrc.dsl.CodeChunk.dontTrustPrecedenceOf;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.mapLiteral;
import static com.google.template.soy.jssrc.dsl.CodeChunk.new_;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;
import static com.google.template.soy.jssrc.dsl.CodeChunk.operation;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ARRAY_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.OPT_IJ_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_CHECK_MAP_KEY;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_CHECK_NOT_NULL;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoConstructor;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoToSanitizedContentConverterFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentToProtoConverterFunction;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
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
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.dsl.CodeChunk.WithValue;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.proto.Protos;
import com.google.template.soy.types.proto.SoyProtoType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent chunk of JavaScript code.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <hr>
 *
 * <h3>Types and Dependencies</h3>
 *
 * Types are used to allow reflective access to protobuf values even after JSCompiler has
 * rewritten field names.
 *
 * <p>
 * For example, one might normally access field foo on a protocol buffer by calling
 *    <pre>my_pb.getFoo()</pre>
 * A Soy author can access the same by writing
 *    <pre>{$my_pb.foo}</pre>
 * But the relationship between "foo" and "getFoo" is not preserved by JSCompiler's renamer.
 *
 * <p>
 * To avoid adding many spurious dependencies on all protocol buffers compiled with a Soy
 * template, we make type-unsound (see CAVEAT below) assumptions:
 * <ul>
 *   <li>That the top-level inputs, opt_data and opt_ijData, do not need conversion.
 *   <li>That we can enumerate the concrete types of a container when the default
 *     field lookup strategy would fail.  For example, if an instance of {@code my.Proto}
 *     can be passed to the param {@code $my_pb}, then {@code $my_pb}'s static type is a
 *     super-type of {@code my.Proto}.</li>
 *   <li>That the template contains enough information to determine types that need to be
 *     converted.
 *     <br>
 *     Pluggable {@link com.google.template.soy.types.SoyTypeRegistry SoyTypeRegistries}
 *     allow recognizing input coercion, for example between {@code goog.html.type.SafeHtml}
 *     and Soy's {@code html} string sub-type.
 *     <br>
 *     When the converted type is a protocol-buffer type, we assume that the expression to be
 *     converted can be fully-typed by expressionTypesVisitor.
 * </ul>
 *
 * <p>
 * CAVEAT: These assumptions are unsound, but necessary to be able to deploy JavaScript
 * binaries of acceptable size.
 * <p>
 * Type-failures are correctness issues but do not lead to increased exposure to XSS or
 * otherwise compromise security or privacy since a failure to unpack a type leads to a
 * value that coerces to a trivial value like {@code undefined} or {@code "[Object]"}.
 * </p>
 *
 */
public class TranslateExprNodeVisitor
    extends AbstractReturningExprNodeVisitor<CodeChunk.WithValue> {

  private static final SoyErrorKind CONSTANT_USED_AS_KEY_IN_MAP_LITERAL =
      SoyErrorKind.of("Keys in map literals cannot be constants (found constant ''{0}'').");
  private static final SoyErrorKind EXPR_IN_MAP_LITERAL_REQUIRES_QUOTE_KEYS_IF_JS =
      SoyErrorKind.of("Expression key ''{0}'' in map literal must be wrapped in quoteKeysIfJs().");
  private static final SoyErrorKind MAP_LITERAL_WITH_NON_ID_KEY_REQUIRES_QUOTE_KEYS_IF_JS =
      SoyErrorKind.of(
          "Map literal with non-identifier key {0} must be wrapped in quoteKeysIfJs().");
  private static final SoyErrorKind SOY_JS_SRC_FUNCTION_NOT_FOUND =
      SoyErrorKind.of("Failed to find SoyJsSrcFunction ''{0}''.");
  private static final SoyErrorKind UNION_ACCESSOR_MISMATCH =
      SoyErrorKind.of(
          "Cannot access field ''{0}'' of type ''{1}'', "
              + "because the different union member types have different access methods.");

  /**
   * Injectable factory for creating an instance of this class.
   */
  public interface TranslateExprNodeVisitorFactory {
    TranslateExprNodeVisitor create(
        TranslationContext translationContext, ErrorReporter errorReporter);
  }

  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /**
   * The current replacement JS expressions for the local variables (and foreach-loop special
   * functions) current in scope.
   */
  private final SoyToJsVariableMappings variableMappings;
  private final ErrorReporter errorReporter;
  private final CodeChunk.Generator codeGenerator;

  @AssistedInject
  TranslateExprNodeVisitor(
      SoyJsSrcOptions jsSrcOptions,
      @Assisted TranslationContext translationContext,
      @Assisted ErrorReporter errorReporter) {
    this.jsSrcOptions = jsSrcOptions;
    this.errorReporter = errorReporter;
    this.variableMappings = translationContext.soyToJsVariableMappings();
    this.codeGenerator = translationContext.codeGenerator();
  }

  /**
   * Method that returns code to access a named parameter.
   * @param paramName the name of the parameter.
   * @param isInjected true if this is an injected parameter.
   * @return The code to access the value of that parameter.
   */
  static CodeChunk.WithValue genCodeForParamAccess(String paramName, boolean isInjected) {
    return isInjected ? OPT_IJ_DATA.dotAccess(paramName) : OPT_DATA.dotAccess(paramName);
  }

  @Override protected CodeChunk.WithValue visitExprRootNode(ExprRootNode node) {
    // ExprRootNode is some indirection to make it easier to replace expressions. All we need to do
    // is visit the only child
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override
  protected WithValue visitBooleanNode(BooleanNode node) {
    return node.getValue() ? LITERAL_TRUE : LITERAL_FALSE;
  }

  @Override
  protected WithValue visitFloatNode(FloatNode node) {
    return number(node.getValue());
  }

  @Override
  protected WithValue visitIntegerNode(IntegerNode node) {
    return number(node.getValue());
  }

  @Override
  protected WithValue visitNullNode(NullNode node) {
    return LITERAL_NULL;
  }

  @Override protected CodeChunk.WithValue visitStringNode(StringNode node) {
    return stringLiteral(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override protected CodeChunk.WithValue visitListLiteralNode(ListLiteralNode node) {
    return arrayLiteral(visitChildren(node));
  }

  @Override protected CodeChunk.WithValue visitMapLiteralNode(MapLiteralNode node) {
    return visitMapLiteralNodeHelper(node, false);
  }

  /**
   * Helper to visit a MapLiteralNode, with the extra option of whether to quote keys.
   */
  private CodeChunk.WithValue visitMapLiteralNodeHelper(MapLiteralNode node, boolean doQuoteKeys) {

    // If there are only string keys, then the expression will be
    //     {aa: 11, bb: 22}    or    {'aa': 11, 'bb': 22}
    // where the former is with unquoted keys and the latter with quoted keys.
    // If there are both string and nonstring keys, then the expression will be
    //     (function() { var $$tmp0 = {'aa': 11}; $$tmp0[opt_data.bb] = 22; return $$tmp0; })()

    // If we are outputting JS code to be processed by Closure Compiler, then it is important that
    // any unquoted map literal keys are string literals, since Closure Compiler can rename unquoted
    // map keys and we want everything to be renamed at the same time.
    boolean isProbablyUsingClosureCompiler =
        jsSrcOptions.shouldGenerateJsdoc() ||
        jsSrcOptions.shouldProvideRequireSoyNamespaces() ||
        jsSrcOptions.shouldProvideRequireJsFunctions();

    // We will divide the map literal contents into two categories.
    //
    // Key-value pairs with StringNode keys can be included in the JS object literal.
    // Key-value pairs that are not StringNodes (VarRefs, IJ values, etc.) must be passed through
    // the soy.$$checkMapKey() function, cannot be included in the JS object literal, and must
    // generate code in the form of:  $$map[soy.$$checkMapKey(key)] = value

    LinkedHashMap<CodeChunk.WithValue, CodeChunk.WithValue> objLiteral = new LinkedHashMap<>();
    LinkedHashMap<CodeChunk.WithValue, CodeChunk.WithValue> assignments = new LinkedHashMap<>();

  // Process children

    for (int i = 0; i < node.numChildren(); i += 2) {
      ExprNode keyNode = node.getChild(i);
      ExprNode valueNode = node.getChild(i + 1);

      // error case: key is a non-string primitive.
      // TODO: Support map literal with nonstring key. We can probably just remove this case and
      // roll it into the next case.
      if (!(keyNode instanceof StringNode) && keyNode instanceof PrimitiveNode) {
        errorReporter.report(
            keyNode.getSourceLocation(),
            CONSTANT_USED_AS_KEY_IN_MAP_LITERAL,
            keyNode.toSourceString());
        continue;
      }

      // error case: for closure compiler users, do not allow unquoted, non-string-literal keys,
      // since the compiler may change the names of any unquoted map keys.
      if (isProbablyUsingClosureCompiler && !doQuoteKeys && !(keyNode instanceof StringNode)) {
        errorReporter.report(
            keyNode.getSourceLocation(),
            EXPR_IN_MAP_LITERAL_REQUIRES_QUOTE_KEYS_IF_JS,
            keyNode.toSourceString());
        continue;
      }

      if (keyNode instanceof StringNode) {
        // key is a StringNode; key-value pair gets included in the JS object literal.
        // figure out whether the string should be quoted or not.

        if (doQuoteKeys) {
          objLiteral.put(visit(keyNode), visit(valueNode));
        } else {
          String strKey = ((StringNode) keyNode).getValue();

          if (BaseUtils.isIdentifier(strKey)) {
            objLiteral.put(id(strKey), visit(valueNode));
          } else if (isProbablyUsingClosureCompiler) {
            errorReporter.report(
                keyNode.getSourceLocation(),
                MAP_LITERAL_WITH_NON_ID_KEY_REQUIRES_QUOTE_KEYS_IF_JS,
                keyNode.toSourceString());
            continue;
          } else {
            objLiteral.put(visit(keyNode), visit(valueNode));
          }
        }
      } else {
        // key is not a StringNode; key must be passed through soy.$$checkMapKey() and the pair
        // cannot be included in the JS object literal.

        CodeChunk.WithValue rawKey = visit(keyNode);
        assignments.put(SOY_CHECK_MAP_KEY.call(rawKey), visit(valueNode));
      }
    }

  // Build the map literal

    ImmutableList<CodeChunk.WithValue> keys = ImmutableList.copyOf(objLiteral.keySet());
    ImmutableList<CodeChunk.WithValue> values = ImmutableList.copyOf(objLiteral.values());

    CodeChunk.WithValue map = mapLiteral(keys, values);

    if (assignments.isEmpty()) {
      // If there are no assignments, we can return the map literal directly without assigning
      // to a tmp var.
      return map;
    }

    // Otherwise, we need to bail to a tmp var and emit assignment statements.
    CodeChunk.WithValue mapVar = codeGenerator.declare(map).ref();
    ImmutableList.Builder<CodeChunk> initialStatements = ImmutableList.builder();
    for (Map.Entry<CodeChunk.WithValue, CodeChunk.WithValue> entry : assignments.entrySet()) {
      initialStatements.add(mapVar.bracketAccess(entry.getKey()).assign(entry.getValue()));
    }

    return mapVar.withInitialStatements(initialStatements.build());
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected CodeChunk.WithValue visitVarRefNode(VarRefNode node) {
    CodeChunk.WithValue translation;
    if (node.isDollarSignIjParameter()) {
      // Case 1: Injected data reference.
      return OPT_IJ_DATA.dotAccess(node.getName());
    } else if ((translation = variableMappings.maybeGet(node.getName())) != null) {
      // Case 2: In-scope local var.
      return translation;
    } else {
      // Case 3: Data reference.
      return genCodeForParamAccess(node.getName(), node.getDefnDecl().isInjected());
    }
  }

  @Override
  protected CodeChunk.WithValue visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node).result(codeGenerator);
  }

  /** See {@link NullSafeAccumulator} for discussion. */
  private NullSafeAccumulator visitNullSafeNode(ExprNode node) {
    switch (node.getKind()) {
      case FIELD_ACCESS_NODE:
        FieldAccessNode fieldAccess = (FieldAccessNode) node;
        NullSafeAccumulator base = visitNullSafeNode(fieldAccess.getBaseExprChild());
        FieldAccess access =
            genCodeForFieldAccess(
                fieldAccess.getBaseExprChild().getType(), fieldAccess, fieldAccess.getFieldName());
        return base.dotAccess(access, fieldAccess.isNullSafe());

      case ITEM_ACCESS_NODE:
        ItemAccessNode itemAccess = (ItemAccessNode) node;
        base = visitNullSafeNode(itemAccess.getBaseExprChild());
        CodeChunk.WithValue key = visit(itemAccess.getKeyExprChild());
        return base.bracketAccess(key, itemAccess.isNullSafe());

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
      return genCodeForProtoAccess((SoyProtoType) baseType, fieldName);
    }

    return FieldAccess.id(fieldName);
  }

  private static FieldAccess genCodeForProtoAccess(SoyProtoType type, String fieldName) {
    FieldDescriptor desc = type.getFieldDescriptor(fieldName);
    boolean isSanitizedContent = Protos.isSanitizedContentField(desc);
    Preconditions.checkNotNull(
        desc,
        "Error in proto %s, field not found: %s",
        type.getDescriptor().getFullName(),
        fieldName);
    if (desc.isExtension()) {
      return isSanitizedContent
          ? FieldAccess.callAndUnpack()
              .getter("getExtension")
              .arg(extensionField(desc))
              .unpackFunctionName(protoToSanitizedContentConverterFunction(desc.getMessageType()))
              .isRepeated(desc.isRepeated())
              .build()
          : FieldAccess.call("getExtension", extensionField(desc));
    }

    String getter = "get" + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
    return isSanitizedContent
        ? FieldAccess.callAndUnpack()
            .getter(getter)
            .unpackFunctionName(protoToSanitizedContentConverterFunction(desc.getMessageType()))
            .isRepeated(desc.isRepeated())
            .build()
        : FieldAccess.call(getter);
  }

  @Override protected CodeChunk.WithValue visitGlobalNode(GlobalNode node) {
    if (node.isResolved()) {
      return visit(node.getValue());
    }
    // jssrc supports unknown globals by plopping the global name directly into the output
    // NOTE: this may cause the jscompiler to emit warnings, users will need to whitelist them or
    // fix their use of unknown globals.
    return CodeChunk.dottedIdNoRequire(node.getName());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override protected CodeChunk.WithValue visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    List<CodeChunk.WithValue> operands = visitChildren(node);
    CodeChunk.WithValue consequent = operands.get(0);
    CodeChunk.WithValue alternate = operands.get(1);
    // TODO(user): use the CodeChunk DSL to get variable initialization for free:
    //  return codeGenerator
    //      .newChunk()
    //      .assign((CodeChunk.WithValue) consequent)
    //      .if_(
    //          codeGenerator.expr(CodeChunk.THIS, " == null"),
    //          codeGenerator.expr(CodeChunk.THIS, " = ", alternate, ";"))
    //      .endif()
    //      .build();
    // We can't do this yet because that chunk is never representable as a single expression.

    return CodeChunk.ifExpression(id("$$temp").assign(consequent).doubleEqualsNull(), alternate)
        .else_(id("$$temp"))
        .build(codeGenerator);
  }

  @Override
  protected CodeChunk.WithValue visitAndOpNode(AndOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    return visit(node.getChild(0)).and(visit(node.getChild(1)), codeGenerator);
  }

  @Override
  protected CodeChunk.WithValue visitOrOpNode(OrOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    return visit(node.getChild(0)).or(visit(node.getChild(1)), codeGenerator);
  }

  @Override
  protected WithValue visitConditionalOpNode(ConditionalOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 3);
    return codeGenerator.conditionalExpression(
        visit(node.getChild(0)), visit(node.getChild(1)), visit(node.getChild(2)));
  }

  @Override
  protected CodeChunk.WithValue visitOperatorNode(OperatorNode node) {
    return operation(node.getOperator(), visitChildren(node));
  }

  @Override
  protected CodeChunk.WithValue visitProtoInitNode(ProtoInitNode node) {
    SoyProtoType type = (SoyProtoType) node.getType();
    CodeChunk.WithValue proto = new_(protoConstructor(type)).call();
    if (node.numChildren() == 0) {
      // If there's no further structure to the proto, no need to declare a variable.
      return proto;
    }
    CodeChunk.WithValue protoVar = codeGenerator.declare(proto).ref();
    ImmutableList.Builder<CodeChunk> initialStatements = ImmutableList.builder();

    for (int i = 0; i < node.numChildren(); i++) {
      String fieldName = node.getParamName(i);
      FieldDescriptor fieldDesc = type.getFieldDescriptor(fieldName);
      CodeChunk.WithValue fieldValue = visit(node.getChild(i));
      if (Protos.isSanitizedContentField(fieldDesc)) {
        CodeChunk.WithValue sanitizedContentPackFn =
            sanitizedContentToProtoConverterFunction(fieldDesc.getMessageType());
        fieldValue =
            fieldDesc.isRepeated()
                ? GOOG_ARRAY_MAP.call(fieldValue, sanitizedContentPackFn)
                : sanitizedContentPackFn.call(fieldValue);
      }

      // See go/jspb for setter and getter names.  // MOE: strip_line
      if (fieldDesc.isExtension()) {
        CodeChunk.WithValue extInfo = extensionField(fieldDesc);
        initialStatements.add(protoVar.dotAccess("setExtension").call(extInfo, fieldValue));
      } else {
        String setFn = "set" + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
        initialStatements.add(protoVar.dotAccess(setFn).call(fieldValue));
      }
    }

    return protoVar.withInitialStatements(initialStatements.build());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override protected CodeChunk.WithValue visitFunctionNode(FunctionNode node) {
    SoyFunction soyFunction = node.getSoyFunction();
    if (soyFunction instanceof BuiltinFunction) {
      switch ((BuiltinFunction) soyFunction) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case QUOTE_KEYS_IF_JS:
          return visitMapLiteralNodeHelper((MapLiteralNode) node.getChild(0), true);
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node);
        case V1_EXPRESSION:
          return visitV1ExpressionFunction(node);
        default:
          throw new AssertionError();
      }
    } else if (soyFunction instanceof SoyJsSrcFunction) {
      List<CodeChunk.WithValue> args = visitChildren(node);
      List<JsExpr> functionInputs = new ArrayList<>(args.size());
      List<CodeChunk> initialStatements = new ArrayList<>();
      RequiresCollector.IntoImmutableSet collector = new RequiresCollector.IntoImmutableSet();
      // SoyJsSrcFunction doesn't understand CodeChunks; it needs JsExprs.
      // Grab the JsExpr for each CodeChunk arg to deliver to the SoyToJsSrcFunction as input.
      for (CodeChunk.WithValue arg : args) {
        arg.collectRequires(collector);
        functionInputs.add(arg.singleExprOrName());
        Iterables.addAll(initialStatements, arg.initialStatements());
      }
      // Compute the function on the JsExpr inputs.
      SoyJsSrcFunction soyJsSrcFunction = (SoyJsSrcFunction) soyFunction;
      if (soyJsSrcFunction instanceof SoyLibraryAssistedJsSrcFunction) {
        for (String name :
            ((SoyLibraryAssistedJsSrcFunction) soyJsSrcFunction).getRequiredJsLibNames()) {
          collector.add(GoogRequire.create(name));
        }
      }
      CodeChunk.WithValue functionOutput =
          dontTrustPrecedenceOf(soyJsSrcFunction.computeForJsSrc(functionInputs), collector.get());
      return functionOutput.withInitialStatements(initialStatements);
    } else {
      errorReporter.report(
          node.getSourceLocation(), SOY_JS_SRC_FUNCTION_NOT_FOUND, node.getFunctionName());
      return stringLiteral("SOY_FUNCTION_FAILED");
    }
  }

  private CodeChunk.WithValue visitCheckNotNullFunction(FunctionNode node) {
    return SOY_CHECK_NOT_NULL.call(visit(node.getChild(0)));
  }

  private CodeChunk.WithValue visitIsFirstFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__isFirst");
  }

  private CodeChunk.WithValue visitIsLastFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__isLast");
  }

  private CodeChunk.WithValue visitIndexFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return variableMappings.get(varName + "__index");
  }

  private CodeChunk.WithValue visitV1ExpressionFunction(FunctionNode node) {
    String exprText = ((StringNode) node.getChild(0)).getValue();
    return CodeChunk.fromExpr(
        V1JsExprTranslator.translateToJsExpr(
            exprText, node.getSourceLocation(), variableMappings, errorReporter),
        ImmutableList.<GoogRequire>of());
  }
}
