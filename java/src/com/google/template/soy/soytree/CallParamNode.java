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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;

/**
 * Abstract node representing a 'param'.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public abstract class CallParamNode extends AbstractCommandNode {

  /** The param key. */
  private final Identifier key;

  // This parameter may be synthetically generated from an HTML attribute. Within velogging,
  // we need to reconstruct the original name.
  private String originalName = null;

  // These will be filled in when the param is an element composition attribute and is also a
  // logging function. They are copies of the original function and conditional nodes.
  private ExprRootNode loggingFunction;
  private ExprRootNode loggingConditional;

  protected CallParamNode(int id, SourceLocation sourceLocation, Identifier key) {
    super(id, sourceLocation, "param");
    this.key = checkNotNull(key);
    this.loggingFunction = null;
    this.loggingConditional = null;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected CallParamNode(CallParamNode orig, CopyState copyState) {
    super(orig, copyState);
    this.key = orig.key;
    this.originalName = orig.originalName;
  }

  public void setLoggingFunction(FunctionNode loggingFunction) {
    this.loggingFunction = new ExprRootNode(loggingFunction);
  }

  public ExprRootNode getLoggingFunction() {
    return loggingFunction;
  }

  public void setLoggingConditional(ExprNode loggingConditional) {
    this.loggingConditional = new ExprRootNode(loggingConditional);
  }

  public ExprRootNode getLoggingConditional() {
    return loggingConditional;
  }

  /** Returns the param key. */
  public Identifier getKey() {
    return key;
  }

  public String getOriginalName() {
    return originalName;
  }

  public void setOriginalName(String originalName) {
    this.originalName = originalName;
  }

  @Override
  public CallNode getParent() {
    return (CallNode) super.getParent();
  }
}
