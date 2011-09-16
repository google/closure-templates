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
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

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


  /** The name of the containing delegate package, or null if none. */
  private final String delPackageName;

  /** This Soy file's namespace, or null if syntax version V1. */
  private final String namespace;

  /** The autoescape mode for templates in this file that do not declare an autoescape mode. */
  private final AutoescapeMode defaultAutoescapeMode;

  /** The path to the source Soy file (null if not supplied). */
  private String filePath;

  /** This Soy file's name (null if not supplied). */
  private String fileName;

  /** Decomposes namespace command text into a namespace (group 1) and attributes (group 2). */
  private static final Pattern CMD_TEXT_PATTERN = Pattern.compile(
      "\\s* (" + BaseUtils.DOTTED_IDENT_RE + ") (\\s .*)?",
      Pattern.COMMENTS);

  /** The default autoescape mode if none is specified in the command text. */
  private static final AutoescapeMode DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE =
      AutoescapeMode.TRUE;

  /** Parser for the command text besides the namespace. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("namespace",
          new Attribute("autoescape", AutoescapeMode.getAttributeValues(),
                        DEFAULT_FILE_WIDE_DEFAULT_AUTOESCAPE_MODE.getAttributeValue()));


  /**
   * @param id The id for this node.
   * @param delpackageCmdText This Soy file's delegate package, or null if none.
   * @param namespaceCmdText This Soy file's namespace and attributes.
   *     Nullable for backwards compatibility only.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyFileNode(int id, @Nullable String delpackageCmdText, @Nullable String namespaceCmdText)
      throws SoySyntaxException {
    super(id);

    if (delpackageCmdText != null) {
      this.delPackageName = delpackageCmdText.trim();
      if (! BaseUtils.isDottedIdentifier(delPackageName)) {
        throw new SoySyntaxException("Invalid delegate package name \"" + delPackageName + "\".");
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
        throw new SoySyntaxException(
            "Invalid namespace command text \"" + namespaceCmdText + "\".");
      }
    }

    this.namespace = namespace;
    this.defaultAutoescapeMode = defaultAutoescapeMode;
    if (namespace == null) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } else if (!BaseUtils.isDottedIdentifier(namespace)) {
      throw new SoySyntaxException("Invalid namespace name \"" + namespace + "\".");
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected SoyFileNode(SoyFileNode orig) {
    super(orig);
    this.delPackageName = orig.delPackageName;
    this.namespace = orig.namespace;
    this.defaultAutoescapeMode = orig.defaultAutoescapeMode;
    this.filePath = orig.filePath;
    this.fileName = orig.fileName;
  }


  @Override public Kind getKind() {
    return Kind.SOY_FILE_NODE;
  }


  /** Returns the name of the containing delegate package, or null if none. */
  public String getDelPackageName() {
    return delPackageName;
  }


  /** Returns this Soy file's namespace, or null if syntax version V1. */
  public String getNamespace() {
    return namespace;
  }


  /** Returns the default autoescaping mode for contained templates. */
  public AutoescapeMode getDefaultAutoescapeMode() {
    return defaultAutoescapeMode;
  }


  /** @param filePath The path to the source Soy file. */
  public void setFilePath(String filePath) {
    int lastBangIndex = filePath.lastIndexOf('!');
    if (lastBangIndex != -1) {
      // This is a resource in a JAR file. Only keep everything after the bang.
      filePath = filePath.substring(lastBangIndex + 1);
    }
    this.filePath = filePath;
    int lastSlashIndex = CharMatcher.anyOf("/\\").lastIndexIn(filePath);
    if (lastSlashIndex != -1 && lastSlashIndex != filePath.length() - 1) {
      fileName = filePath.substring(lastSlashIndex + 1);
    } else {
      fileName = filePath;
    }
  }


  /** Returns the path to the source Soy file (null if not supplied). */
  public String getFilePath() {
    return filePath;
  }


  /** Returns this Soy file's name (null if not supplied). */
  public String getFileName() {
    return fileName;
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (namespace != null) {
      sb.append("{namespace ").append(namespace).append("}\n");
    }

    for (SoyNode child : getChildren()) {
      sb.append("\n");
      sb.append(child.toSourceString());
    }

    return sb.toString();
  }


  @Override public SoyFileNode clone() {
    return new SoyFileNode(this);
  }

}
