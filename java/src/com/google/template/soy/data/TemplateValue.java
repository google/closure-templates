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
import com.google.template.soy.data.internal.SoyRecordImpl;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/** Runtime type for templates. */
@AutoValue
public abstract class TemplateValue extends SoyAbstractValue {
  @Nonnull
  public static TemplateValue create(String templateName) {
    return new AutoValue_TemplateValue(templateName, Optional.empty(), Optional.empty());
  }

  @Nonnull
  public static TemplateValue create(String templateName, Object compiledTemplate) {
    return new AutoValue_TemplateValue(
        templateName, Optional.empty(), Optional.of(compiledTemplate));
  }

  @Nonnull
  public static TemplateValue createWithBoundParameters(
      String templateName, SoyRecord boundParameters) {
    return new AutoValue_TemplateValue(
        templateName, Optional.of(boundParameters), Optional.empty());
  }

  @Nonnull
  public static TemplateValue createWithBoundParameters(
      String templateName, SoyRecord boundParameters, Object compiledTemplate) {
    return new AutoValue_TemplateValue(
        templateName, Optional.of(boundParameters), Optional.of(compiledTemplate));
  }

  @Nonnull
  public static TemplateValue createFromTemplate(
      TemplateInterface template, Object compiledTemplate) {
    @SuppressWarnings("unchecked")
    SoyRecord record =
        SoyRecordImpl.forProviderMap((Map<String, SoyValueProvider>) template.getParamsAsMap());
    return new AutoValue_TemplateValue(
        template.getTemplateName(), Optional.of(record), Optional.of(compiledTemplate));
  }

  public abstract String getTemplateName();

  public abstract Optional<SoyRecord> getBoundParameters();

  // This is supposed to be the CompiledTemplate interface, but is an Object here because of
  // circular build dependencies.
  public abstract Optional<Object> compiledTemplate();

  @Nonnull
  public Object getCompiledTemplate() {
    return compiledTemplate().get();
  }

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
}
