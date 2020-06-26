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
import java.util.Optional;

/** Tofu-specific runtime type for templates. */
@AutoValue
public abstract class TofuTemplateValue extends SoyAbstractValue {
  public static TofuTemplateValue create(String templateName) {
    return new AutoValue_TofuTemplateValue(templateName, Optional.empty());
  }

  public static TofuTemplateValue createWithBoundParameters(
      String templateName, SoyRecord boundParameters) {
    return new AutoValue_TofuTemplateValue(templateName, Optional.of(boundParameters));
  }

  public abstract String getTemplateName();

  public abstract Optional<SoyRecord> getBoundParameters();

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {
    return String.format("** FOR DEBUGGING ONLY: template(%s) **", getTemplateName());
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) {
    throw new IllegalStateException(
        "Cannot print template types; this should have been caught during parsing.");
  }
}
