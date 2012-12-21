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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoyFileKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Node representing a Soy file.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SoyFileNode extends AbstractParentSoyNode<TemplateNode>
    implements SplitLevelTopNode<TemplateNode> {


  /** Decomposes namespace command text into a namespace (group 1) and attributes (group 2). */
  private static final Pattern CMD_TEXT_PATTERN = Pattern.compile(
      "\\s* (" + BaseUtils.DOTTED_IDENT_RE + ") (\\s .*)?", Pattern.COMMENTS);

  /** The default autoescape mode if none is specified in the command text. */
  private static final AutoescapeMode DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE =
      AutoescapeMode.TRUE;

  /** Parser for the command text besides the namespace. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("namespace",
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(),
              DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE.getAttributeValue()));


  /** The kind of this Soy file */
  private final SoyFileKind soyFileKind;

  /** The name of the containing delegate package, or null if none. */
  @Nullable private final String delPackageName;

  /** This Soy file's namespace, or null if syntax version V1. */
  @Nullable private final String namespace;

  /** The autoescape mode for templates in this file that do not declare an autoescape mode. */
  private final AutoescapeMode defaultAutoescapeMode;

  /** Map from aliases to namespaces for this file. */
  private final ImmutableMap<String, String> aliasToNamespaceMap;

  /** This Soy file's name (null if not supplied). */
  @Nullable private String fileName;


  /**
   * @param id The id for this node.
   * @param soyFileKind The kind of this Soy file.
   * @param delpackageCmdText This Soy file's delegate package, or null if none.
   * @param namespaceCmdText This Soy file's namespace and attributes. Nullable for backwards
   *     compatibility only.
   * @param aliasCmdTexts The command texts of the 'alias' declarations. Allowed to be null.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyFileNode(
      int id, SoyFileKind soyFileKind, @Nullable String delpackageCmdText,
      @Nullable String namespaceCmdText, @Nullable List<String> aliasCmdTexts)
      throws SoySyntaxException {
    super(id);

    this.soyFileKind = soyFileKind;

    if (delpackageCmdText != null) {
      this.delPackageName = delpackageCmdText.trim();
      if (! BaseUtils.isDottedIdentifier(delPackageName)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid delegate package name \"" + delPackageName + "\".");
      }
    } else {
      this.delPackageName = null;
    }

    String namespace = null;
    AutoescapeMode defaultAutoescapeMode = DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE;

    if (namespaceCmdText != null) {
      Matcher matcher = CMD_TEXT_PATTERN.matcher(namespaceCmdText);
      if (matcher.matches()) {
        namespace = matcher.group(1);
        String attributeText = matcher.group(2);
        if (attributeText != null) {
          attributeText = attributeText.trim();
          Map<String, String> attributes = ATTRIBUTES_PARSER.parse(attributeText);
          if (attributes.containsKey("autoescape")) {
            defaultAutoescapeMode = AutoescapeMode.forAttributeValue(attributes.get("autoescape"));
          }
        }
      } else {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Invalid namespace command text \"" + namespaceCmdText + "\".");
      }
    }

    this.namespace = namespace;
    this.defaultAutoescapeMode = defaultAutoescapeMode;
    if (namespace == null) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } else if (!BaseUtils.isDottedIdentifier(namespace)) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid namespace name \"" + namespace + "\".");
    }

    if (aliasCmdTexts != null) {
      Preconditions.checkNotNull(this.namespace);
      String aliasForFileNamespace = BaseUtils.extractPartAfterLastDot(this.namespace);
      Map<String, String> tempAliasToNamespaceMap = Maps.newLinkedHashMap();
      for (String aliasCmdText : aliasCmdTexts) {
        // Note: The form of the 'alias' tag with "as ..." is currently disabled in the parser.
        String aliasNamespace = aliasCmdText.trim();
        Preconditions.checkArgument(BaseUtils.isDottedIdentifier(aliasNamespace));
        String alias = BaseUtils.extractPartAfterLastDot(aliasNamespace);
        if (alias.equals(aliasForFileNamespace) && ! aliasNamespace.equals(this.namespace)) {
          throw SoySyntaxException.createWithoutMetaInfo(String.format(
              "Not allowed to alias the last part of the file's namespace to some other namespace" +
                  " (file's namespace is \"%s\", while aliased namespace is \"%s\").",
              this.namespace, aliasNamespace));
        }
        if (tempAliasToNamespaceMap.containsKey(alias)) {
          throw SoySyntaxException.createWithoutMetaInfo(String.format(
              "Found 2 namespaces with the same alias (\"%s\" and \"%s\").",
              tempAliasToNamespaceMap.get(alias), aliasNamespace));
        }
        tempAliasToNamespaceMap.put(alias, aliasNamespace);
      }
      aliasToNamespaceMap = ImmutableMap.copyOf(tempAliasToNamespaceMap);
    } else {
      aliasToNamespaceMap = ImmutableMap.of();
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected SoyFileNode(SoyFileNode orig) {
    super(orig);
    this.soyFileKind = orig.soyFileKind;
    this.delPackageName = orig.delPackageName;
    this.namespace = orig.namespace;
    this.defaultAutoescapeMode = orig.defaultAutoescapeMode;
    this.aliasToNamespaceMap = orig.aliasToNamespaceMap;  // immutable
    this.fileName = orig.fileName;
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
    return namespace;
  }


  /** Returns the default autoescaping mode for contained templates. */
  public AutoescapeMode getDefaultAutoescapeMode() {
    return defaultAutoescapeMode;
  }


  /** Returns the map from aliases to namespaces for this file. */
  public ImmutableMap<String, String> getAliasToNamespaceMap() {
    return aliasToNamespaceMap;
  }


  @Override public void setSourceLocation(SourceLocation srcLoc) {
    super.setSourceLocation(srcLoc);

    String filePath = srcLoc.getFilePath();
    int lastSlashIndex = CharMatcher.anyOf("/\\").lastIndexIn(filePath);
    if (lastSlashIndex != -1 && lastSlashIndex != filePath.length() - 1) {
      fileName = filePath.substring(lastSlashIndex + 1);
    } else {
      fileName = filePath;
    }
  }


  /** @param filePath The path to the source Soy file. */
  public void setFilePath(String filePath) {
    setSourceLocation(new SourceLocation(filePath, 0));
  }


  /** Returns the path to the source Soy file ("unknown" if not supplied). */
  public String getFilePath() {
    return getSourceLocation().getFilePath();
  }


  /** Returns this Soy file's name (null if not supplied). */
  @Nullable public String getFileName() {
    return fileName;
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (delPackageName != null) {
      sb.append("{delpackage ").append(delPackageName).append("}\n");
    }
    if (namespace != null) {
      sb.append("{namespace ").append(namespace).append("}\n");
    }

    if (aliasToNamespaceMap.size() > 0) {
      sb.append("\n");
      for (String aliasNamespace : aliasToNamespaceMap.values()) {
        sb.append("{alias ").append(aliasNamespace).append("}\n");
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


  @Override public SoyFileNode clone() {
    return new SoyFileNode(this);
  }

}
