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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing a 'css' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CssNode extends AbstractCommandNode implements StandaloneNode, StatementNode {

  /**
   * The selector text. Either the entire command text of the CSS command, or the suffix if you are
   * using @component soy syntax: <code>{css $componentName, SUFFIX}</code>
   */
  private final String selectorText;

  /**
   * This pair keeps a mapping to the last used map and the calculated value, so that we don't have
   * to lookup the value again if the same renaming map is used.
   *
   * <p>Note that you need to make sure that the number of actually occurring maps is very low and
   * should really be at max 2 (one for obfuscated and one for unobfuscated renaming). Also in
   * production only one of the maps should really be used, so that cache hit rate approaches 100%.
   *
   * <p>Note: Not used for css() function calls. TODO(user): Remove.
   */
  private Pair<SoyCssRenamingMap, String> renameCache;

  public CssNode(int id, SourceLocation location, String selectorText) {
    super(id, location, "css");
    this.selectorText = checkNotNull(selectorText);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CssNode(CssNode orig, CopyState copyState) {
    super(orig, copyState);
    this.selectorText = orig.selectorText;
  }

  /**
   * Transform constructor - creates a copy but with different selector text.
   *
   * @param orig The node to copy.
   */
  public CssNode(CssNode orig, String newSelectorText, CopyState copyState) {
    super(orig, copyState);
    this.selectorText = newSelectorText;
  }

  @Override
  public Kind getKind() {
    return Kind.CSS_NODE;
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

    String mappedText = cssRenamingMap.get(selectorText);
    if (mappedText != null) {
      renameCache = Pair.of(cssRenamingMap, mappedText);
      return mappedText;
    }

    return selectorText;
  }

  @Override
  public String getCommandText() {
    return selectorText;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public CssNode copy(CopyState copyState) {
    return new CssNode(this, copyState);
  }
}
