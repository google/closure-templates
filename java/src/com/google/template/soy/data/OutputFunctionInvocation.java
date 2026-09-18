/*
 * Copyright 2024 Google Inc.
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
import com.google.common.collect.ImmutableList;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Encapsulates a deferred extern function call with two execution pathways: primary (invoked when
 * streaming to direct output) and fallback (invoked when evaluating in a buffering/flattening
 * context).
 */
@AutoValue
public abstract class OutputFunctionInvocation {

  @SuppressWarnings("Immutable")
  public abstract MethodHandle primary();

  @SuppressWarnings("Immutable")
  public abstract MethodHandle fallback();

  @SuppressWarnings("Immutable")
  public abstract ImmutableList<?> args();

  @Nullable
  public abstract SanitizedContent.ContentKind contentKind();

  public static OutputFunctionInvocation create(
      MethodHandle primary,
      MethodHandle fallback,
      List<?> args,
      @Nullable SanitizedContent.ContentKind contentKind) {
    return new AutoValue_OutputFunctionInvocation(
        primary, fallback, ImmutableList.copyOf(args), contentKind);
  }

  @CanIgnoreReturnValue
  public String evalPrimary() {
    try {
      Object result = primary().invokeWithArguments(args());
      return extractContent(result);
    } catch (Throwable t) {
      sneakyThrow(t);
      return "";
    }
  }

  public String evalPrimary(List<Function<String, String>> escapers) {
    return escape(evalPrimary(), escapers);
  }

  @CanIgnoreReturnValue
  public String evalFallback() {
    try {
      Object result = fallback().invokeWithArguments(args());
      return extractContent(result);
    } catch (Throwable t) {
      sneakyThrow(t);
      return "";
    }
  }

  public String evalFallback(List<Function<String, String>> escapers) {
    return escape(evalFallback(), escapers);
  }

  public void appendFallback(Appendable target, List<Function<String, String>> escapers)
      throws IOException {
    target.append(evalFallback(escapers));
  }

  public void appendPrimary(Appendable target, List<Function<String, String>> escapers)
      throws IOException {
    target.append(evalPrimary(escapers));
  }

  private static String escape(String value, List<Function<String, String>> escapers) {
    for (Function<String, String> escaper : escapers) {
      value = escaper.apply(value);
    }
    return value;
  }

  public static String extractContent(@Nullable Object result) {
    if (result == null) {
      return "";
    }
    if (result instanceof SanitizedContent sanitizedContent) {
      return sanitizedContent.getContent();
    }
    if (result instanceof SafeHtml safeHtml) {
      return safeHtml.getSafeHtmlString();
    }
    if (result instanceof SafeUrl safeUrl) {
      return safeUrl.getSafeUrlString();
    }
    if (result instanceof TrustedResourceUrl trustedResourceUrl) {
      return trustedResourceUrl.getTrustedResourceUrlString();
    }
    if (result instanceof SafeStyle safeStyle) {
      return safeStyle.getSafeStyleString();
    }
    if (result instanceof SafeStyleSheet safeStyleSheet) {
      return safeStyleSheet.getSafeStyleSheetString();
    }
    if (result instanceof SafeScript safeScript) {
      return safeScript.getSafeScriptString();
    }
    return result.toString();
  }

  // Rethrow checked exceptions as unchecked without wrapping.
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
