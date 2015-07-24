/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.html;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.AbstractSoyNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * A Node that holds a {@link PrintNode} in an HTML context, either within a tag (as an attribute)
 * or as the child of an HTML element. Note that this is not used for {@link PrintNode}s in an
 * attribute's value, as those are treated as simple strings.
 */
public final class HtmlPrintNode extends AbstractSoyNode
  implements StandaloneNode {

  /**
   * The possible contexts where the print node might appear. In all other locations, the print
   * node is treated as a normal print node (producing plain text) and does not have an associated
   * HtmlPrintNode.
   */
  public enum Context {
    HTML_TAG,
    HTML_PCDATA
  }

  private final PrintNode printNode;

  private final Context context;

  /**
   * @param id The id for this node.
   * @param printNode A printNode.
   * @param context The context in which printNode appears.
   * @param sourceLocation The node's source location.
   */
  public HtmlPrintNode(int id, PrintNode printNode, Context context,
      SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.printNode = printNode;
    this.context = context;
  }

  private HtmlPrintNode(HtmlPrintNode orig, CopyState copyState) {
    super(orig, copyState);
    this.printNode = orig.printNode;
    this.context = orig.context;
  }

  public Context getContext() {
    return context;
  }

  @Override public Kind getKind() {
    return Kind.HTML_PRINT_NODE;
  }

  public PrintNode getPrintNode() {
    return printNode;
  }

  @Override public String toSourceString() {
    return printNode.toSourceString();
  }

  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlPrintNode(this, copyState);
  }
  
}
