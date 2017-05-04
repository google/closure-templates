/*
 * Copyright 2011 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node representing a call to a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallBasicNode extends CallNode {

  /**
   * The callee name string as it appears in the source code.
   *
   * <p>Not final. The contextual autoescaper can rewrite the callee name, if the same callee
   * template is called into from two different contexts, and the autoescaper needs to clone a
   * template and retarget the call.
   */
  private String sourceCalleeName;

  /** The full name of the template being called. Briefly null before being set. */
  // TODO(user): Fold SetFullCalleeVisitor into parser, remove this field.
  private String calleeName;

  /**
   * The list of params that need to be type checked when this node is run. All the params that
   * could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  @Nullable private ImmutableList<TemplateParam> paramsToRuntimeTypeCheck = null;

  public CallBasicNode(
      int id,
      SourceLocation location,
      String calleeName,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "call", attributes);
    this.sourceCalleeName = calleeName;
    this.calleeName = calleeName;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
        case "phname":
          // Parsed in CallNode.
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              name,
              "call",
              ImmutableList.of("data", "phname"));
      }
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallBasicNode(CallBasicNode orig, CopyState copyState) {
    super(orig, copyState);
    this.sourceCalleeName = orig.sourceCalleeName;
    this.calleeName = orig.calleeName;
    this.paramsToRuntimeTypeCheck = orig.paramsToRuntimeTypeCheck;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }

  /** Returns the callee name string as it appears in the source code. */
  public String getSrcCalleeName() {
    return sourceCalleeName;
  }

  /** Do not call this method outside the contextual autoescaper. */
  public void setSrcCalleeName(String sourceCalleeName) {
    this.sourceCalleeName = sourceCalleeName;
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  // TODO(user): remove
  public String getCalleeName() {
    return calleeName;
  }

  /**
   * Sets the full name of the template being called (must not be a partial name).
   *
   * @param calleeName The full name of the template being called.
   */
  // TODO(user): Remove.
  public void setCalleeName(String calleeName) {
    checkArgument(BaseUtils.isDottedIdentifier(calleeName));
    this.calleeName = calleeName;
  }

  /** Sets the names of the params that require runtime type checking against callee's types. */
  public void setParamsToRuntimeCheck(Collection<TemplateParam> paramNames) {
    checkState(this.paramsToRuntimeTypeCheck == null);
    this.paramsToRuntimeTypeCheck = ImmutableList.copyOf(paramNames);
  }

  @Override
  public ImmutableList<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    return paramsToRuntimeTypeCheck == null ? callee.getParams() : paramsToRuntimeTypeCheck;
  }

  @Override
  public String getCommandText() {
    String commandText = sourceCalleeName;
    if (isPassingAllData()) {
      commandText += " data=\"all\"";
    } else if (getDataExpr() != null) {
      commandText += " data=\"" + getDataExpr().toSourceString() + '"';
    }
    if (getUserSuppliedPhName() != null) {
      commandText += " phname=\"" + getUserSuppliedPhName() + '"';
    }

    return commandText;
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
