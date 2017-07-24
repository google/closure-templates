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
   * The full name of the template being called, after namespace / alias resolution.
   *
   * <p>Not final. The contextual autoescaper can rewrite the callee name, if the same callee
   * template is called into from two different contexts, and the autoescaper needs to clone a
   * template and retarget the call.
   */
  private String fullCalleeName;

  /** The callee name string as it appears in the source code. */
  private String sourceCalleeName;

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
      String sourceCalleeName,
      String fullCalleeName,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "call", attributes, errorReporter);
    checkArgument(BaseUtils.isDottedIdentifier(fullCalleeName));

    this.sourceCalleeName = sourceCalleeName;
    this.fullCalleeName = fullCalleeName;

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
    this.fullCalleeName = orig.fullCalleeName;
    this.paramsToRuntimeTypeCheck = orig.paramsToRuntimeTypeCheck;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }

  /** Returns the callee name string as it appears in the source code. */
  public String getSourceCalleeName() {
    return sourceCalleeName;
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    return fullCalleeName;
  }

  /** Do not call this method outside the contextual autoescaper. */
  public void setNewCalleeName(String name) {
    checkArgument(BaseUtils.isDottedIdentifier(name));
    this.sourceCalleeName = name;
    this.fullCalleeName = name;
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
    StringBuilder commandText = new StringBuilder(sourceCalleeName);

    if (isPassingAllData()) {
      commandText.append(" data=\"all\"");
    } else if (getDataExpr() != null) {
      commandText.append(" data=\"").append(getDataExpr().toSourceString()).append('"');
    }
    if (getUserSuppliedPhName() != null) {
      commandText.append(" phname=\"").append(getUserSuppliedPhName()).append('"');
    }

    return commandText.toString();
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
