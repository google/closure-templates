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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node representing a call to a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallBasicNode extends CallNode {

  /** The full name of the template being called, after namespace / alias resolution. */
  private final String fullCalleeName;

  /** The callee name string as it appears in the source code. */
  private final Identifier sourceCalleeName;

  /**
   * The list of params that need to be type checked when this node is run. All the params that
   * could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  @Nullable private Predicate<String> paramsToRuntimeTypeCheck = null;

  public CallBasicNode(
      int id,
      SourceLocation location,
      Identifier sourceCalleeName,
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
        case "key":
        case MessagePlaceholders.PHNAME_ATTR:
        case MessagePlaceholders.PHEX_ATTR:
          // Parsed in CallNode.
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              name,
              "call",
              ImmutableList.of(
                  "data", MessagePlaceholders.PHNAME_ATTR, MessagePlaceholders.PHEX_ATTR));
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
    return sourceCalleeName.identifier();
  }

  @Override
  public SourceLocation getSourceCalleeLocation() {
    return sourceCalleeName.location();
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    return fullCalleeName;
  }

  /**
   * Sets the names of the params that require runtime type checking against callee's types.
   *
   * <p>This mechanism is used by the TOFU runtime only to save some work when calling templates.
   */
  public void setParamsToRuntimeCheck(Predicate<String> paramNames) {
    checkState(this.paramsToRuntimeTypeCheck == null);
    this.paramsToRuntimeTypeCheck = checkNotNull(paramNames);
  }

  @Override
  public Predicate<String> getParamsToRuntimeCheck(String calleeTemplateName) {
    return paramsToRuntimeTypeCheck == null ? Predicates.alwaysTrue() : paramsToRuntimeTypeCheck;
  }

  @Override
  public String getCommandText() {
    StringBuilder commandText = new StringBuilder(getSourceCalleeName());

    if (isPassingAllData()) {
      commandText.append(" data=\"all\"");
    } else if (getDataExpr() != null) {
      commandText.append(" data=\"").append(getDataExpr().toSourceString()).append('"');
    }
    if (getUserSuppliedPhName() != null) {
      commandText.append(" phname=\"").append(getUserSuppliedPhName()).append('"');
    }
    if (getUserSuppliedPhExample() != null) {
      commandText.append(" phex=\"").append(getUserSuppliedPhExample()).append('"');
    }

    return commandText.toString();
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
