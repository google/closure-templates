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

import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jssrc.dsl.CodeChunk;

/** Manages variable scopes and lifetimes. */
public final class TranslationContext {

  public static TranslationContext of(
      SoyToJsVariableMappings soyToJsVariableMappings, UniqueNameGenerator nameGenerator) {
    return new TranslationContext(soyToJsVariableMappings, nameGenerator);
  }

  private SoyToJsVariableMappings soyToJsVariableMappings;
  private UniqueNameGenerator nameGenerator;

  private TranslationContext(
      SoyToJsVariableMappings soyToJsVariableMappings, UniqueNameGenerator nameGenerator) {
    this.soyToJsVariableMappings = soyToJsVariableMappings;
    this.nameGenerator = nameGenerator;
  }

  public SoyToJsVariableMappings soyToJsVariableMappings() {
    return soyToJsVariableMappings;
  }

  public CodeChunk.Generator codeGenerator() {
    return CodeChunk.Generator.create(nameGenerator);
  }

  /** The name generator for this template. */
  UniqueNameGenerator nameGenerator() {
    return nameGenerator;
  }

  /** A simple closeable to exit a variable scope. */
  public interface ExitScope extends AutoCloseable {
    @Override
    void close();
  }

  /** Creates a new variable naming scope that will be active until the `ExitScope` is closed. */
  public ExitScope enterSoyScope() {
    var prevMappings = soyToJsVariableMappings;
    soyToJsVariableMappings = SoyToJsVariableMappings.startingWith(prevMappings);
    return () -> soyToJsVariableMappings = prevMappings;
  }

  /**
   * Creates a new variable naming scope that will be active until the `ExitScope` is closed. Use
   * this when we are entering a JS block scope as well, since this can be used to reduce name
   * mangling.
   */
  public ExitScope enterSoyAndJsScope() {
    var prevMappings = soyToJsVariableMappings;
    soyToJsVariableMappings = SoyToJsVariableMappings.startingWith(prevMappings);
    var prevGenerator = nameGenerator;
    nameGenerator = nameGenerator.branch();
    return () -> {
      soyToJsVariableMappings = prevMappings;
      nameGenerator = prevGenerator;
    };
  }
}
