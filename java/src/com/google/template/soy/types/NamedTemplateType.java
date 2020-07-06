/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.types;

import com.google.auto.value.AutoValue;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.SoyType.Kind;
import java.util.Optional;

/**
 * Placeholder type for named templates before their signatures have been resolved.
 * TODO(b/158474755) This can be removed once partial template registries can be resolved in
 * filepath order.
 */
@AutoValue
public abstract class NamedTemplateType extends SoyType {

  public static NamedTemplateType create(String templateName) {
    return new AutoValue_NamedTemplateType(templateName, Optional.empty());
  }

  static NamedTemplateType createWithBoundParameters(
      String templateName, RecordType boundParameters) {
    return new AutoValue_NamedTemplateType(templateName, Optional.of(boundParameters));
  }

  /** Fully-qualified of the template. */
  public abstract String getTemplateName();

  /** Record type of bound parameters, if any. */
  public abstract Optional<RecordType> getBoundParameters();

  @Override
  public final Kind getKind() {
    return Kind.NAMED_TEMPLATE;
  }

  @Override
  final boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    // Nothing is assignable to this placeholder type.
    return false;
  }

  @Override
  public final String toString() {
    if (getBoundParameters().isPresent()) {
      return String.format("%s.bind(%s)", getTemplateName(), getBoundParameters().get());
    } else {
      return getTemplateName();
    }
  }

  @Override
  final void doToProto(SoyTypeP.Builder builder) {
    throw new UnsupportedOperationException(
        "NamedTemplateType should have been resolved before being written to proto.");
  }

  @Override
  public final <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
