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
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

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


  /** This Soy file's namespace, or null if syntax version V1. */
  private final String namespace;

  /** The path to the source Soy file (null if not supplied). */
  private String filePath;

  /** This Soy file's name (null if not supplied). */
  private String fileName;


  /**
   * @param id The id for this node.
   * @param namespace This Soy file's namespace. Nullable for backwards compatibility only.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyFileNode(String id, @Nullable String namespace) throws SoySyntaxException {
    super(id);

    this.namespace = namespace;
    if (namespace == null) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } else if (!BaseUtils.isDottedIdentifier(namespace)) {
      throw new SoySyntaxException("Invalid namespace \"" + namespace + "\".");
    }
  }


  /** Returns this Soy file's namespace, or null if syntax version V1. */
  public String getNamespace() {
    return namespace;
  }

  /** @param filePath The path to the source Soy file. */
  public void setFilePath(String filePath) {
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

}
