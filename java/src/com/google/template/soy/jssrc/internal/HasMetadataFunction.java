/*
 * Copyright 2017 Google Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import java.util.List;
import java.util.Set;

/**
 * Soy special function for internal usages. This function is for checking if the global metadata
 * exists during runtime.
 */
public final class HasMetadataFunction implements SoyLibraryAssistedJsSrcFunction {
  // $$ prefix ensures that the function cannot be used directly
  public static final String NAME = "$$hasMetadata";
  public static final HasMetadataFunction INSTANCE = new HasMetadataFunction();

  private HasMetadataFunction() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    return new JsExpr("soy.velog.$$hasMetadata()", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy.velog");
  }
}
