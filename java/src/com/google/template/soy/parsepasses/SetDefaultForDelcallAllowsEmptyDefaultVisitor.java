/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for setting the default value of CallDelegateNode.allowsEmptyDefault for all 'delcall's
 * where it is not specified by the user.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree or a Soy file. This pass mutates
 * {@code CallDelegateNode}s. There is no return value.
 *
 */
public class SetDefaultForDelcallAllowsEmptyDefaultVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Default value for CallDelegateNode.allowsEmptyDefault. */
  private final boolean defaultValueForAllowsEmptyDefault;


  public SetDefaultForDelcallAllowsEmptyDefaultVisitor(SyntaxVersion declaredSyntaxVersion) {
    // Note: For readability, purposely not simplifying.
    //noinspection RedundantConditionalExpression IntelliJ
    this.defaultValueForAllowsEmptyDefault =
        (declaredSyntaxVersion.num >= SyntaxVersion.V2_2.num) ? false : true;
  }


  @Override public Void exec(SoyNode soyNode) {
    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);
    return super.exec(soyNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {
    node.maybeSetAllowsEmptyDefault(defaultValueForAllowsEmptyDefault);
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
