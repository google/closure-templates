/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.Keep;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.template.soy.data.AbstractLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.SaveStateMetaFactory;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.msgs.restricted.PlaceholderName;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPartForRendering;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPartForRendering;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime utilities uniquely for the {@code jbcsrc} backend.
 *
 * <p>This class is public so it can be be used by generated template code. Please do not use it
 * from client code.
 */
@SuppressWarnings("ShortCircuitBoolean")
public final class JbcSrcRuntime {
  private static final Logger logger = Logger.getLogger(JbcSrcRuntime.class.getName());

  @Nonnull
  public static AssertionError unexpectedStateError(StackFrame frame) {
    return new AssertionError("Unexpected state requested: " + frame.stateNumber);
  }

  /**
   * Every {@code debugger} statement will call this method. You can use conditional breakpoints
   * here to easily stop execution at the right location.
   */
  public static void debugger(String fileName, int lineNumber) {
    logger.log(
        Level.WARNING,
        String.format(
            "Hit {debugger} statement at %s:%d. Put a breakpoint here to halt Soy rendering.",
            fileName, lineNumber),
        new Exception());
  }

  @Keep
  public static boolean numberEqualsStringAsNumber(double number, String expr) {
    return stringEqualsAsNumber(expr, number);
  }

  @Keep
  public static boolean stringEqualsAsNumber(String expr, double number) {
    if (expr == null) {
      return false;
    }

    try {
      return Double.parseDouble(expr) == number;
    } catch (NumberFormatException nfe) {
      return false;
    }
  }


