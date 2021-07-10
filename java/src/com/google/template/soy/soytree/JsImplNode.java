/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyNode.Kind;
import java.util.List;

/** Js implementation for an extern. */
public final class JsImplNode extends ExternImplNode {
  private static final SoyErrorKind INVALID_IMPL_ATTRIBUTE =
      SoyErrorKind.of("''{0}'' is not a valid attribute.");

  private static final String NAMESPACE = "namespace";
  private static final String FUNCTION = "function";
  public static final String FIELDS = String.format("%s,%s", NAMESPACE, FUNCTION);
  private static final SoyErrorKind UNEXPECTED_ARGS =
      SoyErrorKind.of("JS implementations require attributes" + JsImplNode.FIELDS + " .");

  private final ImmutableList<CommandTagAttribute> attributes;

  // Stored separately from {@code attributes} for convenience.
  private CommandTagAttribute module;
  private CommandTagAttribute function;

  public JsImplNode(
      int id,
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, sourceLocation, "jsimpl");

    if (attributes.size() != 2) {
      errorReporter.report(sourceLocation, UNEXPECTED_ARGS);
    }
    attributes.stream()
        .filter(attr -> !(attr.hasName(NAMESPACE) || attr.hasName(FUNCTION)))
        .findAny()
        .ifPresent(
            invalidAttr ->
                errorReporter.report(
                    invalidAttr.getSourceLocation(),
                    INVALID_IMPL_ATTRIBUTE,
                    invalidAttr.getName()));

    this.attributes = ImmutableList.copyOf(attributes);
    initAttributes();
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private JsImplNode(JsImplNode orig, CopyState copyState) {
    super(orig, copyState);
    this.attributes =
        orig.attributes.stream()
            .map(origAttr -> origAttr.copy(copyState))
            .collect(toImmutableList());
    initAttributes();
  }

  /**
   * Pulls out relevant attributes into class fields for quick reference. Should only be used in
   * constructors.
   */
  private final void initAttributes() {
    for (CommandTagAttribute attr : attributes) {
      if (attr.hasName(NAMESPACE)) {
        this.module = attr;
      } else if (attr.hasName(FUNCTION)) {
        this.function = attr;
      }
    }
  }

  @Override
  public Kind getKind() {
    return Kind.JS_IMPL_NODE;
  }

  @Override
  public JsImplNode copy(CopyState copyState) {
    return new JsImplNode(this, copyState);
  }

  public String module() {
    return module.getValue();
  }

  public String function() {
    return function.getValue();
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return getSourceLocation();
  }

  @Override
  public ImmutableList<CommandTagAttribute> getAttributes() {
    return attributes;
  }

  @Override
  public ExternNode getParent() {
    return (ExternNode) super.getParent();
  }
}
