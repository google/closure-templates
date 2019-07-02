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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import javax.annotation.Nullable;

/**
 * Node representing a Soy file.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyFileNode extends AbstractParentSoyNode<TemplateNode>
    implements SplitLevelTopNode<TemplateNode> {

  /** The name of the containing delegate package, or null if none. */
  @Nullable private final String delPackageName;

  /** This Soy file's namespace, or null if syntax version V1. */
  private final NamespaceDeclaration namespaceDeclaration;

  private final ImmutableList<AliasDeclaration> aliasDeclarations;

  private final TemplateNode.SoyFileHeaderInfo headerInfo;

  /**
   * @param id The id for this node.
   * @param filePath The path to the Soy source file.
   * @param namespaceDeclaration This Soy file's namespace and attributes. Nullable for backwards
   *     compatibility only.
   * @param headerInfo Other file metadata, (e.g. delpackages, aliases)
   */
  public SoyFileNode(
      int id,
      String filePath,
      NamespaceDeclaration namespaceDeclaration,
      TemplateNode.SoyFileHeaderInfo headerInfo) {
    super(id, new SourceLocation(filePath));
    this.headerInfo = headerInfo;
    this.delPackageName = headerInfo.getDelPackageName();
    this.namespaceDeclaration = namespaceDeclaration; // Immutable
    this.aliasDeclarations = headerInfo.getAliases(); // immutable
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SoyFileNode(SoyFileNode orig, CopyState copyState) {
    super(orig, copyState);
    this.delPackageName = orig.delPackageName;
    this.namespaceDeclaration = orig.namespaceDeclaration.copy(copyState);
    this.aliasDeclarations = orig.aliasDeclarations; // immutable
    this.headerInfo = orig.headerInfo.copy();
  }


  @Override
  public Kind getKind() {
    return Kind.SOY_FILE_NODE;
  }

  /** Returns the attibutes of the namespace tag. */
  public ImmutableList<CommandTagAttribute> getNamespaceAttributes() {
    return namespaceDeclaration.attrs;
  }

  /** Returns the name of the containing delegate package, or null if none. */
  @Nullable
  public String getDelPackageName() {
    return delPackageName;
  }

  /** Returns this Soy file's namespace. */
  public String getNamespace() {
    return namespaceDeclaration.getNamespace();
  }

  /** Returns the parsed namespace for the file. */
  public NamespaceDeclaration getNamespaceDeclaration() {
    return namespaceDeclaration;
  }

  /** Returns the CSS namespaces required by this file (usable in any template in this file). */
  public ImmutableList<String> getRequiredCssNamespaces() {
    return namespaceDeclaration.getRequiredCssNamespaces();
  }

  /** Returns the CSS base namespace for this file (usable in any template in this file). */
  @Nullable
  public String getCssBaseNamespace() {
    return namespaceDeclaration.getCssBaseNamespace();
  }

  /** Returns the syntactic alias directives in the file. */
  public ImmutableList<AliasDeclaration> getAliasDeclarations() {
    return aliasDeclarations;
  }

  /** Returns the path to the source Soy file ("unknown" if not supplied). */
  public String getFilePath() {
    return getSourceLocation().getFilePath();
  }

  /** Returns this Soy file's name. */
  @Nullable
  public String getFileName() {
    return getSourceLocation().getFileName();
  }

  /** Resolves a qualified name against the aliases for this file. */
  public String resolveAlias(String fullName) {
    return headerInfo.resolveAlias(fullName);
  }

  public boolean aliasUsed(String alias) {
    return headerInfo.aliasUsed(alias);
  }

  /** @deprecated SoyFileNodes don't have source locations. */
  @Deprecated
  @Override
  public SourceLocation getSourceLocation() {
    return super.getSourceLocation();
  }

  @Override
  public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (delPackageName != null) {
      sb.append("{delpackage ").append(delPackageName).append("}\n");
    }
    sb.append(namespaceDeclaration.toSourceString());

    if (!aliasDeclarations.isEmpty()) {
      sb.append("\n");
      for (AliasDeclaration aliasDeclaration : aliasDeclarations) {
        String alias = aliasDeclaration.alias().identifier();
        String aliasNamespace = aliasDeclaration.namespace().identifier();
        if (aliasNamespace.equals(alias) || aliasNamespace.endsWith("." + alias)) {
          sb.append("{alias ").append(aliasNamespace).append("}\n");
        } else {
          sb.append("{alias ").append(aliasNamespace).append(" as ").append(alias).append("}\n");
        }
      }
    }

    for (SoyNode child : getChildren()) {
      sb.append("\n");
      sb.append(child.toSourceString());
    }

    return sb.toString();
  }

  @Override
  public SoyFileSetNode getParent() {
    return (SoyFileSetNode) super.getParent();
  }

  @Override
  public SoyFileNode copy(CopyState copyState) {
    return new SoyFileNode(this, copyState);
  }
}
