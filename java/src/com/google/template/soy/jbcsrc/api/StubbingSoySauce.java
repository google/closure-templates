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
package com.google.template.soy.jbcsrc.api;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValueProvider;
import java.util.function.Predicate;

/**
 * Utility for creating a {@link SoySauce} instance with the special power to "stub" templates
 * matching a given predicate. This is used for legacy unit tests; please contact soy-dev@ if you
 * think you need to use this.
 */
public final class StubbingSoySauce {
  /**
   * Construct a SoySauce object that can stub out arbitrary template implementations.
   *
   * @param preconfiguredBuilder a fully constructed builder.
   * @param stubMatcher a predicate that decides which templates to stub. Only public templates are
   *     stubbable. The predicate is expected to be idempotent, and the behavior is undefined if it
   *     is not.
   * @param stubFactory a factory that provides stub implementations
   */
  public static SoySauce create(
      SoySauceBuilder preconfiguredBuilder,
      ImmutablePredicate<String> stubMatcher,
      StubFactory stubFactory) {
    Preconditions.checkNotNull(stubMatcher);
    Preconditions.checkNotNull(stubFactory);
    return preconfiguredBuilder
        .withCustomCompiledTemplatesFactory(
            (delTemplateNames, loader) ->
                StubbingCompiledTemplates.create(
                    stubMatcher, stubFactory, delTemplateNames, loader))
        .build();
  }

  /** Non-instantiable. */
  private StubbingSoySauce() {}

  /** Immutable sub-interface of {@link Predicate}. */
  @Immutable
  public interface ImmutablePredicate<T> extends Predicate<T> {}

  /**
   * Interface for creating stubs. This will be called for templates which match the provided
   * predicate.
   */
  @Immutable
  public interface StubFactory {
    TemplateStub<SafeHtml> createHtmlTemplate(String templateName);

    TemplateStub<SafeScript> createJsTemplate(String templateName);

    TemplateStub<SafeUrl> createUriTemplate(String templateName);

    TemplateStub<TrustedResourceUrl> createTrustedResourceUriTemplate(String templateName);

    TemplateStub<SanitizedContent> createAttributesTemplate(String templateName);

    TemplateStub<SafeStyleSheet> createCssTemplate(String templateName);

    TemplateStub<String> createTextTemplate(String templateName);
  }

  /** Interface for individual template stubs. */
  @Immutable
  public interface TemplateStub<T> {
    T render(ImmutableMap<String, SoyValueProvider> params);
  }
}