  @Keep
  @Nonnull
  public static SoyValue getField(SoyValue record, RecordProperty field) {
    if (record.isNullish()) {
      throw new NullPointerException("Attempted to access field '" + field.getName() + "' of null");
    }
    return ((SoyRecord) record).getPositionalParam(field).resolve();
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getFieldProvider(SoyValue record, RecordProperty field) {
    if (record.isNullish()) {
      throw new NullPointerException("Attempted to access field '" + field.getName() + "' of null");
    }
    return ((SoyRecord) record).getPositionalParam(field);
  }

  /**
   * Wraps a given template with a buffer.
   *
   * @param delegate The delegate template to render
   * @param ignoreExceptions Whether exceptions should be ignored.
   * @param bufferedRenderDoneFn Function to apply after main rendering is done.
   */
  @Keep
  public static CompiledTemplate bufferTemplate(
      CompiledTemplate delegate,
      boolean ignoreExceptions,
      BufferedRenderDoneFn bufferedRenderDoneFn) {
    return new BufferedCompiledTemplate(delegate, ignoreExceptions, bufferedRenderDoneFn);
  }

  @Keep
  public static SoyValue getSoyListItem(List<SoyValueProvider> list, long index) {
    return getSoyListItemProvider(list, index).resolve();
  }

  @Keep
  public static SoyValueProvider getSoyListItemProvider(List<SoyValueProvider> list, long index) {
    if (list == null) {
      throw new NullPointerException("Attempted to access list item '" + index + "' of null");
    }
    int size = list.size();
    // use & instead of && to avoid a branch
    if (index < size & index >= 0) {
      SoyValueProvider soyValueProvider = list.get((int) index);
      return soyValueProvider == null ? UndefinedData.INSTANCE : soyValueProvider;
    }
    return UndefinedData.INSTANCE;
  }

  public static RenderResult getListStatus(List<? extends SoyValueProvider> soyValueProviders) {
    // avoid allocating an iterator
    for (SoyValueProvider soyValueProvider : soyValueProviders) {
      RenderResult result = soyValueProvider.status();
      if (!result.isDone()) {
        return result;
      }
    }
    return RenderResult.done();
  }

  public static RenderResult getMapStatus(
      Map<String, ? extends SoyValueProvider> soyValueProviders) {
    for (SoyValueProvider value : soyValueProviders.values()) {
      RenderResult result = value.status();
      if (!result.isDone()) {
        return result;
      }
    }
    return RenderResult.done();
  }

  @Keep
  @Nonnull
  public static SoyValue getSoyMapItem(SoyValue soyMap, SoyValue key) {
    return getSoyMapItemProvider(soyMap, key).resolve();
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getSoyMapItemProvider(SoyValue soyMap, SoyValue key) {
    if (soyMap.isNullish()) {
      throw new NullPointerException("Attempted to access map item '" + key + "' of null");
    }
    SoyValueProvider soyValueProvider = ((SoyMap) soyMap).getProvider(key);
    return soyValueProvider == null ? UndefinedData.INSTANCE : soyValueProvider;
  }

  @Keep
  public static SoyValue getSoyLegacyObjectMapItem(SoyValue legacyObjectMap, SoyValue key) {
    return getSoyLegacyObjectMapItemProvider(legacyObjectMap, key).resolve();
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getSoyLegacyObjectMapItemProvider(
      SoyValue legacyObjectMap, SoyValue key) {
    if (legacyObjectMap == null || legacyObjectMap.isNullish()) {
      throw new NullPointerException("Attempted to access map item '" + key + "' of null");
    }
    SoyValueProvider soyValueProvider = ((SoyLegacyObjectMap) legacyObjectMap).getItemProvider(key);
    return soyValueProvider == null ? UndefinedData.INSTANCE : soyValueProvider;
  }

  @Keep
  @Nonnull
  public static String handleBasicTranslationAndEscapeHtml(String text) {
    try {
      return MsgRenderer.maybeEscapeHtmlOnto(text, null);
    } catch (IOException e) {
      throw new AssertionError("impossible", e);
    }
  }

  /**
   * A Message renderer represents a message to be rendered. It encapsulates the placeholders and
   * message parts and can dynamically render them. This manages a small state machine that allows
   * for rendering to proceed.
   */
  public static class MsgRenderer implements SoyValueProvider {
    SoyMsgRawParts msgParts;
    private int partIndex;
    private SoyValueProvider pendingRender;
    final ToIntFunction<PlaceholderName> placeholderPositionFunction;
    final ImmutableList<SoyValueProvider> placeholders;

    // Some placeholders have ordering constraints.  This is necessary for the velog to function
    // correctly in the face of translators reordering things.
    // The constraints are simply that an end tag must come after a start tag
    @Nullable final Multiset<PlaceholderName> startPlaceholderRenderCount;

    // an optional map from a placeholder to another placeholder that must precede it.
    @Nullable
    final ImmutableSetMultimap<PlaceholderName, PlaceholderName> endPlaceholderToStartPlaceholder;

    private final boolean htmlEscape;
    private BufferingAppendable buffer;
    private boolean isDone;

    public MsgRenderer(
        SoyMsgRawParts msgParts,
        ToIntFunction<PlaceholderName> placeholderPositionFunction,
        ImmutableList<SoyValueProvider> placeholders,
        boolean htmlEscape,
        @Nullable
            ImmutableSetMultimap<PlaceholderName, PlaceholderName>
                endPlaceholderToStartPlaceholder) {
      this.msgParts = msgParts;
      this.placeholderPositionFunction = placeholderPositionFunction;
      this.placeholders = placeholders;
      this.htmlEscape = htmlEscape;
      if (endPlaceholderToStartPlaceholder != null) {
        this.endPlaceholderToStartPlaceholder = endPlaceholderToStartPlaceholder;
        this.startPlaceholderRenderCount = HashMultiset.create();
      } else {
        this.endPlaceholderToStartPlaceholder = null;
        this.startPlaceholderRenderCount = null;
      }
    }

    /**
     * Html escapes the string.
     *
     * <p>If the appendable is non-null, the result is appended to it and null is returned.
     *
     * <p>If the appendable is null, the escaped string is returned
     */
    @Nullable
    @CanIgnoreReturnValue
    static String maybeEscapeHtmlOnto(String s, @Nullable Appendable out) throws IOException {
      // Note that "&" is not replaced because the translation can contain HTML entities.
      int ltIndex = s.indexOf('<');
      if (ltIndex < 0) {
        if (out != null) {
          out.append(s);
          return null;
        }
        return s;
      }
      int length = s.length();
      boolean returnToString = false;
      if (out == null) {
        out = new StringBuilder(length + 3);
        returnToString = true;
      }
      int i = 0;
      do {
        out.append(s, i, ltIndex).append("&lt;");
        i = ltIndex + 1;
      } while (ltIndex < length && (ltIndex = s.indexOf('<', ltIndex + 1)) > 0);
      out.append(s, i, length);
      if (returnToString) {
        return out.toString();
      }
      return null;
    }

    @Override
    public final RenderResult status() {
      if (isDone) {
        return RenderResult.done();
      }
      BufferingAppendable currentBuilder = buffer;
      if (currentBuilder == null) {
        buffer = currentBuilder = LoggingAdvisingAppendable.buffering();
      }
      RenderResult result;
      try {
        result = renderAndResolve(currentBuilder);
      } catch (IOException ioe) {
        throw new AssertionError("impossible", ioe);
      }
      if (result.isDone()) {
        isDone = true;
      }
      return result;
    }

    @Override
    public final SoyValue resolve() {
      checkState(status().isDone());
      return buffer.getAsStringData();
    }

    SoyValueProvider getPlaceholder(PlaceholderName placeholderName) {
      return placeholders.get(placeholderPositionFunction.applyAsInt(placeholderName));
    }

    /** Renders the message to the given output stream incrementally. */
    @Override
    public RenderResult renderAndResolve(LoggingAdvisingAppendable out) throws IOException {
      // if we were in the middle of a placeholder render, finish that.
      if (pendingRender != null) {
        RenderResult result = pendingRender.renderAndResolve(out);
        if (!result.isDone()) {
          return result;
        }
        pendingRender = null;
      }
      for (int i = partIndex; i < msgParts.numParts(); i++) {
        Object msgPart = msgParts.getPart(i);
        if (msgPart instanceof String) {
          String s = (String) msgPart;
          if (htmlEscape) {
            maybeEscapeHtmlOnto(s, out);
          } else {
            out.append(s);
          }
        } else {
          var placeholderName = (PlaceholderName) msgPart;
          if (endPlaceholderToStartPlaceholder != null) {
            checkPlaceholderOrdering(placeholderName);
          }
          SoyValueProvider placeholderValue = getPlaceholder(placeholderName);
          RenderResult result = placeholderValue.renderAndResolve(out);
          if (!result.isDone()) {
            partIndex = i + 1;
            pendingRender = placeholderValue;
            return result;
          }
        }
      }
      if (startPlaceholderRenderCount != null && !startPlaceholderRenderCount.isEmpty()) {
        throw new IllegalStateException(
            "The following placeholders never had their matching placeholders rendered: "
                + startPlaceholderRenderCount.elementSet());
      }
      return RenderResult.done();
    }

    private void checkPlaceholderOrdering(PlaceholderName placeholderName) {
      if (endPlaceholderToStartPlaceholder.inverse().containsKey(placeholderName)) {
        startPlaceholderRenderCount.add(placeholderName);
      } else {
        // check if it is an end tag
        ImmutableSet<PlaceholderName> startPlaceholders =
            endPlaceholderToStartPlaceholder.get(placeholderName);
        if (!startPlaceholders.isEmpty()) {
          // make sure the start tag has been rendered
          boolean matched = false;
          for (PlaceholderName startPlaceholder : startPlaceholders) {
            if (startPlaceholderRenderCount.remove(startPlaceholder)) {
              matched = true;
              break;
            }
          }
          if (!matched) {
            // uhoh
            throw new IllegalStateException(
                String.format(
                    "Expected placeholder '%s' to come after one of %s",
                    placeholderName.name(),
                    startPlaceholders.stream()
                        .map(PlaceholderName::name)
                        .collect(joining(",", "[", "]"))));
          }
        }
      }
    }
  }

  /** A MsgRenderer for plural or select style messages. */
  public static final class PlrSelMsgRenderer extends MsgRenderer {
    private boolean resolvedCases;
    private final ULocale locale;

    public PlrSelMsgRenderer(
        SoyMsgRawParts msgParts,
        ToIntFunction<PlaceholderName> placeholderPositionFunction,
        ImmutableList<SoyValueProvider> placeholders,
        boolean htmlEscape,
        ULocale locale,
        @Nullable
            ImmutableSetMultimap<PlaceholderName, PlaceholderName>
                endPlaceholderToStartPlaceholder) {
      super(
          msgParts,
          placeholderPositionFunction,
          placeholders,
          htmlEscape,
          endPlaceholderToStartPlaceholder);
      this.locale = locale;
    }

    @Override
    public RenderResult renderAndResolve(LoggingAdvisingAppendable out) throws IOException {
      // plural/select messages always start with a sequence of plural and select values.
      // Additionally, we are guaranteed (by contract with the gencode) that the plural/select
      // variables are resolved.  So we need to resolve those first.
      // NOTE: that in the most common case, this loop only executes once and at maximum it will
      // loop 3 times.
      while (!resolvedCases) {
        if (msgParts instanceof SoyMsgSelectPartForRendering) {
          SoyMsgSelectPartForRendering selectPart = (SoyMsgSelectPartForRendering) msgParts;
          SoyValueProvider selectPlaceholder = getPlaceholder(selectPart.getSelectVarName());
          var caseSelectionResult = selectPlaceholder.status();
          if (caseSelectionResult.isDone()) {
            msgParts = selectPart.lookupCase(selectPlaceholder.resolve().coerceToString());
          } else {
            return caseSelectionResult;
          }
        } else if (msgParts instanceof SoyMsgPluralPartForRendering) {
          SoyMsgPluralPartForRendering pluralPart = (SoyMsgPluralPartForRendering) msgParts;
          SoyValueProvider pluralPlaceholder = getPlaceholder(pluralPart.getPluralVarName());
          var caseSelectionResult = pluralPlaceholder.status();
          if (caseSelectionResult.isDone()) {
            double pluralValue = pluralPlaceholder.resolve().numberValue();
            msgParts = pluralPart.lookupCase(pluralValue, locale);
          } else {
            return caseSelectionResult;
          }
        } else {
          resolvedCases = true;
          break;
        }
      }
      // render the cases.
      return super.renderAndResolve(out);
    }
  }

  private static final LoggingAdvisingAppendable LOGGER =
      new AbstractLoggingAdvisingAppendable() {
        @Override
        public boolean softLimitReached() {
          return false;
        }

        @Override
        protected void doAppend(char c) {
          System.out.append(c);
        }

        @Override
        protected void doAppend(CharSequence csq, int start, int end) {
          System.out.append(csq, start, end);
        }

        @Override
        protected void doAppend(CharSequence csq) {
          System.out.append(csq);
        }

        @Override
        protected void doEnterLoggableElement(LogStatement statement) {}

        @Override
        protected void doExitLoggableElement() {}

        @Override
        protected void doAppendLoggingFunctionInvocation(
            LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers) {
          String val = funCall.placeholderValue();
          for (Function<String, String> directive : escapers) {
            val = directive.apply(val);
          }
          System.out.append(val);
        }

        @Override
        public void flushBuffers(int depth) {
          throw new AssertionError("should not be called");
        }
      };

  /** Determines if the operand's string form can be equality-compared with a string. */
  @Keep
  public static boolean compareBoxedStringToBoxed(SoyValue string, SoyValue other) {
    if (string.isNullish() != other.isNullish()) {
      return false;
    } else if (string.isNullish()) {
      return true;
    }
    return compareUnboxedStringToBoxed(string.stringValue(), other);
  }

  @Keep
  public static boolean compareBoxedValueToBoxedString(SoyValue other, SoyValue string) {
    return compareBoxedStringToBoxed(string, other);
  }

  @Keep
  public static boolean compareBoxedValueToUnboxedString(SoyValue other, String stringValue) {
    return compareUnboxedStringToBoxed(stringValue, other);
  }

  @Keep
  public static boolean compareUnboxedStringToBoxed(String stringValue, SoyValue other) {
    // This follows similarly to the Javascript specification, to ensure similar operation
    // over Javascript and Java: http://www.ecma-international.org/ecma-262/5.1/#sec-11.9.3
    if (other instanceof StringData || other instanceof SanitizedContent) {
      return Objects.equals(stringValue, other.toString());
    }
    if (other instanceof NumberData) {
      try {
        // Parse the string as a number.
        return Double.parseDouble(stringValue) == other.numberValue();
      } catch (NumberFormatException nfe) {
        // Didn't parse as a number.
        return false;
      }
    }
    return false;
  }

  @Nonnull
  @Keep
  public static LoggingAdvisingAppendable logger() {
    return LOGGER;
  }

  @Keep
  public static int rangeLoopLength(int start, int end, int step) {
    int length = end - start;
    if ((length ^ step) < 0) {
      return 0;
    }
    return length / step + (length % step == 0 ? 0 : 1);
  }

  @Keep
  public static boolean coerceToBoolean(double v) {
    // NaN and 0 should both be falsy, all other numbers are truthy
    // use & instead of && to avoid a branch
    return v != 0.0 & !Double.isNaN(v);
  }

  @Keep
  public static boolean coerceToBoolean(@Nullable String v) {
    return v != null && !v.isEmpty();
  }

  /** Function to execute after rendering of a section of buffered template is done. */
  @Immutable
  public static interface BufferedRenderDoneFn {
    public void exec(LoggingAdvisingAppendable appendable, BufferingAppendable buffer)
        throws IOException;
  }

  /** Replays commands to the main appendable, including logging. */
  public static final BufferedRenderDoneFn REPLAYING_BUFFERED_RENDER_DONE_FN =
      (appendable, buffer) -> buffer.replayOn(appendable);

  /** Coerces to a string and applies non-streaming escaping directives. */
  public static class EscapingBufferedRenderDoneFn implements BufferedRenderDoneFn {

    // these directives are builtin escaping directives which are all pure
    // functions but not annotated.
    @SuppressWarnings("Immutable")
    private final ImmutableList<SoyJavaPrintDirective> directives;

    public EscapingBufferedRenderDoneFn(ImmutableList<SoyJavaPrintDirective> directives) {
      this.directives = directives;
    }

    @Override
    public void exec(LoggingAdvisingAppendable appendable, BufferingAppendable buffer)
        throws IOException {
      SoyValue resultData = buffer.getAsSoyValue();
      for (SoyJavaPrintDirective directive : directives) {
        resultData = directive.applyForJava(resultData, ImmutableList.of());
      }
      resultData.render(appendable);
    }
  }

  /** Wraps a compiled template with a buffer. */
  @Immutable
  private static final class BufferedCompiledTemplate implements CompiledTemplate {
    private final CompiledTemplate delegate;

    private final boolean ignoreExceptions;

    private final BufferedRenderDoneFn bufferedRenderDoneFn;

    static class SaveRestoreState {
      static final MethodHandle SAVE_STATE_METHOD_HANDLE;
      static final MethodHandle RESTORE_APPENDABLE_HANDLE;

      static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType saveMethodType =
            methodType(StackFrame.class, StackFrame.class, int.class, BufferingAppendable.class);
        SAVE_STATE_METHOD_HANDLE =
            SaveStateMetaFactory.bootstrapSaveState(lookup, "saveState", saveMethodType)
                .getTarget();
        RESTORE_APPENDABLE_HANDLE =
            SaveStateMetaFactory.bootstrapRestoreState(
                    lookup,
                    "restoreLocal",
                    methodType(BufferingAppendable.class, StackFrame.class),
                    saveMethodType,
                    0)
                .getTarget()
                .asType(methodType(BufferingAppendable.class, StackFrame.class));
      }
    }

    BufferedCompiledTemplate(
        CompiledTemplate delegate,
        boolean ignoreExceptions,
        BufferedRenderDoneFn bufferedRenderDoneFn) {
      this.delegate = checkNotNull(delegate);
      this.ignoreExceptions = ignoreExceptions;
      this.bufferedRenderDoneFn = bufferedRenderDoneFn;
    }

    @Nullable
    @Override
    public StackFrame render(
        StackFrame frame,
        ParamStore params,
        LoggingAdvisingAppendable appendable,
        RenderContext context)
        throws IOException {
      BufferingAppendable buffer;
      int state;
      StackFrame originalFrame = frame;
      if (frame == null) {
        state = 0;
      } else {
        state = frame.stateNumber;
        frame = frame.child;
      }
      switch (state) {
        case 0:
          buffer = LoggingAdvisingAppendable.buffering();
          break;
        case 1:
          try {
            buffer =
                (BufferingAppendable)
                    BufferedCompiledTemplate.SaveRestoreState.RESTORE_APPENDABLE_HANDLE.invokeExact(
                        originalFrame);
          } catch (Throwable t) {
            // the above is essentially a field read and should never fail.
            throw new AssertionError(t);
          }
          break;
        default:
          throw unexpectedStateError(frame);
      }
      try {
        frame = delegate.render(frame, params, buffer, context);
      } catch (RuntimeException e) {
        if (ignoreExceptions) {
          return null;
        }
        throw e;
      }
      if (frame == null) {
        bufferedRenderDoneFn.exec(appendable, buffer);
      } else {
        try {
          frame =
              (StackFrame)
                  BufferedCompiledTemplate.SaveRestoreState.SAVE_STATE_METHOD_HANDLE.invokeExact(
                      frame, 1, buffer);
        } catch (Throwable t) {
          throw new AssertionError(t);
        }
      }
      return frame;
    }
  }

  @Keep
  public static LogStatement createLogStatement(boolean logOnly, SoyValue value) {
    SoyVisualElementData veData = (SoyVisualElementData) value;
    return LogStatement.create(veData.ve().id(), veData.data(), logOnly);
  }

  @Keep
  public static LogStatement createLogStatement(SoyValue value) {
    SoyVisualElementData veData = (SoyVisualElementData) value;
    return LogStatement.create(veData.ve().id(), veData.data(), false);
  }


  /** Asserts that all members of the list are resolved. */
  @Keep
  @Nonnull
  public static <T extends SoyValueProvider> List<T> checkResolved(List<T> providerList) {
    for (int i = 0; i < providerList.size(); i++) {
      T provider = providerList.get(i);
      if (!(provider instanceof SoyValue)) {
        throw new IllegalStateException(
            "item " + i + " was expected to be a SoyValue, instead it is: " + provider.getClass());
      }
    }
    return providerList;
  }

  /** Asserts that all members of the map are resolved. */
  @Keep
  @Nonnull
  public static <K, V extends SoyValueProvider> Map<K, V> checkResolved(Map<K, V> providerMap) {
    for (Map.Entry<K, V> entry : providerMap.entrySet()) {
      V provider = entry.getValue();
      if (!(provider instanceof SoyValue)) {
        throw new IllegalStateException(
            "item "
                + entry.getKey()
                + " was expected to be a SoyValue, instead it is: "
                + provider.getClass());
      }
    }
    return providerMap;
  }

  /** For repeated extensions, returns all of the extensions values as a list. */
  @Keep
  @Nonnull
  public static <MessageT extends ExtendableMessage<MessageT>, T>
      LazyProtoToSoyValueList<T> getExtensionList(
          MessageT message,
          ExtensionLite<MessageT, List<T>> extension,
          ProtoFieldInterpreter protoFieldInterpreter) {
    ImmutableList.Builder<T> list = ImmutableList.builder();
    for (int i = 0; i < message.getExtensionCount(extension); i++) {
      list.add(message.getExtension(extension, i));
    }
    return LazyProtoToSoyValueList.forList(list.build(), protoFieldInterpreter);
  }

  @Nonnull
  @Keep
  public static TemplateValue bindTemplateParams(TemplateValue template, ParamStore boundParams) {
    return TemplateValue.create(
        template.getTemplateName(), new BoundTemplate(template, boundParams));
  }

  @Immutable
  private static final class BoundTemplate implements CompiledTemplate {
    static class SaveRestoreState {
      static final MethodHandle SAVE_STATE_METHOD_HANDLE;
      static final MethodHandle RESTORE_TEMPLATE_HANDLE;
      static final MethodHandle RESTORE_PARAMS_HANDLE;

      static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType saveMethodType =
            methodType(
                StackFrame.class,
                StackFrame.class,
                int.class,
                CompiledTemplate.class,
                ParamStore.class);
        SAVE_STATE_METHOD_HANDLE =
            SaveStateMetaFactory.bootstrapSaveState(lookup, "saveState", saveMethodType)
                .getTarget();
        RESTORE_TEMPLATE_HANDLE =
            SaveStateMetaFactory.bootstrapRestoreState(
                    lookup,
                    "restoreLocal",
                    methodType(CompiledTemplate.class, StackFrame.class),
                    saveMethodType,
                    0)
                .getTarget()
                .asType(methodType(CompiledTemplate.class, StackFrame.class));
        RESTORE_PARAMS_HANDLE =
            SaveStateMetaFactory.bootstrapRestoreState(
                    lookup,
                    "restoreLocal",
                    methodType(ParamStore.class, StackFrame.class),
                    saveMethodType,
                    1)
                .getTarget()
                .asType(methodType(ParamStore.class, StackFrame.class));
      }
    }

    private final String name;

    @SuppressWarnings("Immutable") // this is never mutated
    private final ParamStore boundParams;

    private final CompiledTemplate delegate;

    BoundTemplate(TemplateValue value, ParamStore boundParams) {
      // unwrap delegation by eagerly merging params, this removes layers of indirection at call
      // time
      var delegate = (CompiledTemplate) value.compiledTemplate().orElse(null);
      if (delegate instanceof BoundTemplate) {
        BoundTemplate partiallyBoundTemplate = (BoundTemplate) delegate;
        boundParams = ParamStore.merge(partiallyBoundTemplate.boundParams, boundParams);
        this.delegate = partiallyBoundTemplate.delegate;
      } else {
        this.delegate = delegate;
      }
      this.name = value.getTemplateName();
      this.boundParams = ParamStore.merge(value.getBoundParameters(), boundParams);
    }

    @Nullable
    @Override
    public StackFrame render(
        @Nullable StackFrame frame,
        ParamStore params,
        LoggingAdvisingAppendable appendable,
        RenderContext context)
        throws IOException {
      CompiledTemplate delegate;
      ParamStore mergedParams;
      int state;
      StackFrame childFrame;
      if (frame == null) {
        state = 0;
        childFrame = null;
      } else {
        state = frame.stateNumber;
        childFrame = frame.child;
      }
      switch (state) {
        case 0:
          delegate = this.delegate;
          // Delegate is null when the template is passed from java as a parameter. Resolve it on
          // demand.
          if (delegate == null) {
            delegate = context.getTemplate(name);
          }
          mergedParams = ParamStore.merge(boundParams, params);
          break;
        case 1:
          try {
            delegate =
                (CompiledTemplate) SaveRestoreState.RESTORE_TEMPLATE_HANDLE.invokeExact(frame);
            mergedParams = (ParamStore) SaveRestoreState.RESTORE_PARAMS_HANDLE.invokeExact(frame);
          } catch (Throwable t) {
            // the above are essentially field reads and should never fail.
            throw new AssertionError(t);
          }
          break;
        default:
          throw unexpectedStateError(frame);
      }
      frame = delegate.render(childFrame, mergedParams, appendable, context);
      if (frame == null) {
        return null;
      }
      try {
        return (StackFrame)
            SaveRestoreState.SAVE_STATE_METHOD_HANDLE.invokeExact(frame, 1, delegate, mergedParams);
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
    }
  }

  public static CompiledTemplate getCompiledTemplate(TemplateValue template) {
    var delegate = (CompiledTemplate) template.compiledTemplate().orElse(null);
    if (delegate != null && template.getBoundParameters().isEmpty()) {
      // This is likely a template literal from the code generator, so we can just return the
      // delegate.
      return delegate;
    }
    return new BoundTemplate(template, ParamStore.EMPTY_INSTANCE);
  }

  /**
   * Helper method to fully await for a {@link SoyValueProvider} to resolve. NOTE: this may induce
   * blocking.
   *
   * <p>Used by the implementations of our subclasses used to implement {@code let}{@code param} and
   * {@code msg} placeholders.
   */
  static void awaitProvider(SoyValueProvider provider) {
    while (true) {
      RenderResult result = provider.status();
      switch (result.type()) {
        case LIMITED:
          // Docs on SoyValueProvider.status() call this state illegal.
          throw new AssertionError(
              "SoyValueProvider.status() returned a RenderResult.limited() which is out of spec");
        case DETACH:
          Future<?> future = result.future();
          if (logger.isLoggable(Level.WARNING)) {
            logger.log(
                Level.WARNING,
                "blocking to resolve a SoyValueProvider: " + future,
                new Exception());
          }
          try {
            future.get();
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // restore interrupted bit
            throw new RuntimeException(
                "Interrupted while waiting on: " + future + " to complete", ie);
          } catch (CancellationException | ExecutionException expected) {
            // ignore these here, both of these are final states for the future.  When calling back
            // into status() the provider should end up dereferencing the future which should ensure
            // that an exception is thrown with the correct stack trace.
          }
          break;
        case DONE:
          return;
      }
    }
  }

  @Keep
  public static boolean isNonSoyNullish(SoyValueProvider value) {
    return !value.resolve().isNullish();
  }

  @Keep
  public static boolean isNonSoyNull(SoyValueProvider value) {
    return !value.resolve().isNull();
  }

  @Keep
  @Nullable
  public static SoyValue soyNullishToJavaNull(SoyValue value) {
    return value == null || value.isNullish() ? null : value;
  }

  @Keep
  @Nullable
  public static SoyValue soyNullToJavaNull(SoyValue value) {
    return value == null || value.isNull() ? null : value;
  }

  @Keep
  @Nonnull
  public static <T> T checkExpressionNotNull(T value, String expression) {
    if (value == null || (value instanceof SoyValue && ((SoyValue) value).isNullish())) {
      throw new NullPointerException("'" + expression + "' evaluates to null");
    }
    return value;
  }

  @Nonnull
  @Keep
  public static String base64Encode(ByteString byteString) {
    return BaseEncoding.base64().encode(byteString.toByteArray());
  }

  @Nonnull
  @Keep
  public static ByteString base64Decode(String base64) {
    return ByteString.copyFrom(BaseEncoding.base64().decode(base64));
  }

  public static int asSwitchableValue(long value, int unusedKey) {
    int asInt = (int) value;
    // If when coerced to an int, it is equal to value, then we can losslessly use it as a switch
    // expression, otherwise we map to an unused key
    if (asInt == value) {
      return asInt;
    }
    return unusedKey;
  }

  public static int asSwitchableValue(double value, int unusedKey) {
    int asInt = (int) value;
    // If when coerced to an int, it is equal to value, then we have an integer encoded as a double
    if (asInt == value) {
      return asInt;
    }
    return unusedKey;
  }

  public static int asSwitchableValue(SoyValue value, int unusedKey) {
    if (value instanceof NumberData) {
      return asSwitchableValue(value.numberValue(), unusedKey);
    }
    return unusedKey;
  }

  public static SoyValue emptyToUndefined(SoyValue value) {
    return value.stringValue().isEmpty() ? UndefinedData.INSTANCE : value;
  }

  /**
   * Holds some rarely needed constants so that we can defer initializing them in the common case
   */
  @VisibleForTesting
  public static final class EveryDetachStateForTesting {
    private static final Set<Object> visited = Sets.newConcurrentHashSet();

    private static final StackFrame TRIVIAL_PENDING =
        StackFrame.create(RenderResult.continueAfter(Futures.immediateVoidFuture()));

    public static void clear() {
      visited.clear();
    }

    public static boolean maybeForceLimited(boolean actual, Object callsite) {
      return actual || EveryDetachStateForTesting.visited.add(callsite);
    }

    @Nullable
    public static StackFrame maybeForceContinueAfter(Object callsite) {
      return maybeForceContinueAfter((StackFrame) null, callsite);
    }

    @Nullable
    public static StackFrame maybeForceContinueAfter(@Nullable StackFrame actual, Object callsite) {
      if (actual == null && EveryDetachStateForTesting.visited.add(callsite)) {
        actual = EveryDetachStateForTesting.TRIVIAL_PENDING;
      }
      return actual;
    }

    public static RenderResult maybeForceContinueAfter(RenderResult actual, Object callsite) {
      if (actual.isDone() && EveryDetachStateForTesting.visited.add(callsite)) {
        actual = EveryDetachStateForTesting.TRIVIAL_PENDING.asRenderResult();
      }
      return actual;
    }

    private EveryDetachStateForTesting() {}
  }

  private JbcSrcRuntime() {}
}
