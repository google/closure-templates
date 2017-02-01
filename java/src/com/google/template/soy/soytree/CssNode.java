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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Node representing a 'css' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
// TODO: Figure out why the CSS @component syntax doesn't support
// injected data ($ij.foo).  It looks like Soy is not checking CssNodes for
// injected data.
public final class CssNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, ExprHolderNode {

  /** Regular expression for a CSS class name. */
  private static final String CSS_CLASS_NAME_RE = "(-|%)?[a-zA-Z_]+[a-zA-Z0-9_-]*";

  /** Pattern for valid selectorText in a 'css' tag. */
  private static final Pattern SELECTOR_TEXT_PATTERN =
      Pattern.compile("^(" + CSS_CLASS_NAME_RE + "|" + "[$]?" + BaseUtils.DOTTED_IDENT_RE + ")$");

  private static final SoyErrorKind INVALID_CSS_ARGUMENT =
      SoyErrorKind.of(
          "Invalid argument to CSS command. Argument must be a valid CSS class name or"
              + " identifier.");

  /**
   * Component name expression of a CSS command. Null if CSS command has no expression. In the
   * example <code>{css $componentName, SUFFIX}</code>, this would be $componentName.
   */
  @Nullable private final ExprRootNode componentNameExpr;

  /**
   * The selector text. Either the entire command text of the CSS command, or the suffix if you are
   * using @component soy syntax: <code>{css $componentName, SUFFIX}</code>
   */
  private final String selectorText;

  /**
   * This pair keeps a mapping to the last used map and the calculated value, so that we don't have
   * lookup the value again if the same renaming map is used. Note that you need to make sure that
   * the number of actually occuring maps is very low and should really be at max 2 (one for
   * obfuscated and one for unobfuscated renaming). Also in production only one of the maps should
   * really be used, so that cache hit rate approaches 100%.
   */
  Pair<SoyCssRenamingMap, String> renameCache;

  private CssNode(
      int id,
      String commandText,
      @Nullable ExprRootNode componentNameExpr,
      String selectorText,
      SourceLocation sourceLocation) {
    super(id, sourceLocation, "css", commandText);
    this.componentNameExpr = componentNameExpr;
    this.selectorText = selectorText;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CssNode(CssNode orig, CopyState copyState) {
    super(orig, copyState);
    //noinspection ConstantConditions IntelliJ
    this.componentNameExpr =
        (orig.componentNameExpr != null) ? orig.componentNameExpr.copy(copyState) : null;
    this.selectorText = orig.selectorText;
  }

  /**
   * Transform constructor - creates a copy but with different selector text.
   *
   * @param orig The node to copy.
   */
  public CssNode(CssNode orig, String newSelectorText, CopyState copyState) {
    super(orig, copyState);
    //noinspection ConstantConditions IntelliJ
    this.componentNameExpr =
        (orig.componentNameExpr != null) ? orig.componentNameExpr.copy(copyState) : null;
    this.selectorText = newSelectorText;
  }

  @Override
  public Kind getKind() {
    return Kind.CSS_NODE;
  }

  /** Returns the parsed component name expression, or null if this node has no expression. */
  @Nullable
  public ExprRootNode getComponentNameExpr() {
    return componentNameExpr;
  }

  /** Returns the component name text, or null if this node has no component expression. */
  public String getComponentNameText() {
    return (componentNameExpr != null) ? componentNameExpr.toSourceString() : null;
  }

  /** Returns the selector text from this command. */
  public String getSelectorText() {
    return selectorText;
  }

  public String getRenamedSelectorText(SoyCssRenamingMap cssRenamingMap) {
    // Copy the property to a local here as it may be written to in a separate thread.
    // The cached value is a pair that keeps a reference to the map that was used for renaming it.
    // If the same map is passed to this call, we use the cached value, otherwise we rename
    // again and store the a new pair in the cache. For thread safety reasons this must be a Pair
    // over 2 independent instance variables.
    Pair<SoyCssRenamingMap, String> cache = renameCache;
    if (cache != null && cache.first == cssRenamingMap) {
      return cache.second;
    }
    if (cssRenamingMap != null) {
      String mappedText = cssRenamingMap.get(selectorText);
      if (mappedText != null) {
        renameCache = Pair.of(cssRenamingMap, mappedText);
        return mappedText;
      }
    }
    return selectorText;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return (componentNameExpr != null)
        ? ImmutableList.of(new ExprUnion(componentNameExpr))
        : Collections.<ExprUnion>emptyList();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public CssNode copy(CopyState copyState) {
    return new CssNode(this, copyState);
  }

  /** Builder for {@link CssNode}. */
  public static final class Builder {
    private final int id;
    private final String commandText;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param commandText The node's command text.
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link CssNode} built from the builder's state, reporting syntax errors to the
     * given {@link ErrorReporter}.
     */
    public CssNode build(SoyParsingContext context) {
      int delimPos = commandText.lastIndexOf(',');
      ExprRootNode componentNameExpr = null;
      String selectorText = commandText;
      if (delimPos != -1) {
        String componentNameText = commandText.substring(0, delimPos).trim();
        componentNameExpr =
            new ExprRootNode(
                new ExpressionParser(componentNameText, sourceLocation, context).parseExpression());
        selectorText = commandText.substring(delimPos + 1).trim();
      }
      if (!SELECTOR_TEXT_PATTERN.matcher(selectorText).matches()) {
        context.report(sourceLocation, INVALID_CSS_ARGUMENT);
      }
      return new CssNode(id, commandText, componentNameExpr, selectorText, sourceLocation);
    }
  }
}
