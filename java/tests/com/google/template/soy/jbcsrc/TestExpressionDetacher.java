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
package com.google.template.soy.jbcsrc;

import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;

/** Stub implementation of {@link ExpressionDetacher} suitable for use in tests. */
public final class TestExpressionDetacher implements ExpressionDetacher {

  public TestExpressionDetacher() {}

  @Override
  public Expression resolveSoyValueProvider(Expression soyValueProvider) {
    return MethodRef.SOY_VALUE_PROVIDER_RESOLVE.invoke(soyValueProvider);
  }

  @Override
  public Expression waitForSoyValueProvider(Expression soyValueProvider) {
    return soyValueProvider;
  }

  @Override
  public Expression resolveSoyValueProviderList(Expression soyValueProviderList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Expression resolveSoyValueProviderMap(Expression soyValueProviderMap) {
    throw new UnsupportedOperationException();
  }
}
