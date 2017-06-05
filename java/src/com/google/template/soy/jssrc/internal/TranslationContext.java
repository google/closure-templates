/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.auto.value.AutoValue;
import com.google.template.soy.jssrc.dsl.CodeChunk;

/**
 * Encapsulates state needed throughout the jssrc backend,
 * to prevent parameter lists from getting too long.
 * TODO: bags-of-parameters are still a code smell. Sort out which parts
 * actually need this stuff.
 */
@AutoValue
public abstract class TranslationContext {

  public abstract SoyToJsVariableMappings variableMappings();

  public abstract CodeChunk.Generator codeGenerator();

  public static TranslationContext of(
      SoyToJsVariableMappings variableMappings, CodeChunk.Generator codeGenerator) {
    return new AutoValue_TranslationContext(variableMappings, codeGenerator);
  }
}
