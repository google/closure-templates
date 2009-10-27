/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Node representing a 'print' directive.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class PrintDirectiveNode extends AbstractSoyNode implements ExprHolderNode {


  /** Mapping from V1 to V2 directive names. */
  private static final Map<String, String> V1_TO_V2_DIRECTIVE_NAMES =
      ImmutableMap.of("|noescape", "|noAutoescape",
                      "|escape", "|escapeHtml",
                      "|insertwordbreaks", "|insertWordBreaks");


  /** The directive name (including vertical bar). */
  private String name;

  /** The text of all the args. */
  private final String argsText;

  /** The parsed args. */
  private final List<ExprRootNode<ExprNode>> args;


  /**
   * @param id The id for this node.
   * @param name The directive name (including vertical bar).
   * @param argsText The text of all the args, or empty string if none (usually empty string).
   * @throws SoySyntaxException If a syntax error is found.
   */
  public PrintDirectiveNode(String id, String name, String argsText) throws SoySyntaxException {
    super(id);

    String translatedV2DirectiveName = V1_TO_V2_DIRECTIVE_NAMES.get(name);
    if (translatedV2DirectiveName == null) {
      // V2 directive name.
      this.name = name;
    } else {
      // V1 directive name that we must translate to V2. Also maybe adjust syntax version.
      this.name = translatedV2DirectiveName;
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }

    this.argsText = argsText;

    if (this.argsText.length() > 0) {
      try {
        args = (new ExpressionParser(argsText)).parseExpressionList();
      } catch (TokenMgrError tme) {
        throw createExceptionForInvalidArgs(tme);
      } catch (ParseException pe) {
        throw createExceptionForInvalidArgs(pe);
      }
    } else {
      args = Collections.emptyList();
    }
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidArgs(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid arguments for print directive \"" + toString() + "\".", cause);
  }


  /** @param name The new name to set for this directive. */
  public void setName(String name) {
    this.name = name;
  }

  /** Returns the directive name (including vertical bar). */
  public String getName() {
    return name;
  }

  /** The text of all the args. */
  public String getArgsText() {
    return argsText;
  }

  /** The parsed args. */
  public List<ExprRootNode<ExprNode>> getArgs() {
    return args;
  }


  @Override public String toSourceString() {
    return name + ((argsText.length() > 0) ? ":" + argsText : "");
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return args;
  }

}
