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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.TypeReference;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.FileMetadata.Extern.JavaImpl.MethodType;
import com.google.template.soy.soytree.SoyNode.StackContextNode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Java implementation for an extern. */
public final class JavaImplNode extends AbstractCommandNode
    implements ExternImplNode, CommandTagAttributesHolder, StackContextNode {
  public static final ImmutableSet<String> IMPLICIT_PARAMS =
      ImmutableSet.of(
          "com.google.template.soy.data.Dir",
          "com.google.template.soy.plugin.java.RenderCssHelper",
          "com.ibm.icu.util.ULocale");
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
  public static final MethodType DEFAULT_TYPE = MethodType.STATIC;

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
  private TypeReference parsedReturnType;
  private ImmutableList<TypeReference> parsedParamTypes = ImmutableList.of();

  public JavaImplNode(
      int id,
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter,
      Function<String, TypeReference> typeParser,
      Function<String, ImmutableList<TypeReference>> typeListParser) {
    super(id, sourceLocation, "javaimpl");
    this.attributes = ImmutableList.copyOf(attributes);
    initAttributes(errorReporter);

    if (returnType != null) {
      this.parsedReturnType = typeParser.apply(returnType.getValue());
    }
    if (params != null) {
      this.parsedParamTypes = typeListParser.apply(params.getValue());
    }
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
    this.parsedReturnType = orig.parsedReturnType;
    this.parsedParamTypes = orig.parsedParamTypes;
  }

  /**
   * Pulls out relevant attributes into class fields for quick reference. Should only be used in
   * constructors.
   */
  private void initAttributes(ErrorReporter errorReporter) {
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

  public MethodType type() {
    return type != null ? MethodType.valueOf(Ascii.toUpperCase(type.getValue())) : DEFAULT_TYPE;
  }

  public boolean isStatic() {
    return type().isStatic();
  }

  public boolean isInterface() {
    return type().isInterface();
  }

  public ImmutableList<TypeReference> paramTypes() {
    return parsedParamTypes;
  }

  public static boolean isParamImplicit(String param) {
    return IMPLICIT_PARAMS.contains(param);
  }

  public TypeReference returnType() {
    return parsedReturnType;
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

  @Nullable
  public String getRawAttributeValue(String paramName) {
    Optional<CommandTagAttribute> attr =
        attributes.stream().filter(a -> paramName.equals(a.getName().identifier())).findFirst();
    return attr.map(CommandTagAttribute::getValue).orElse(null);
  }

  public boolean isAsync() {
    return parsedReturnType.isGeneric() && isSupportedFutureClassName(returnType().className());
  }

  /** Only these exact future classes are supported in the implementation's method declaration. */
  private static final ImmutableSet<String> FUTURE_CLASS_NAMES =
      ImmutableSet.of(Future.class.getName(), ListenableFuture.class.getName());

  public static boolean isSupportedFutureClassName(String s) {
    return FUTURE_CLASS_NAMES.contains(s);
  }

  @Override
  public ExternNode getParent() {
    return (ExternNode) super.getParent();
  }

  @Override
  public StackTraceElement createStackTraceElement(SourceLocation srcLocation) {
    SoyFileNode file = getNearestAncestor(SoyFileNode.class);
    return new StackTraceElement(
        /* declaringClass= */ file.getNamespace(),
        /* methodName= */ getParent().getIdentifier().identifier(),
        srcLocation.getFileName(),
        srcLocation.getBeginLine());
  }
}
