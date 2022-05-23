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
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Java implementation for an extern. */
public final class JavaImplNode extends ExternImplNode {
  public static final String CLASS = "class";
  public static final String METHOD = "method";
  public static final String PARAMS = "params";
  public static final String RETURN = "return";
  public static final String TYPE = "type";

  public static final String TYPE_STATIC = "static"; // static method in a class
  public static final String TYPE_INSTANCE = "instance"; // instance method in a class
  public static final String TYPE_INTERFACE = "interface"; // instance method in a interface
  public static final String TYPE_STATIC_INTERFACE =
      "static_interface"; // static method in a interface
  private static final ImmutableSet<String> ALLOWED_TYPES =
      ImmutableSet.of(TYPE_STATIC, TYPE_INSTANCE, TYPE_INTERFACE, TYPE_STATIC_INTERFACE);

  private static final SoyErrorKind INVALID_IMPL_ATTRIBUTE =
      SoyErrorKind.of("Invalid attribute ''{0}''.");
  private static final SoyErrorKind MISSING_ATTR =
      SoyErrorKind.of("Missing required attribute ''{0}''.");
  private static final SoyErrorKind BAD_TYPE =
      SoyErrorKind.of(
          String.format(
              "Valid values for ''%s'' are %s.",
              TYPE, ALLOWED_TYPES.stream().collect(joining("'', ''", "''", "''"))));

  private final ImmutableList<CommandTagAttribute> attributes;

  // Stored separately from {@code attributes} for convenience.
  private CommandTagAttribute className;
  private CommandTagAttribute methodName;
  private CommandTagAttribute params;
  private CommandTagAttribute returnType;
  private CommandTagAttribute type;

  public JavaImplNode(
      int id,
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, sourceLocation, "javaimpl");
    this.attributes = ImmutableList.copyOf(attributes);
    initAttributes(errorReporter);
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
    initAttributes(ErrorReporter.devnull());
  }

  /**
   * Pulls out relevant attributes into class fields for quick reference. Should only be used in
   * constructors.
   */
  private final void initAttributes(ErrorReporter errorReporter) {
    for (CommandTagAttribute attr : attributes) {
      if (attr.hasName(CLASS)) {
        this.className = attr;
      } else if (attr.hasName(METHOD)) {
        this.methodName = attr;
      } else if (attr.hasName(PARAMS)) {
        this.params = attr;
      } else if (attr.hasName(RETURN)) {
        this.returnType = attr;
      } else if (attr.hasName(TYPE)) {
        this.type = attr;
        if (!ALLOWED_TYPES.contains(type.getValue())) {
          errorReporter.report(attr.getSourceLocation(), BAD_TYPE);
        }
      } else {
        errorReporter.report(attr.getSourceLocation(), INVALID_IMPL_ATTRIBUTE, attr.getName());
      }
    }

    if (className == null) {
      errorReporter.report(getSourceLocation(), MISSING_ATTR, CLASS);
    }
    if (methodName == null) {
      errorReporter.report(getSourceLocation(), MISSING_ATTR, METHOD);
    }
    if (params == null) {
      errorReporter.report(getSourceLocation(), MISSING_ATTR, PARAMS);
    }
    if (returnType == null) {
      errorReporter.report(getSourceLocation(), MISSING_ATTR, RETURN);
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

  public String type() {
    return type != null ? type.getValue() : TYPE_STATIC;
  }

  public boolean isStatic() {
    String t = type();
    return TYPE_STATIC.equals(t) || TYPE_STATIC_INTERFACE.equals(t);
  }

  public boolean isInterface() {
    String t = type();
    return TYPE_INTERFACE.equals(t) || TYPE_STATIC_INTERFACE.equals(t);
  }

  public ImmutableList<String> params() {
    String val = params.getValue();
    if (val.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(Arrays.asList(val.split("\\s*,\\s*")));
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

  @Override
  public ExternNode getParent() {
    return (ExternNode) super.getParent();
  }
}
