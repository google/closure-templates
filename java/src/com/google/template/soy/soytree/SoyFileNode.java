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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Node representing a Soy file.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyFileNode extends AbstractParentSoyNode<TemplateNode>
    implements SplitLevelTopNode<TemplateNode> {

  public static final Predicate<SoyFileNode> MATCH_SRC_FILENODE =
      new Predicate<SoyFileNode>() {
        @Override
        public boolean apply(@Nullable SoyFileNode input) {
          return input != null && input.getSoyFileKind() == SoyFileKind.SRC;
        }
      };

  /** The kind of this Soy file */
  private final SoyFileKind soyFileKind;

  /** The name of the containing delegate package, or null if none. */
  @Nullable private final String delPackageName;

  /** This Soy file's namespace, or null if syntax version V1. */
  private final NamespaceDeclaration namespaceDeclaration;

  /** Map from aliases to namespaces for this file. */
  private final ImmutableMap<String, String> aliasToNamespaceMap;

  private final ImmutableList<AliasDeclaration> aliasDeclarations;

  private final String filePath;
  private final String fileName;

  /**
   * @param id The id for this node.
   * @param filePath The path to the Soy source file.
   * @param soyFileKind The kind of this Soy file.
   * @param namespaceDeclaration This Soy file's namespace and attributes. Nullable for backwards
   *     compatibility only.
   * @param headerInfo Other file metadata, (e.g. delpackages, aliases)
   */
  public SoyFileNode(
      int id,
      String filePath,
      SoyFileKind soyFileKind,
      NamespaceDeclaration namespaceDeclaration,
      TemplateNode.SoyFileHeaderInfo headerInfo) {
    super(id, null /* there is no source location. */);
    this.filePath = checkNotNull(filePath);
    this.fileName = SourceLocation.fileNameFromPath(filePath);
    this.soyFileKind = soyFileKind;
    this.delPackageName = headerInfo.delPackageName;
    this.namespaceDeclaration = namespaceDeclaration; // Immutable
    this.aliasDeclarations = headerInfo.aliasDeclarations; // immutable
    this.aliasToNamespaceMap = headerInfo.aliasToNamespaceMap; // immutable
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SoyFileNode(SoyFileNode orig, CopyState copyState) {
    super(orig, copyState);
    this.soyFileKind = orig.soyFileKind;
    this.filePath = orig.filePath;
    this.fileName = orig.fileName;
    this.delPackageName = orig.delPackageName;
    this.namespaceDeclaration = orig.namespaceDeclaration; // Immutable
    this.aliasDeclarations = orig.aliasDeclarations; // immutable
    this.aliasToNamespaceMap = orig.aliasToNamespaceMap; // immutable
  }

  @Override
  public Kind getKind() {
    return Kind.SOY_FILE_NODE;
  }

  /** Returns the kind of this Soy file. */
  public SoyFileKind getSoyFileKind() {
    return soyFileKind;
  }

  /** Returns the name of the containing delegate package, or null if none. */
  @Nullable
  public String getDelPackageName() {
    return delPackageName;
  }

  /** Returns this Soy file's namespace, or null if syntax version V1. */
  @Nullable
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

  /** Returns the map from aliases to namespaces for this file. */
  public ImmutableMap<String, String> getAliasToNamespaceMap() {
    return aliasToNamespaceMap;
  }

  /** Returns the syntactic alias directives in the file. For semantics, use aliasToNamespaceMap. */
  public ImmutableList<AliasDeclaration> getAliasDeclarations() {
    return aliasDeclarations;
  }

  /** Returns the path to the source Soy file ("unknown" if not supplied). */
  public String getFilePath() {
    return filePath;
  }

  /** Returns this Soy file's name. */
  @Nullable
  public String getFileName() {
    return fileName;
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

    if (!aliasToNamespaceMap.isEmpty()) {
      sb.append("\n");
      for (Map.Entry<String, String> entry : aliasToNamespaceMap.entrySet()) {
        String alias = entry.getKey();
        String aliasNamespace = entry.getValue();
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
