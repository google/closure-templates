/*
 * Copyright 2019 Google Inc.
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

import java.util.Map;

/**
 * Partial template instance (may not have all required params set), used for testing. This file is
 * build visibility restricted to internal testing tools only.
 */
public final class PartialTemplateBuilder implements SoyTemplate {
  private final SoyTemplate template;

  public PartialTemplateBuilder(BaseSoyTemplateImpl.AbstractBuilder<?, ?> builder) {
    template = builder.buildPartialForTests();
  }

  @Override
  public String getTemplateName() {
    return template.getTemplateName();
  }

  @Override
  public Map<String, ?> getParamsAsMap() {
    return template.getParamsAsMap();
  }
}
