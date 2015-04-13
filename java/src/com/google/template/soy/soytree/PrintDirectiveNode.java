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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node representing a 'print' directive.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PrintDirectiveNode extends AbstractSoyNode implements ExprHolderNode {


  /** Set of directive names only recognized in V1. */
  private static final Set<String> V1_DIRECTIVE_NAMES = ImmutableSet.of(
      "|noescape", "|escape", "|insertwordbreaks");

  /** Maps deprecated directive names to the names of modern directives. */
  private static final Map<String, String> DEPRECATED_DIRECTIVE_NAMES = ImmutableMap.of(
      "|noescape", "|noAutoescape",
      "|escape", "|escapeHtml",
      "|escapeJs", "|escapeJsString",
      "|insertwordbreaks", "|insertWordBreaks");


  /** The directive name (including vertical bar). */
  private final String name;

  /** The directive name in source code (including vertical bar). For user msgs only! */
  private final String srcName;

  /** The text of all the args. */
  private final String argsText;

  /** The parsed args. */
  private final ImmutableList<ExprRootNode> args;

  private PrintDirectiveNode(
      int id,
      String name,
      String srcName,
      ImmutableList<ExprRootNode> args,
      String argsText,
      SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.name = name;
    this.srcName = srcName;
    this.args = args;
    this.argsText = argsText;
    // If this name is part of the V1 syntax, then maybe set the syntax version.
    if (V1_DIRECTIVE_NAMES.contains(srcName)) {
      maybeSetSyntaxVersionBound(new SyntaxVersionBound(
          SyntaxVersion.V2_1, "Print directive '" + srcName + "' is from Soy V1.0."));
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private PrintDirectiveNode(PrintDirectiveNode orig) {
    super(orig);
    this.srcName = orig.srcName;
    this.name = orig.name;
    this.argsText = orig.argsText;
    List<ExprRootNode> tempArgs = Lists.newArrayListWithCapacity(orig.args.size());
    for (ExprRootNode origArg : orig.args) {
      tempArgs.add(origArg.clone());
    }
    this.args = ImmutableList.copyOf(tempArgs);
  }


  @Override public Kind getKind() {
    return Kind.PRINT_DIRECTIVE_NODE;
  }


  /** Returns the directive name (including vertical bar). */
  public String getName() {
    return name;
  }


  /** The parsed args. */
  public List<ExprRootNode> getArgs() {
    return args;
  }


  @Override public String toSourceString() {
    return srcName + ((argsText.length() > 0) ? ":" + argsText : "");
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ExprUnion.createList(args);
  }


  @Override public PrintDirectiveNode clone() {
    return new PrintDirectiveNode(this);
  }

  /**
   * Builder for {@link PrintDirectiveNode}.
   */
  public static final class Builder {
    private final int id;
    private final String srcName;
    private final String argsText;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param srcName The directive name in source code (including vertical bar).
     * @param argsText The text of all the args, or empty string if none (usually empty string).
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, String srcName, String argsText, SourceLocation sourceLocation) {
      this.id = id;
      this.srcName = srcName;
      this.argsText = argsText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link PrintDirectiveNode} from the state of this builder, reporting syntax
     * errors to the given {@link ErrorReporter}.
     */
    public PrintDirectiveNode build(ErrorReporter errorReporter) {
      String name = parseName();
      ImmutableList<ExprRootNode> args = parseArgs(errorReporter);
      return new PrintDirectiveNode(id, name, srcName, args, argsText, sourceLocation);
    }

    private String parseName() {
      String translatedDirectiveName = DEPRECATED_DIRECTIVE_NAMES.get(srcName);
      if (translatedDirectiveName == null) {
        // Not a deprecated directive name.
        return srcName;
      } else {
        // Use the undeprecated name since the supporting Java and JavaScript code only contains
        // support functions for undeprecated directives.
        return translatedDirectiveName;
      }
    }

    private ImmutableList<ExprRootNode> parseArgs(ErrorReporter errorReporter) {
      if (this.argsText.isEmpty()) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<ExprRootNode> args = ImmutableList.builder();
      for (ExprNode expr : new ExpressionParser(argsText, sourceLocation, errorReporter)
          .parseExpressionList()) {
        args.add(new ExprRootNode(expr));
      }
      return args.build();
    }
  }

}
