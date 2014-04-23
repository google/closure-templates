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

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.BaseUtils;
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
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsCodeUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.UnionType;

import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent JS expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TranslateToJsExprVisitor extends AbstractReturningExprNodeVisitor<JsExpr> {

  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface TranslateToJsExprVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement JS expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public TranslateToJsExprVisitor create(Deque<Map<String, JsExpr>> localVarTranslations);
  }

  /** Map of all SoyJsSrcFunctions (name to function). */
  private final Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap;

  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JsExpr>> localVarTranslations;

  /**
   * @param soyJsSrcFunctionsMap Map of all SoyJsSrcFunctions (name to function).
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  TranslateToJsExprVisitor(
      Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap, SoyJsSrcOptions jsSrcOptions,
      @Assisted Deque<Map<String, JsExpr>> localVarTranslations) {
    this.soyJsSrcFunctionsMap = soyJsSrcFunctionsMap;
    this.jsSrcOptions = jsSrcOptions;
    this.localVarTranslations = localVarTranslations;
  }

  /**
   * Method that returns code to access a named parameter.
   * @param paramName the name of the parameter.
   * @param isInjected true if this is an injected parameter.
   * @return The code to access the value of that parameter.
   */
  static String genCodeForParamAccess(String paramName, boolean isInjected) {
    return (isInjected ? "opt_ijData" : "opt_data") + genCodeForKeyAccess(paramName);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.

  @Override protected JsExpr visitExprRootNode(ExprRootNode<?> node) {
    return visit(node.getChild(0));
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
          } else {
            if (isProbablyUsingClosureCompiler) {
              throw SoySyntaxException.createWithoutMetaInfo(
                  "Map literal with non-identifier key must be wrapped in quoteKeysIfJs()" +
                      " (found non-identifier key \"" + keyNode.toSourceString() +
                      "\" in map literal \"" + node.toSourceString() + "\").");
            } else {
              strKeysEntriesSnippet.append(visit(keyNode).getText());
            }
          }
        }
        strKeysEntriesSnippet.append(": ").append(visit(valueNode).getText());

      } else if (keyNode instanceof ConstantNode) {
        // TODO: Support map literal with nonstring key. We can probably just remove this case and
        // roll it into the next case.
        throw SoySyntaxException.createWithoutMetaInfo(
            "Map literal must have keys that are strings or expressions that will evaluate to" +
                " strings at render time (found non-string key \"" + keyNode.toSourceString() +
                "\" in map literal \"" + node.toSourceString() + "\").");

      } else {
        if (isProbablyUsingClosureCompiler && ! doQuoteKeys) {
          throw SoySyntaxException.createWithoutMetaInfo(
              "Map literal with expression key must be wrapped in quoteKeysIfJs()" +
                  " (found expression key \"" + keyNode.toSourceString() +
                  "\" in map literal \"" + node.toSourceString() + "\").");
        }
        nonstrKeysEntriesSnippet
            .append(" map_s[soy.$$checkMapKey(").append(visit(keyNode).getText()).append(")] = ")
            .append(visit(valueNode).getText()).append(';');
      }
    }

    String fullExprText;
    if (nonstrKeysEntriesSnippet.length() == 0) {
      fullExprText = "{" + strKeysEntriesSnippet.toString() + "}";
    } else {
      fullExprText = "(function() { var map_s = {" + strKeysEntriesSnippet.toString() + "};" +
          nonstrKeysEntriesSnippet.toString() + " return map_s; })()";
    }

    return new JsExpr(fullExprText, Integer.MAX_VALUE);
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
      return new JsExpr(
          nullSafetyPrefix.toString() + refText, Operator.CONDITIONAL.getPrecedence());
    }
  }

  private String visitNullSafeNodeRecurse(ExprNode node, StringBuilder nullSafetyPrefix) {

    switch (node.getKind()) {
      case VAR_REF_NODE: {
        VarRefNode varRef = (VarRefNode) node;
        if (varRef.isInjected()) {
          // Case 1: Injected data reference.
          if (varRef.isNullSafeInjected()) {
            nullSafetyPrefix.append("(opt_ijData == null) ? null : ");
          }
          return "opt_ijData" + genCodeForKeyAccess(varRef.getName());
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
            return scope + genCodeForKeyAccess(varRef.getName());
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
          return refText + genCodeForFieldAccess(
              fieldAccess.getBaseExprChild().getType(), fieldAccess.getFieldName());
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
   * Private helper for {@code visitDataAccessNode()} to generate the code for a key access, e.g.
   * ".foo" or "['class']". Handles JS reserved words.
   * @param key The key.
   */
  static String genCodeForKeyAccess(String key) {
    return JsSrcUtils.isReservedWord(key) ? "['" + key + "']" : "." + key;
  }

  /**
   * Private helper for {@code visitDataAccessNode()} to generate the code for a field
   * name access, e.g. ".foo" or "['class']". Handles JS reserved words. If the base type
   * is an object type, then it delegates the generation of the JS code to the type
   * object.
   * @param baseType The type of the object that contains the field.
   * @param fieldName The field name.
   */
  private static String genCodeForFieldAccess(SoyType baseType, String fieldName) {
    if (baseType != null) {
      // For unions, attempt to generate the field access code for each member
      // type, and then see if they all agree.
      if (baseType.getKind() == SoyType.Kind.UNION) {
        UnionType unionType = (UnionType) baseType;
        String fieldAccessCode = null;
        for (SoyType memberType : unionType.getMembers()) {
          if (memberType.getKind() != SoyType.Kind.NULL) {
            String fieldAccessForType = genCodeForFieldAccess(memberType, fieldName);
            if (fieldAccessCode == null) {
              fieldAccessCode = fieldAccessForType;
            } else if (!fieldAccessCode.equals(fieldAccessForType)) {
              throw SoySyntaxException.createWithoutMetaInfo(
                  "Cannot access field '" + fieldName + "' of type'" + baseType.toString() +
                  ", because the different union member types have different access methods.");
            }
          }
        }
        return fieldAccessCode;
      }

      if (baseType.getKind() == SoyType.Kind.OBJECT) {
        SoyObjectType objType = (SoyObjectType) baseType;
        String accessExpr = objType.getFieldAccessor(fieldName, SoyBackendKind.JS_SRC);
        if (accessExpr != null) {
          return accessExpr;
        }
      }
    }
    return genCodeForKeyAccess(fieldName);
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

  @Override protected JsExpr visitOperatorNode(OperatorNode node) {
    return genJsExprUsingSoySyntax(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override protected JsExpr visitFunctionNode(FunctionNode node) {
    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      if (numArgs != nonpluginFn.getNumArgs()) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fnName + "' called with the wrong number of arguments" +
                " (function call \"" + node.toSourceString() + "\").");
      }
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case QUOTE_KEYS_IF_JS:
          return visitMapLiteralNodeHelper((MapLiteralNode) node.getChild(0), true);
        default:
          throw new AssertionError();
      }
    }

    // Handle plugin functions.
    SoyJsSrcFunction fn = soyJsSrcFunctionsMap.get(fnName);
    if (fn != null) {
      if (! fn.getValidArgsSizes().contains(numArgs)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fnName + "' called with the wrong number of arguments" +
                " (function call \"" + node.toSourceString() + "\").");
      }
      List<JsExpr> args = visitChildren(node);
      try {
        return fn.computeForJsSrc(args);
      } catch (Exception e) {
        throw SoySyntaxException.createCausedWithoutMetaInfo(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
    }

    // Function not found.
    throw SoySyntaxException.createWithoutMetaInfo(
        "Failed to find SoyJsSrcFunction with name '" + fnName + "'" +
            " (function call \"" + node.toSourceString() + "\").");
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
