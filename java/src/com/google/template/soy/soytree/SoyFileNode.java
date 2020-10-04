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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing a Soy file.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyFileNode extends AbstractParentSoyNode<SoyNode>
    implements SplitLevelTopNode<SoyNode> {

  /** A css path required by a Soy file. Constains both the source text and the resolved file. */
  public static final class CssPath {
    private final String sourcePath;
    private String resolvedPath;

    CssPath(String sourcePath) {
      this.sourcePath = checkNotNull(sourcePath);
    }

    private CssPath(CssPath origin) {
      this.sourcePath = origin.sourcePath;
      this.resolvedPath = origin.resolvedPath;
    }

    public String sourcePath() {
      return sourcePath;
    }

    public Optional<String> resolvedPath() {
      return Optional.ofNullable(resolvedPath);
    }

    public void setResolvedPath(String resolvedPath) {
      checkState(this.resolvedPath == null);
      this.resolvedPath = checkNotNull(resolvedPath);
    }

    CssPath copy() {
      return new CssPath(this);
    }
  }

  /** The name and location of the containing delegate package, or null if none. */
  @Nullable private final DelPackageDeclaration delPackage;

  /** This Soy file's namespace, or null if syntax version V1. */
  private final NamespaceDeclaration namespaceDeclaration;

  private final ImmutableList<AliasDeclaration> aliasDeclarations;

  private final TemplateNode.SoyFileHeaderInfo headerInfo;

  private final ImmutableList<Comment> comments;

  private final ImportsContext importsContext;

  private final ImmutableList<CssPath> requiredCssPaths;

  /**
   * @param id The id for this node.
   * @param sourceLocation The source location of the file.
   * @param namespaceDeclaration This Soy file's namespace and attributes. Nullable for backwards
   *     compatibility only.
   * @param headerInfo Other file metadata, (e.g. delpackages, aliases)
   */
  public SoyFileNode(
      int id,
      SourceLocation sourceLocation,
      NamespaceDeclaration namespaceDeclaration,
      SoyFileHeaderInfo headerInfo,
      ImmutableList<Comment> comments) {
    super(id, sourceLocation);
    this.headerInfo = headerInfo;
    this.delPackage = headerInfo.getDelPackage();
    this.namespaceDeclaration = namespaceDeclaration; // Immutable
    this.aliasDeclarations = headerInfo.getAliases(); // immutable
    this.comments = comments;
    this.importsContext = new ImportsContext();
    this.requiredCssPaths =
        namespaceDeclaration.getRequiredCssPaths().stream()
            .map(CssPath::new)
            .collect(toImmutableList());
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SoyFileNode(SoyFileNode orig, CopyState copyState) {
    super(orig, copyState);
    this.delPackage = orig.delPackage;
    this.namespaceDeclaration = orig.namespaceDeclaration.copy(copyState);
    this.aliasDeclarations = orig.aliasDeclarations; // immutable
    this.headerInfo = orig.headerInfo.copy();
    this.comments = orig.comments;
    // Imports context must be reset during edit-refresh (can't be set/cached in single file
    // passes).
    this.importsContext = new ImportsContext();
    this.requiredCssPaths =
        orig.requiredCssPaths.stream().map(CssPath::copy).collect(toImmutableList());
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
    return delPackage == null ? null : delPackage.name().identifier();
  }

  /** Returns info about the containing delegate package, or null if none. */
  @Nullable
  public DelPackageDeclaration getDelPackage() {
    return delPackage;
  }

  /** Returns this Soy file's namespace. */
  public String getNamespace() {
    return namespaceDeclaration.getNamespace();
  }

  /** Returns the parsed namespace for the file. */
  public NamespaceDeclaration getNamespaceDeclaration() {
    return namespaceDeclaration;
  }

  /**
   * Returns the CSS namespaces required by this file (usable in any template in this file).
   * TODO(b/151775233): This does not include CSS imports that also have css namespaces. To make CSS
   * collection work, this needs to be augmented.
   */
  public ImmutableList<String> getRequiredCssNamespaces() {
    return namespaceDeclaration.getRequiredCssNamespaces();
  }

  /** Returns the CSS namespaces required by this file (usable in any template in this file). */
  public ImmutableList<CssPath> getRequiredCssPaths() {
    return requiredCssPaths;
  }

  public ImmutableList<String> getRequireCss() {
    return ImmutableList.<String>builder()
        .addAll(getRequiredCssNamespaces())
        .addAll(getRequiredCssPaths().stream().map(CssPath::sourcePath).collect(toImmutableList()))
        .build();
  }

  /** Returns the CSS base namespace for this file (usable in any template in this file). */
  @Nullable
  public String getCssBaseNamespace() {
    return namespaceDeclaration.getCssBaseNamespace();
  }

  @Nullable
  public String getCssPrefix() {
    return namespaceDeclaration.getCssPrefix();
  }

  /** Returns the syntactic alias directives in the file. */
  public ImmutableList<AliasDeclaration> getAliasDeclarations() {
    return aliasDeclarations;
  }

  /** Returns the path to the source Soy file ("unknown" if not supplied). */
  public SourceFilePath getFilePath() {
    return getSourceLocation().getFilePath();
  }

  public ImmutableList<TemplateNode> getTemplates() {
    // No need to look recursively since we know these are all top level.
    return this.getChildren().stream()
        .filter(c -> c instanceof TemplateNode)
        .map(c -> (TemplateNode) c)
        .collect(ImmutableList.toImmutableList());
  }

  public ImmutableList<ImportNode> getImports() {
    // No need to look recursively since we know these are all top level.
    return this.getChildren().stream()
        .filter(c -> c instanceof ImportNode)
        .map(c -> (ImportNode) c)
        .collect(ImmutableList.toImmutableList());
  }

  /** Returns this Soy file's name. */
  @Nullable
  public String getFileName() {
    return getSourceLocation().getFileName();
  }

  /** Returns all comments in the entire Soy file (not just doc-level comments). */
  public ImmutableList<Comment> getComments() {
    return comments;
  }

  /** Resolves a qualified name against the aliases for this file. */
  public Identifier resolveAlias(Identifier identifier) {
    return headerInfo.resolveAlias(identifier);
  }

  public boolean aliasUsed(String alias) {
    return headerInfo.aliasUsed(alias);
  }

  public SoyFileHeaderInfo getHeaderInfo() {
    return headerInfo;
  }

  public SoyTypeRegistry getSoyTypeRegistry() {
    Preconditions.checkState(
        importsContext != null,
        "Called getSoyTypeRegistry() before ResolveProtoImportsPass was run.");
    return importsContext.getTypeRegistry();
  }

  public ImportsTemplateRegistry getTemplateRegistry() {
    Preconditions.checkState(
        importsContext != null,
        "Called getTemplateRegistry() before ResolveTemplateImportsPass was run.");
    return importsContext.getTemplateRegistry();
  }

  public boolean hasTemplateRegistry() {
    return importsContext.hasTemplateRegistry();
  }

  public ImportsContext getImportsContext() {
    return importsContext;
  }

  @Override
  public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (delPackage != null) {
      sb.append("{delpackage ").append(delPackage.name()).append("}\n");
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
