/*
 * Copyright 2012 Google Inc.
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
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing the 'log' statement.
 *
 */
public final class LogNode extends AbstractBlockCommandNode
    implements StandaloneNode, StatementNode {

  public LogNode(int id, SourceLocation sourceLocation) {
    super(id, sourceLocation, "log");
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private LogNode(LogNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.LOG_NODE;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public LogNode copy(CopyState copyState) {
    return new LogNode(this, copyState);
  }
}
