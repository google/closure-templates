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
package com.google.template.soy.logging;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeUrl;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;
import java.io.IOException;
import java.util.Optional;

/**
 * Experimental logging interface for soy.
 *
 * <p>This implements a callback protocol with the {@code velog} syntax.
 */
public interface SoyLogger {
  /** Logging attributes for {@code velog} root elements. */
  @AutoValue
  public abstract class LoggingAttrs {
    public static Builder builder() {
      return new Builder();
    }

    LoggingAttrs() {}

    /**
     * The attributes to be added to the element.
     *
     * <p>The attributes are added in the order they are added to the builder. The values are
     * escaped and suitable for encoding in a double-quoted attribute value.
     */
    abstract ImmutableMap<String, String> attrs();

    abstract boolean hasAnchorAttributes();

    /** Writes the attributes to the output appendable. */
    public void writeTo(boolean isAnchorTag, Appendable outputAppendable) throws IOException {
      if (hasAnchorAttributes() && !isAnchorTag) {
        throw new IllegalStateException(
            "logger attempted to add anchor attributes to a non-anchor element.");
      }
      for (var entry : attrs().entrySet()) {
        var name = entry.getKey();
        outputAppendable
            .append(' ')
            .append(name)
            .append("=\"")
            .append(entry.getValue())
            .append('"');
      }
    }

    @Override
    public final String toString() {
      StringBuilder sb = new StringBuilder();
      try {
        writeTo(true, sb);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
      return sb.substring(1); // skip the leading space
    }

    /** Builder for {@link LoggingAttrs}. */
    public static final class Builder {
      private boolean hasAnchorAttributes = false;
      private final ImmutableMap.Builder<String, String> attrsBuilder = ImmutableMap.builder();

      private Builder() {}

      private void addAttribute(String key, String value) {
        attrsBuilder.put(key, HtmlEscapers.htmlEscaper().escape(value));
      }

      /**
       * Adds a data attribute to the logging attributes.
       *
       * <p>The key must start with "data-"
       */
      @CanIgnoreReturnValue
      public Builder addDataAttribute(String key, String value) {
        checkArgument(key.startsWith("data-"), "data attribute key must start with 'data-'.");
        checkNotNull(value);
        addAttribute(key, value);
        return this;
      }

      /**
       * Adds an anchor href attribute to the logging attributes.
       *
       * <p>If the element this is attached to is an anchor tag, this will be used as the href, if
       * it isn't an anchor an error will be thrown.
       */
      @CanIgnoreReturnValue
      public Builder addAnchorHref(SafeUrl value) {
        addAttribute("href", value.getSafeUrlString());
        this.hasAnchorAttributes = true;
        return this;
      }

      /**
       * Adds an anchor ping attribute to the logging attributes.
       *
       * <p>If the element this is attached to is an anchor tag, this will be used as the ping, if
       * it isn't an anchor an error will be thrown.
       */
      @CanIgnoreReturnValue
      public Builder addAnchorPing(SafeUrl value) {
        addAttribute("ping", value.getSafeUrlString());
        this.hasAnchorAttributes = true;
        return this;
      }

      /**
       * Adds an anchor ping attribute to the logging attributes.
       *
       * <p>If the element this is attached to is an anchor tag, this will be used as the ping, if
       * it isn't an anchor an error will be thrown.
       */
      @CanIgnoreReturnValue
      public Builder addAnchorPing(Iterable<? extends SafeUrl> value) {
        StringBuilder pingBuilder = new StringBuilder();
        boolean first = true;
        for (SafeUrl url : value) {
          if (!first) {
            pingBuilder.append(' ');
          }
          pingBuilder.append(url.getSafeUrlString());
          first = false;
        }
        addAttribute("ping", pingBuilder.toString());
        return this;
      }

      /**
       * Builds the {@link LoggingAttrs}.
       *
       * @throws IllegalStateException if no attributes were added.
       */
      public LoggingAttrs build() {
        var attrs = attrsBuilder.buildOrThrow();
        if (attrs.isEmpty()) {
          throw new IllegalStateException("LoggingAttrs must have at least one attribute");
        }
        return new AutoValue_SoyLogger_LoggingAttrs(attrs, hasAnchorAttributes);
      }
    }
  }

  /** Data to be used to output VE logging info to be outputted to the DOM while in debug mode. */
  @AutoValue
  public abstract static class EnterData {
    public static final EnterData EMPTY = create(Optional.empty(), Optional.empty());

    public static EnterData create(SafeHtml debugHtml) {
      return create(Optional.of(debugHtml), Optional.empty());
    }

    public static EnterData create(LoggingAttrs loggingAttrs) {
      return create(Optional.empty(), Optional.of(loggingAttrs));
    }

    public static EnterData create(SafeHtml debugHtml, LoggingAttrs loggingAttrs) {
      return create(Optional.of(debugHtml), Optional.of(loggingAttrs));
    }

    private static EnterData create(
        Optional<SafeHtml> debugHtml, Optional<LoggingAttrs> loggingAttrs) {
      return new AutoValue_SoyLogger_EnterData(debugHtml, loggingAttrs);
    }

    public abstract Optional<SafeHtml> debugHtml();

    public abstract Optional<LoggingAttrs> loggingAttrs();

    EnterData() {}
  }

  /**
   * Called when a {@code velog} statement is entered.
   *
   * @return Data to be used to output VE logging info to be outputted to the DOM while in debug
   *     mode. Method implementation must check and only return VE logging info if in debug mode.
   *     Most implementations will likely return Optional.empty(). TODO(b/148167210): This is
   *     currently under implementation.
   */
  EnterData enter(LogStatement statement);

  /**
   * Called when a {@code velog} statement is exited.
   *
   * @return Optional VE logging info to be outputted to the DOM while in debug mode. Method
   *     implementation must check and only return VE logging info if in debug mode. Most
   *     implementations will likely return Optional.empty(). TODO(b/148167210): This is currently
   *     under implementation.
   */
  Optional<SafeHtml> exit();

  // called to format a logging function value.
  String evalLoggingFunction(LoggingFunctionInvocation value);

  /** The ID of the UndefinedVe. */
  long UNDEFINED_VE_ID = -1;

  /** The name of the UndefinedVe. */
  String UNDEFINED_VE_NAME = "UndefinedVe";
}
