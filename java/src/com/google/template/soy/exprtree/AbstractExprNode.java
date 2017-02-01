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

package com.google.template.soy.exprtree;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.AbstractNode;
import com.google.template.soy.basetree.CopyState;

/**
 * Abstract implementation of an ExprNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class AbstractExprNode extends AbstractNode implements ExprNode {

  private final SourceLocation sourceLocation;

  @Override
  public ParentExprNode getParent() {
    return (ParentExprNode) super.getParent();
  }

  protected AbstractExprNode(SourceLocation sourceLocation) {
    this.sourceLocation = Preconditions.checkNotNull(sourceLocation);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected AbstractExprNode(AbstractExprNode orig, CopyState copyState) {
    super(orig, copyState);
    this.sourceLocation = orig.sourceLocation;
  }

  /**
   * TODO(user): The quality of source locations in expression nodes is not great. These source
   * locations are computed by the expression parser, and they depend on the parentSourceLocation
   * that is passed in to the ExpressionParser constructor. The quality of these
   * parentSourceLocations varies. Callers often perform regex-based munging of expression texts
   * before passing them to the ExpressionParser (example: {@code
   * CallParamNode.Builder#parseCommandTextHelper}), which implies that the parentSourceLocation
   * passed along with the munged text is inaccurate.
   *
   * <p>Until this is fixed, source locations obtained from expression nodes should be treated as
   * best-effort only.
   */
  @Override
  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }
}
