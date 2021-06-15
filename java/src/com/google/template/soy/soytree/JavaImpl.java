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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.Arrays;
import java.util.List;

/** Java implementation for an extern. */
@AutoValue
public abstract class JavaImpl {
  private static final SoyErrorKind INVALID_IMPL_ATTRIBUTE =
      SoyErrorKind.of("''{0}'' is not a valid attribute.");
  private static final String CLASS = "class";
  private static final String METHOD = "method";
  private static final String PARAMS = "params";
  private static final String RETURN = "return";
  public static final String FIELDS = String.format("%s,%s,%s,%s", CLASS, METHOD, PARAMS, RETURN);
  private static final SoyErrorKind UNEXPECTED_ARGS =
      SoyErrorKind.of("Java implementations require attributes " + FIELDS + " .");

  public static JavaImpl create(
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    if (attributes.size() != 4) {
      errorReporter.report(sourceLocation, UNEXPECTED_ARGS);
      return null;
    }
    Builder externBuilder = builder();
    externBuilder.setSourceLocation(sourceLocation);
    for (CommandTagAttribute attr : attributes) {
      String value = attr.getValue();
      if (attr.hasName(CLASS)) {
        externBuilder.setModule(value);
      } else if (attr.hasName(METHOD)) {
        externBuilder.setFunction(value);
      } else if (attr.hasName(PARAMS)) {
        externBuilder.setArgs(ImmutableList.copyOf(Arrays.asList(value.split(","))));
      } else if (attr.hasName(RETURN)) {
        externBuilder.setReturnType(value);
      } else {
        errorReporter.report(attr.getSourceLocation(), INVALID_IMPL_ATTRIBUTE, attr.getName());
        return null;
      }
    }
    return externBuilder.build();
  }

  static Builder builder() {
    return new AutoValue_JavaImpl.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setModule(String value);

    abstract Builder setFunction(String value);

    abstract Builder setArgs(ImmutableList<String> value);

    abstract Builder setReturnType(String value);

    abstract Builder setSourceLocation(SourceLocation sourceLocation);

    abstract JavaImpl build();
  }

  public abstract String module();

  public abstract String function();

  public abstract ImmutableList<String> args();

  public abstract String returnType();

  public abstract SourceLocation sourceLocation();
}
