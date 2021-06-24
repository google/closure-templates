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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Java implementation for an extern. */
public final class JavaImplNode extends ExternImplNode {
  private static final SoyErrorKind INVALID_IMPL_ATTRIBUTE =
      SoyErrorKind.of("''{0}'' is not a valid attribute.");
  public static final String CLASS = "class";
  public static final String METHOD = "method";
  public static final String PARAMS = "params";
  public static final String RETURN = "return";
  public static final String FIELDS = String.format("%s,%s,%s,%s", CLASS, METHOD, PARAMS, RETURN);
  private static final SoyErrorKind UNEXPECTED_ARGS =
      SoyErrorKind.of("Java implementations require attributes " + FIELDS + " .");

  private final ImmutableList<CommandTagAttribute> attributes;

  // Stored separately from {@code attributes} for convenience.
  private CommandTagAttribute className;
  private CommandTagAttribute methodName;
  private CommandTagAttribute params;
  private CommandTagAttribute returnType;

  public JavaImplNode(
      int id,
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, sourceLocation, "javaimpl");

    if (attributes.size() != 4) {
      errorReporter.report(sourceLocation, UNEXPECTED_ARGS);
    }
    attributes.stream()
        .filter(
            attr ->
                !(attr.hasName(CLASS)
                    || attr.hasName(METHOD)
                    || attr.hasName(PARAMS)
                    || attr.hasName(RETURN)))
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
  private JavaImplNode(JavaImplNode orig, CopyState copyState) {
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
      if (attr.hasName(CLASS)) {
        this.className = attr;
      } else if (attr.hasName(METHOD)) {
        this.methodName = attr;
      } else if (attr.hasName(PARAMS)) {
        this.params = attr;
      } else if (attr.hasName(RETURN)) {
        this.returnType = attr;
      }
    }
  }

  @Override
  public Kind getKind() {
    return Kind.JAVA_IMPL_NODE;
  }

  @Override
  public JavaImplNode copy(CopyState copyState) {
    return new JavaImplNode(this, copyState);
  }

  public String className() {
    return className.getValue();
  }

  public String methodName() {
    return methodName.getValue();
  }

  public ImmutableList<String> params() {
    return ImmutableList.copyOf(Arrays.asList(params.getValue().split("\\s*,\\s*")));
  }

  public String returnType() {
    return returnType.getValue();
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return getSourceLocation();
  }

  @Override
  public ImmutableList<CommandTagAttribute> getAttributes() {
    return attributes;
  }

  public SourceLocation getAttributeValueLocation(String paramName) {
    Optional<CommandTagAttribute> attr =
        attributes.stream().filter(a -> paramName.equals(a.getName().identifier())).findFirst();
    return attr.isPresent() ? attr.get().getValueLocation() : SourceLocation.UNKNOWN;
  }
}
