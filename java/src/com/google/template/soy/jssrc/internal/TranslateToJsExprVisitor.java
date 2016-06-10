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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ConstantNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsCodeUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.UnknownType;

import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent JS expression.
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
public class TranslateToJsExprVisitor extends AbstractReturningExprNodeVisitor<JsExpr> {

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
   * Errors in this visitor generate JS source that immediately explodes.
   * Users of Soy are expected to check the error reporter before using the gencode;
   * if they don't, this should apprise them.
   * TODO(brndn): consider changing the visitor to return {@code Optional<JsExpr>}
   * and returning {@link Optional#absent()} on error.
   */
  private static final JsExpr ERROR = new JsExpr(
      "(function() { throw new Error('Soy compilation failed'); })();",
      Integer.MAX_VALUE);

  /**
   * Injectable factory for creating an instance of this class.
   */
  public interface TranslateToJsExprVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement JS expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    TranslateToJsExprVisitor create(
        Deque<Map<String, JsExpr>> localVarTranslations, ErrorReporter errorReporter);
  }

  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JsExpr>> localVarTranslations;
  private final ErrorReporter errorReporter;

  /**
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  TranslateToJsExprVisitor(
      SoyJsSrcOptions jsSrcOptions,
      @Assisted Deque<Map<String, JsExpr>> localVarTranslations,
      @Assisted ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.jsSrcOptions = jsSrcOptions;
    this.localVarTranslations = localVarTranslations;
  }

  /**
   * Method that returns code to access a named parameter.
   * @param paramName the name of the parameter.
   * @param isInjected true if this is an injected parameter.
   * @param type the type of the parameter being accessed.
   * @return The code to access the value of that parameter.
   */
  static String genCodeForParamAccess(String paramName, boolean isInjected, SoyType type) {
    return genCodeForKeyAccess(
        UnknownType.getInstance(), type,
        isInjected ? "opt_ijData" : "opt_data", paramName);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.

  @Override protected JsExpr visitExprRootNode(ExprRootNode node) {
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override protected JsExpr visitStringNode(StringNode node) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    return new JsExpr(
        BaseUtils.escapeToSoyString(node.getValue(), true),
        Integer.MAX_VALUE);
  }

  @Override protected JsExpr visitPrimitiveNode(PrimitiveNode node) {
    // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
    // primitives, the result is usually also the correct JS expression.
    // Note: The rare exception to the above note is a StringNode containing a Unicode Format
    // character (Unicode category "Cf") because of the JavaScript language quirk that requires all
    // category "Cf" characters to be escaped in JS strings. Therefore, we have a separate
    // implementation above for visitStringNode(StringNode).
    return new JsExpr(node.toSourceString(), Integer.MAX_VALUE);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override protected JsExpr visitListLiteralNode(ListLiteralNode node) {

    StringBuilder exprTextSb = new StringBuilder();
    exprTextSb.append('[');

    boolean isFirst = true;
    for (ExprNode child : node.getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        exprTextSb.append(", ");
      }
      exprTextSb.append(visit(child).getText());
    }

    exprTextSb.append(']');

    return new JsExpr(exprTextSb.toString(), Integer.MAX_VALUE);
  }

  @Override protected JsExpr visitMapLiteralNode(MapLiteralNode node) {
    return visitMapLiteralNodeHelper(node, false);
  }

  /**
   * Helper to visit a MapLiteralNode, with the extra option of whether to quote keys.
   */
  private JsExpr visitMapLiteralNodeHelper(MapLiteralNode node, boolean doQuoteKeys) {

    // If there are only string keys, then the expression will be
    //     {aa: 11, bb: 22}    or    {'aa': 11, 'bb': 22}
    // where the former is with unquoted keys and the latter with quoted keys.
    // If there are both string and nonstring keys, then the expression will be
    //     (function() { var map_s = {'aa': 11}; map_s[opt_data.bb] = 22; return map_s; })()

    StringBuilder strKeysEntriesSnippet = new StringBuilder();
    StringBuilder nonstrKeysEntriesSnippet = new StringBuilder();

    boolean isProbablyUsingClosureCompiler =
        jsSrcOptions.shouldGenerateJsdoc() ||
        jsSrcOptions.shouldProvideRequireSoyNamespaces() ||
        jsSrcOptions.shouldProvideRequireJsFunctions();

    Checkpoint checkpoint = errorReporter.checkpoint();

    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      ExprNode keyNode = node.getChild(i);
      ExprNode valueNode = node.getChild(i + 1);

      if (keyNode instanceof StringNode) {
        if (strKeysEntriesSnippet.length() > 0) {
          strKeysEntriesSnippet.append(", ");
        }
        if (doQuoteKeys) {
          strKeysEntriesSnippet.append(visit(keyNode).getText());
        } else {
          String key = ((StringNode) keyNode).getValue();
          if (BaseUtils.isIdentifier(key)) {
            strKeysEntriesSnippet.append(key);
          } else if (isProbablyUsingClosureCompiler) {
            errorReporter.report(
                keyNode.getSourceLocation(),
                MAP_LITERAL_WITH_NON_ID_KEY_REQUIRES_QUOTE_KEYS_IF_JS,
                keyNode.toSourceString());
          } else {
            strKeysEntriesSnippet.append(visit(keyNode).getText());
          }
        }
        strKeysEntriesSnippet.append(": ").append(visit(valueNode).getText());

      } else if (keyNode instanceof ConstantNode) {
        // TODO: Support map literal with nonstring key. We can probably just remove this case and
        // roll it into the next case.
        errorReporter.report(
            keyNode.getSourceLocation(),
            CONSTANT_USED_AS_KEY_IN_MAP_LITERAL,
            keyNode.toSourceString());
      } else if (isProbablyUsingClosureCompiler && !doQuoteKeys) {
        errorReporter.report(
            keyNode.getSourceLocation(),
            EXPR_IN_MAP_LITERAL_REQUIRES_QUOTE_KEYS_IF_JS,
            keyNode.toSourceString());
        return ERROR;
      } else {
        nonstrKeysEntriesSnippet
            .append(" map_s[soy.$$checkMapKey(").append(visit(keyNode).getText()).append(")] = ")
            .append(visit(valueNode).getText()).append(';');
      }
    }

    String fullExprText;
    if (nonstrKeysEntriesSnippet.length() == 0) {
      fullExprText = "{" + strKeysEntriesSnippet + "}";
    } else {
      fullExprText =
          "(function() { var map_s = {" + strKeysEntriesSnippet + "};" + nonstrKeysEntriesSnippet
          + " return map_s; })()";
    }

    return errorReporter.errorsSince(checkpoint)
        ? ERROR
        : new JsExpr(fullExprText, Integer.MAX_VALUE);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override protected JsExpr visitVarRefNode(VarRefNode node) {
    return visitNullSafeNode(node);
  }

  @Override protected JsExpr visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node);
  }

  private JsExpr visitNullSafeNode(ExprNode node) {
    StringBuilder nullSafetyPrefix = new StringBuilder();
    String refText = visitNullSafeNodeRecurse(node, nullSafetyPrefix);

    if (nullSafetyPrefix.length() == 0) {
      return new JsExpr(refText, Integer.MAX_VALUE);
    } else {
      return new JsExpr(nullSafetyPrefix + refText, Operator.CONDITIONAL.getPrecedence());
    }
  }

  private String visitNullSafeNodeRecurse(ExprNode node, StringBuilder nullSafetyPrefix) {

    switch (node.getKind()) {
      case VAR_REF_NODE: {
        VarRefNode varRef = (VarRefNode) node;
        if (varRef.isDollarSignIjParameter()) {
          // Case 1: Injected data reference.
          SoyType type = node.getType();
          return genCodeForKeyAccess(
              UnknownType.getInstance(), type, "opt_ijData", varRef.getName());
        } else {
          JsExpr translation = getLocalVarTranslation(varRef.getName());
          if (translation != null) {
            // Case 2: In-scope local var.
            return translation.getText();
          } else {
            String scope = "opt_data";
            VarDefn var = varRef.getDefnDecl();
            if (var.kind() == VarDefn.Kind.PARAM && ((TemplateParam) var).isInjected()) {
              scope = "opt_ijData";
            }
            // Case 3: Data reference.
            SoyType type = node.getType();
            return genCodeForKeyAccess(UnknownType.getInstance(), type, scope, varRef.getName());
          }
        }
      }

      case FIELD_ACCESS_NODE:
      case ITEM_ACCESS_NODE: {
        DataAccessNode dataAccess = (DataAccessNode) node;
        // First recursively visit base expression.
        String refText = visitNullSafeNodeRecurse(dataAccess.getBaseExprChild(), nullSafetyPrefix);

        // Generate null safety check for base expression.
        if (dataAccess.isNullSafe()) {
          // Note: In JavaScript, "x == null" is equivalent to "x === undefined || x === null".
          nullSafetyPrefix.append("(" + refText + " == null) ? null : ");
        }

        // Generate access to field
        if (node.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
          FieldAccessNode fieldAccess = (FieldAccessNode) node;
          return genCodeForFieldAccess(
              fieldAccess.getBaseExprChild().getType(),
              fieldAccess,
              refText,
              fieldAccess.getFieldName());
        } else {
          // Generate access to item.
          ItemAccessNode itemAccess = (ItemAccessNode) node;
          if (itemAccess.getKeyExprChild() instanceof IntegerNode) {
            return refText + "[" + ((IntegerNode) itemAccess.getKeyExprChild()).getValue() + "]";
          } else {
            JsExpr keyJsExpr = visit(itemAccess.getKeyExprChild());
            return refText + "[" + keyJsExpr.getText() + "]";
          }
        }
      }

      default: {
        JsExpr value = visit(node);
        return genMaybeProtect(value, Integer.MAX_VALUE);
      }
    }
  }


  /**
   * Helper for {@code visitDataAccessNode()} to generate the code for a key access, e.g.
   * ".foo" or "['class']". Handles JS reserved words.
   * @param containerType the type of the container whose key is being accessed.
   * @param memberType the type of the value of the property being mentioned.
   * @param containerExpr An expression that evaluates to the container of the named field.
   *     This expression may have any operator precedence that binds more tightly than unary
   *     operators.
   * @param key The key.
   */
  static String genCodeForKeyAccess(
      SoyType containerType, SoyType memberType, String containerExpr, String key) {
    Preconditions.checkNotNull(containerType);
    Preconditions.checkNotNull(memberType);
    return containerExpr + (JsSrcUtils.isReservedWord(key) ? "['" + key + "']" : "." + key);
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a field
   * name access, e.g. ".foo" or "['class']". Handles JS reserved words. If the base type
   * is an object type, then it delegates the generation of the JS code to the type
   * object.
   * @param baseType The type of the object that contains the field.
   * @param fieldAccessNode The field access node.
   * @param containerExpr An expression that evaluates to the container of the named field.
   *     This expression may have any operator precedence that binds more tightly than unary
   *     operators.
   * @param fieldName The field name.
   */
  private String genCodeForFieldAccess(
      SoyType baseType, FieldAccessNode fieldAccessNode, String containerExpr, String fieldName) {
    Preconditions.checkNotNull(baseType);
    SoyType fieldType = fieldAccessNode.getType();
    Preconditions.checkNotNull(fieldType);
    // For unions, attempt to generate the field access code for each member
    // type, and then see if they all agree.
    if (baseType.getKind() == SoyType.Kind.UNION) {
      // TODO(msamuel): We will need to generate fallback code for each variant.
      UnionType unionType = (UnionType) baseType;
      String fieldAccessCode = null;
      for (SoyType memberType : unionType.getMembers()) {
        if (memberType.getKind() != SoyType.Kind.NULL) {
          String fieldAccessForType = genCodeForFieldAccess(
              memberType, fieldAccessNode, containerExpr, fieldName);
          if (fieldAccessCode == null) {
            fieldAccessCode = fieldAccessForType;
          } else if (!fieldAccessCode.equals(fieldAccessForType)) {
            errorReporter.report(
                fieldAccessNode.getSourceLocation(),
                UNION_ACCESSOR_MISMATCH,
                fieldName,
                baseType);
          }
        }
      }
      return fieldAccessCode;
    }

    if (baseType.getKind() == SoyType.Kind.OBJECT) {
      SoyObjectType objType = (SoyObjectType) baseType;
      String accessExpr = objType.getFieldAccessExpr(
          containerExpr, fieldName, SoyBackendKind.JS_SRC);
      if (accessExpr != null) {
        return accessExpr;
      }
    }

    return genCodeForKeyAccess(baseType, fieldType, containerExpr, fieldName);
  }

  @Override protected JsExpr visitGlobalNode(GlobalNode node) {
    return new JsExpr(node.toSourceString(), Integer.MAX_VALUE);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override protected JsExpr visitNotOpNode(NotOpNode node) {
    // Note: Since we're using Soy syntax for the 'not' operator, we'll end up generating code with
    // a space between the token '!' and the subexpression that it negates. This isn't the usual
    // style, but it should be fine (besides, it's more readable with the extra space).
    return genJsExprUsingSoySyntaxWithNewToken(node, "!");
  }

  @Override protected JsExpr visitAndOpNode(AndOpNode node) {
    return genJsExprUsingSoySyntaxWithNewToken(node, "&&");
  }

  @Override protected JsExpr visitOrOpNode(OrOpNode node) {
    return genJsExprUsingSoySyntaxWithNewToken(node, "||");
  }

  @Override protected JsExpr visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    List<JsExpr> operandJsExprs = visitChildren(node);
    return new JsExpr(
        "($$temp = " + operandJsExprs.get(0).getText() + ") == null ? "
            + operandJsExprs.get(1).getText() + " : $$temp",
        Operator.CONDITIONAL.getPrecedence());
  }

  @Override protected JsExpr visitOperatorNode(OperatorNode node) {
    return genJsExprUsingSoySyntax(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override protected JsExpr visitFunctionNode(FunctionNode node) {
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
          return visitCheckNotNullFunction(node.getChild(0));
        default:
          throw new AssertionError();
      }
    } else if (soyFunction instanceof SoyJsSrcFunction) {
      List<JsExpr> args = visitChildren(node);
      return ((SoyJsSrcFunction) soyFunction).computeForJsSrc(args);
    } else {
      errorReporter.report(
          node.getSourceLocation(), SOY_JS_SRC_FUNCTION_NOT_FOUND, node.getFunctionName());
      return ERROR;
    }
  }

  private JsExpr visitCheckNotNullFunction(ExprNode child) {
    return new JsExpr("soy.$$checkNotNull(" + visit(child).getText() + ")", Integer.MAX_VALUE);
  }

  private JsExpr visitIsFirstFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return getLocalVarTranslation(varName + "__isFirst");
  }


  private JsExpr visitIsLastFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return getLocalVarTranslation(varName + "__isLast");
  }


  private JsExpr visitIndexFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getName();
    return getLocalVarTranslation(varName + "__index");
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   * @param ident The Soy local variable to translate.
   * @return The translated expression for the given variable, or null if not found.
   */
  private JsExpr getLocalVarTranslation(String ident) {
    for (Map<String, JsExpr> localVarTranslationsFrame : localVarTranslations) {
      JsExpr translation = localVarTranslationsFrame.get(ident);
      if (translation != null) {
        return translation;
      }
    }

    return null;
  }

  /**
   * Generates a JS expression for the given OperatorNode's subtree assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator.
   * @param opNode The OperatorNode whose subtree to generate a JS expression for.
   * @return The generated JS expression.
   */
  private JsExpr genJsExprUsingSoySyntax(OperatorNode opNode) {
    return genJsExprUsingSoySyntaxWithNewToken(opNode, null);
  }

  /**
   * Generates a JS expression for the given OperatorNode's subtree assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator, with the exception that the
   * JS operator uses a different token (e.g. "!" instead of "not").
   * @param opNode The OperatorNode whose subtree to generate a JS expression for.
   * @param newToken The equivalent JS operator's token.
   * @return The generated JS expression.
   */
  private JsExpr genJsExprUsingSoySyntaxWithNewToken(OperatorNode opNode, String newToken) {
    List<JsExpr> operandJsExprs = visitChildren(opNode);

    return SoyJsCodeUtils.genJsExprUsingSoySyntaxWithNewToken(
        opNode.getOperator(), operandJsExprs, newToken);
  }

  public static String genMaybeProtect(JsExpr expr, int minSafePrecedence) {
    return (expr.getPrecedence() >= minSafePrecedence) ?
           expr.getText() : "(" + expr.getText() + ")";
  }
}
