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
package com.google.template.soy.data;

import com.google.auto.value.AutoValue;
import com.google.template.soy.data.internal.ParamStore;
import java.util.Optional;
import javax.annotation.Nonnull;

/** Runtime type for templates. */
@AutoValue
public abstract class TemplateValue extends SoyValue {
  @Nonnull
  public static TemplateValue create(String templateName) {
    return createWithBoundParameters(templateName, ParamStore.EMPTY_INSTANCE);
  }

  @Nonnull
  public static TemplateValue create(String templateName, Object compiledTemplate) {
    return new AutoValue_TemplateValue(
        templateName, ParamStore.EMPTY_INSTANCE, Optional.of(compiledTemplate));
  }

  @Nonnull
  public static TemplateValue createWithBoundParameters(
      String templateName, ParamStore boundParameters) {
    return new AutoValue_TemplateValue(templateName, boundParameters, Optional.empty());
  }

  public static TemplateValue createFromTemplate(TemplateInterface template) {
    ParamStore record = (ParamStore) template.getParamsAsRecord();
    return createWithBoundParameters(template.getTemplateName(), record);
  }

  public abstract String getTemplateName();

  public abstract ParamStore getBoundParameters();

  // This is supposed to be the CompiledTemplate interface, but is an Object here because of
  // circular build dependencies.
  // This will always be present for templates that are constructed by the jbcsrc code gen, but
  // absent when constructed by Tofu or passed in at the top level.
  public abstract Optional<Object> compiledTemplate();

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {
    return String.format("** FOR DEBUGGING ONLY: %s **", getTemplateName());
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) {
    throw new IllegalStateException(
        "Cannot print template types; this should have been caught during parsing.");
  }

  @Override
  public String getSoyTypeName() {
    return "template";
  }
}
