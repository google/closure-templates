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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Node representing a Soy file.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyFileNode extends AbstractParentSoyNode<TemplateNode>
    implements SplitLevelTopNode<TemplateNode> {

  private static final SoyError ALIAS_USED_WITHOUT_NAMESPACE =
      SoyError.of(
          "''{alias...'' can only be used in files with valid ''{namespace ...'' "
              + "declarations");

  private static final SoyError INVALID_ALIAS_FOR_LAST_PART_OF_NAMESPACE =
      SoyError.of("Not allowed to alias the last part of the file''s namespace ({0}) "
          + "to another namespace ({1}).");

  private static final SoyError DIFFERENT_NAMESPACES_WITH_SAME_ALIAS =
      SoyError.of("Found two namespaces with the same alias (''{0}'' and ''{1}'').");

  public static final Predicate<SoyFileNode> MATCH_SRC_FILENODE = new Predicate<SoyFileNode>() {
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

  /**
   * @param id The id for this node.
   * @param filePath The path to the Soy source file.
   * @param soyFileKind The kind of this Soy file.
   * @param errorReporter For reporting syntax errors.
   * @param delPackageName This Soy file's delegate package, or null if none.
   * @param namespaceDeclaration This Soy file's namespace and attributes. Nullable for backwards
   *     compatibility only.
   * @param aliases The command texts of the 'alias' declarations. Allowed to be null.
   */
  public SoyFileNode(
      int id,
      String filePath,
      SoyFileKind soyFileKind,
      ErrorReporter errorReporter,
      @Nullable String delPackageName,
      NamespaceDeclaration namespaceDeclaration,
      Collection<AliasDeclaration> aliases) {
    super(id, new SourceLocation(filePath));
    this.soyFileKind = soyFileKind;
    this.delPackageName = delPackageName;
    this.namespaceDeclaration = namespaceDeclaration;

    Map<String, String> tempAliasToNamespaceMap = Maps.newLinkedHashMap();
    String aliasForFileNamespace =
        namespaceDeclaration.isDefined()
            ? BaseUtils.extractPartAfterLastDot(namespaceDeclaration.getNamespace())
            : null;
    for (AliasDeclaration aliasDeclaration : aliases) {
      if (!namespaceDeclaration.isDefined()) {
        errorReporter.report(aliasDeclaration.getLocation(), ALIAS_USED_WITHOUT_NAMESPACE);
      }
      String aliasNamespace = aliasDeclaration.getNamespace();
      String alias = aliasDeclaration.getAlias();
      if (alias.equals(aliasForFileNamespace)
          && !aliasNamespace.equals(namespaceDeclaration.getNamespace())) {
        errorReporter.report(
            aliasDeclaration.getLocation(),
            INVALID_ALIAS_FOR_LAST_PART_OF_NAMESPACE,
            namespaceDeclaration.getNamespace(),
            aliasNamespace);
      }
      if (tempAliasToNamespaceMap.containsKey(alias)) {
        errorReporter.report(
            getSourceLocation(),
            DIFFERENT_NAMESPACES_WITH_SAME_ALIAS,
            tempAliasToNamespaceMap.get(alias),
            aliasNamespace);
      }
      tempAliasToNamespaceMap.put(alias, aliasNamespace);
    }
    this.aliasToNamespaceMap = ImmutableMap.copyOf(tempAliasToNamespaceMap);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private SoyFileNode(SoyFileNode orig, CopyState copyState) {
    super(orig, copyState);
    this.soyFileKind = orig.soyFileKind;
    this.delPackageName = orig.delPackageName;
    this.namespaceDeclaration = orig.namespaceDeclaration; // Immutable
    this.aliasToNamespaceMap = orig.aliasToNamespaceMap; // immutable
  }


  @Override public Kind getKind() {
    return Kind.SOY_FILE_NODE;
  }


  /** Returns the kind of this Soy file. */
  public SoyFileKind getSoyFileKind() {
    return soyFileKind;
  }


  /** Returns the name of the containing delegate package, or null if none. */
  @Nullable public String getDelPackageName() {
    return delPackageName;
  }


  /** Returns this Soy file's namespace, or null if syntax version V1. */
  @Nullable public String getNamespace() {
    return namespaceDeclaration.getNamespace();
  }


  /** Returns the default autoescaping mode for contained templates. */
  public AutoescapeMode getDefaultAutoescapeMode() {
    return namespaceDeclaration.getDefaultAutoescapeMode();
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


  /** Returns the path to the source Soy file ("unknown" if not supplied). */
  public String getFilePath() {
    return getSourceLocation().getFilePath();
  }


  /** Returns this Soy file's name (null if not supplied). */
  @Nullable public String getFileName() {
    return getSourceLocation().getFileName();
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (delPackageName != null) {
      sb.append("{delpackage ").append(delPackageName).append("}\n");
    }
    if (namespaceDeclaration.isDefined()) {
      sb.append("{namespace ").append(namespaceDeclaration.getNamespace());
      if (this.namespaceDeclaration.getAutoescapeMode().isPresent()) {
        sb
            .append(" autoescape=\"")
            .append(this.namespaceDeclaration.getAutoescapeMode().get().getAttributeValue())
            .append("\"");
      }
      sb.append("}\n");
    }

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


  @Override public SoyFileSetNode getParent() {
    return (SoyFileSetNode) super.getParent();
  }


  @Override public SoyFileNode copy(CopyState copyState) {
    return new SoyFileNode(this, copyState);
  }

}
