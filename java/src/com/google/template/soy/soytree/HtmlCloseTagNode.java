/*
 * Copyright 2016 Google Inc.
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
import com.google.template.soy.basetree.CopyState;

/**
 * An HtmlCloseTagNode represents a closing html tag.
 *
 * <p>For example, <code> </{$tag}> </code>.
 */
public final class HtmlCloseTagNode extends HtmlTagNode {

  public HtmlCloseTagNode(int id, TagName tagName, SourceLocation sourceLocation) {
    super(id, tagName, sourceLocation);
  }

  private HtmlCloseTagNode(HtmlCloseTagNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_CLOSE_TAG_NODE;
  }

  @Override
  public HtmlCloseTagNode copy(CopyState copyState) {
    return new HtmlCloseTagNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("</");
    for (int i = 0; i < numChildren(); i++) {
      StandaloneNode child = getChild(i);
      if (i != 0) {
        sb.append(' ');
      }
      sb.append(child.toSourceString());
    }
    sb.append('>');
    return sb.toString();
  }
}
