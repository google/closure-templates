/*
 * Copyright 2013 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing an 'xid' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class XidNode extends AbstractCommandNode implements StandaloneNode, StatementNode {

  private static final SoyErrorKind INVALID_XID_VALUE = SoyErrorKind.of("Invalid xid value");

  /** The text of the identifier. */
  private final String text;

  /**
   * This pair keeps a mapping to the last used map and the calculated value, so that we don't have
   * lookup the value again if the same renaming map is used. Note that you need to make sure that
   * the number of actually occuring maps is very low and should really be at max 2 (one for
   * obfuscated and one for unobfuscated renaming). Also in production only one of the maps should
   * really be used, so that cache hit rate approaches 100%.
   */
  private volatile Pair<SoyIdRenamingMap, String> renameCache;

  /**
   * @param id The id for this node.
   * @param commandText The command text.
   */
  public XidNode(int id, SourceLocation sourceLocation, String commandText) {
    super(id, sourceLocation, "xid", commandText);
    text = commandText;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private XidNode(XidNode orig, CopyState copyState) {
    super(orig, copyState);
    text = orig.text;
  }

  @Override
  public Kind getKind() {
    return Kind.XID_NODE;
  }

  /** Returns the text of the identifier. */
  public String getText() {
    return text;
  }

  public String getRenamedText(SoyIdRenamingMap idRenamingMap) {
    // Copy the property to a local here as it may be written to in a separate thread.
    // The cached value is a pair that keeps a reference to the map that was used for renaming it.
    // If the same map is passed to this call, we use the cached value, otherwise we rename
    // again and store the a new pair in the cache. For thread safety reasons this must be a Pair
    // over 2 independent instance variables.
    Pair<SoyIdRenamingMap, String> cache = renameCache;
    if (cache != null && cache.first == idRenamingMap) {
      return cache.second;
    }
    if (idRenamingMap != null) {
      String mappedText = idRenamingMap.get(text);
      if (mappedText != null) {
        renameCache = Pair.of(idRenamingMap, mappedText);
        return mappedText;
      }
    }
    // Default to pseudo obfuscate with trailing _ since that is what the JS implementation does.
    return text + "_";
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public XidNode copy(CopyState copyState) {
    return new XidNode(this, copyState);
  }

  /** Builder for {@link XidNode}. */
  public static final class Builder {

    public static final XidNode ERROR = new XidNode(-1, SourceLocation.UNKNOWN, "error");

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
     * Builds a new {@link XidNode} from the builder's state. If the builder's state is invalid,
     * errors are reported to {@code errorReporter} and {@link Builder#ERROR} is returned.
     */
    public XidNode build(ErrorReporter errorReporter) {
      Checkpoint checkpoint = errorReporter.checkpoint();
      // Verify that the command text is a single identifier literal.
      if (!BaseUtils.isDottedOrDashedIdent(commandText)) {
        errorReporter.report(sourceLocation, INVALID_XID_VALUE);
      }

      if (errorReporter.errorsSince(checkpoint)) {
        return ERROR;
      }

      XidNode node = new XidNode(id, sourceLocation, commandText);
      return node;
    }
  }
}
