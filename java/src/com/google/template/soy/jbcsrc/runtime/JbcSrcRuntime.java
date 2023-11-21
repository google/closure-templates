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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.GoogleLogger;
import com.google.common.flogger.StackSize;
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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.SaveStateMetaFactory;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
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
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  @Nonnull
  public static AssertionError unexpectedStateError(StackFrame frame) {
    return new AssertionError("Unexpected state requested: " + frame.stateNumber);
  }

  /**
   * Every {@code debugger} statement will call this method. You can use conditional breakpoints
   * here to easily stop execution at the right location.
   */
  public static void debugger(String fileName, int lineNumber) {
    logger.atWarning().withStackTrace(StackSize.MEDIUM).log(
        "Hit {debugger} statement at %s:%d. Put a breakpoint here to halt Soy rendering.",
        fileName, lineNumber);
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
  public static SoyValueProvider soyValueProviderOrNullish(SoyValueProvider provider) {
    SoyValue value = provider.resolve();
    // Nastiness needed for b/161534927
    return value.isNullish() ? value : provider;
  }

  @Keep
  @Nonnull
  public static SoyValue getField(SoyValue record, RecordProperty field) {
    if (record.isNullish()) {
      throw new NullPointerException("Attempted to access field '" + field.getName() + "' of null");
    }
    SoyValue value = ((SoyRecord) record).getField(field);
    return value != null ? value : NullData.INSTANCE;
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getFieldProvider(SoyValue record, RecordProperty field) {
    if (record.isNullish()) {
      throw new NullPointerException("Attempted to access field '" + field.getName() + "' of null");
    }
    return paramOrDefault(
        ((SoyRecord) record).getFieldProvider(field), /* defaultValue= */ NullData.INSTANCE);
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getParameter(
      ParamStore paramStore, RecordProperty field, SoyValue defaultValue) {
    return paramOrDefault(paramStore.getFieldProvider(field), defaultValue);
  }

  @Keep
  @Nonnull
  public static SoyValueProvider getParameter(ParamStore paramStore, RecordProperty field) {
    return paramOrDefault(
        paramStore.getFieldProvider(field), /* defaultValue= */ NullData.INSTANCE);
  }

  /**
   * Interprets a passed parameter. Handling tofu null and reinterpreting null as MISSING_PARAMETER
   */
  @Keep
  @Nonnull
  public static SoyValueProvider param(SoyValueProvider provider) {
    return paramOrDefault(provider, NullData.INSTANCE);
  }

  /**
   * Returns a passed parameter or its default value if no such parameter exists. Pass {@link
   * UndefinedData} for {@code provider} to indicate no such parameter exists and the default should
   * be applied. This behavior is coupled to {@link SoyRecord#getPositionalParam(String)}.
   */
  @Keep
  @Nonnull
  public static SoyValueProvider paramOrDefault(
      @Nullable SoyValueProvider provider, SoyValue defaultValue) {

    return provider == null || provider == UndefinedData.INSTANCE ? defaultValue : provider;
  }

  /**
   * Helper function to translate null -> NullData when calling LegacyFunctionAdapters that may
   * expect it.
   *
   * <p>In the long run we should either fix ToFu (and all SoyJavaFunctions) to not use NullData or
   * we should introduce custom SoyFunction implementations for have come from SoyValueProvider.
   */
  @Keep
  public static SoyValue callLegacySoyFunction(
      LegacyFunctionAdapter fnAdapter, List<SoyValue> args) {
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) == null) {
        args.set(i, NullData.INSTANCE);
      }
    }
    return fnAdapter.computeForJava(args);
  }

  /**
   * Helper function to translate null -> NullData when calling SoyJavaPrintDirectives that may
   * expect it.
   */
  @Keep
  @Nonnull
  public static SoyValue applyPrintDirective(
      SoyJavaPrintDirective directive, SoyValue value, List<SoyValue> args) {
    value = value == null ? NullData.INSTANCE : value;
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) == null) {
        args.set(i, NullData.INSTANCE);
      }
    }
    return Preconditions.checkNotNull(directive.applyForJava(value, args));
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
      return soyValueProvider == null ? NullData.INSTANCE : soyValueProvider;
    }
    return NullData.INSTANCE;
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
    return soyValueProvider == null ? NullData.INSTANCE : soyValueProvider;
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
    return soyValueProvider == null ? NullData.INSTANCE : soyValueProvider;
  }

  @Keep
  @Nonnull
  public static String handleBasicTranslation(List<SoyMsgPart> parts) {
    return ((SoyMsgRawTextPart) parts.get(0)).getRawText();
  }

  @Keep
  @Nonnull
  public static String handleBasicTranslationAndEscapeHtml(List<SoyMsgPart> parts) {
    return MsgRenderer.escapeHtml(handleBasicTranslation(parts));
  }

  /**
   * A Message renderer represents a message to be rendered. It encapsulates the placeholders and
   * message parts and can dynamically render them. This manages a small state machine that allows
   * for rendering to proceed.
   */
  public static class MsgRenderer implements SoyValueProvider {
    ImmutableList<SoyMsgPart> msgParts;
    final ULocale locale;
    private int partIndex;
    private SoyValueProvider pendingRender;
    final Map<String, SoyValueProvider> placeholders;

    // Some placeholders have ordering constraints.  This is necessary for the velog to function
    // correctly in the face of translators reordering things.
    // The constraints are simply that an end tag must come after a start tag
    @Nullable Set<String> startPlaceholders;
    @Nullable Multiset<String> startPlaceholderRenderCount;
    // an optional map from a placeholder to another placeholder that must precede it.
    @Nullable SetMultimap<String, String> endPlaceholderToStartPlaceholder;
    private final long msgId;
    private final boolean htmlEscape;
    private BufferingAppendable buffer;
    private boolean isDone;

    public MsgRenderer(
        long msgId,
        ImmutableList<SoyMsgPart> msgParts,
        @Nullable ULocale locale,
        int numPlaceholders,
        boolean htmlEscape) {
      this.msgId = msgId;
      this.msgParts = msgParts;
      this.locale = locale;
      this.placeholders = Maps.newLinkedHashMapWithExpectedSize(numPlaceholders);
      this.htmlEscape = htmlEscape;
    }

    /**
     * Sets a placeholder value.
     *
     * @param placeholderName The placeholder name
     * @param placeholderValue The placeholder value.
     */
    @CanIgnoreReturnValue
    @Nonnull
    public MsgRenderer setPlaceholder(String placeholderName, SoyValueProvider placeholderValue) {
      Object prev = placeholders.put(placeholderName, placeholderValue);
      if (prev != null) {
        throw new IllegalArgumentException(
            "found multiple placeholders: "
                + prev
                + " and "
                + placeholderValue
                + " for key "
                + placeholderName);
      }
      return this;
    }

    static String escapeHtml(String s) {
      // Note that "&" is not replaced because the translation can contain HTML entities.
      return s.replace("<", "&lt;");
    }

    /**
     * Sets a placeholder and declares that it must come before {@code endPlaceholder}.
     *
     * <p>This is necessary to enforce the constraints of the velogging system.
     *
     * @param placeholderName The placeholder name
     * @param placeholderValue The placeholder value. For a normal placeholder this will be a
     *     SoyValueProvider
     * @param endPlaceholder The name of another placeholder that _must_ come _after_ this one.
     */
    @CanIgnoreReturnValue
    @Nonnull
    public MsgRenderer setPlaceholderAndOrdering(
        String placeholderName, SoyValueProvider placeholderValue, String endPlaceholder) {
      if (endPlaceholderToStartPlaceholder == null) {
        startPlaceholders = new HashSet<>();
        endPlaceholderToStartPlaceholder = HashMultimap.create();
        startPlaceholderRenderCount = HashMultiset.create();
      }
      // We need to check that our ordering constraints make sense.
      // the placeholderName shouldn't be the 'after' node of any other node and the endPlaceholder
      // shouldn't be the before node of any other node.
      // The edges in this ordering graph should create a forest of trees of depth 1.
      if (endPlaceholderToStartPlaceholder.containsKey(placeholderName)) {
        throw new IllegalArgumentException(
            String.format(
                "%s is supposed to come after %s but before %s. Order constraints should not be "
                    + "transitive.",
                placeholderName,
                // just use one of them, there is normally only one
                endPlaceholderToStartPlaceholder.get(placeholderName).iterator().next(),
                endPlaceholder));
      }
      if (startPlaceholders.contains(endPlaceholder)) {
        String beforePlaceholder = null;
        // scan to find the placeholder that is supposed to come after this one.
        for (Map.Entry<String, String> entry : endPlaceholderToStartPlaceholder.entries()) {
          if (endPlaceholder.equals(entry.getValue())) {
            beforePlaceholder = entry.getKey();
            break;
          }
        }
        throw new IllegalArgumentException(
            String.format(
                "%s is supposed to come after %s but before %s. Order constraints should not be "
                    + "transitive.",
                endPlaceholder, placeholderName, beforePlaceholder));
      }
      setPlaceholder(placeholderName, placeholderValue);
      endPlaceholderToStartPlaceholder.put(endPlaceholder, placeholderName);
      startPlaceholders.add(placeholderName);
      return this;
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
      return buffer.getAsSoyValue();
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
      for (int i = partIndex; i < msgParts.size(); i++) {
        SoyMsgPart msgPart = msgParts.get(i);
        if (msgPart instanceof SoyMsgRawTextPart) {
          String s = ((SoyMsgRawTextPart) msgPart).getRawText();
          if (htmlEscape) {
            s = escapeHtml(s);
          }
          out.append(s);
        } else if (msgPart instanceof SoyMsgPlaceholderPart) {
          String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
          if (endPlaceholderToStartPlaceholder != null) {
            if (startPlaceholders.contains(placeholderName)) {
              startPlaceholderRenderCount.add(placeholderName);
            } else {
              // check if it is an end tag
              Set<String> startPlaceholders = endPlaceholderToStartPlaceholder.get(placeholderName);
              if (!startPlaceholders.isEmpty()) {
                // make sure the start tag has been rendered
                boolean matched = false;
                for (String startPlaceholder : startPlaceholders) {
                  if (startPlaceholderRenderCount.remove(startPlaceholder)) {
                    matched = true;
                    break;
                  }
                }
                if (!matched) {
                  // uhoh
                  throw new IllegalStateException(
                      String.format(
                          "Expected placeholder '%s' to come after one of %s, in message %d",
                          placeholderName, startPlaceholders, msgId));
                }
              }
            }
          }
          SoyValueProvider placeholderValue = placeholders.get(placeholderName);
          if (placeholderValue == null) {
            throw new IllegalStateException(
                "No value provided for placeholder: '"
                    + placeholderName
                    + "', expected one of "
                    + placeholders.keySet());
          }
          try {
            RenderResult result = placeholderValue.renderAndResolve(out);
            if (!result.isDone()) {
              // store partIndex as i + 1 so that after the placeholder is done we proceed to the
              // next part
              partIndex = i + 1;
              pendingRender = placeholderValue;
              return result;
            }
          } catch (IllegalStateException e) {
            throw new IllegalStateException(placeholderName, e);
          }
        } else if (msgPart instanceof SoyMsgPluralRemainderPart) {
          // this is weird... shouldn't this be using a number format?
          out.append(String.valueOf(getPluralRemainder()));
        } else {
          throw new AssertionError("unexpected part: " + msgPart);
        }
      }
      if (startPlaceholderRenderCount != null && !startPlaceholderRenderCount.isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "The following placeholders never had their matching placeholders rendered in"
                    + " message %d: %s",
                msgId, startPlaceholderRenderCount.elementSet()));
      }
      return RenderResult.done();
    }

    double getPluralRemainder() {
      throw new UnsupportedOperationException(
          "this is not a plural message so remainder don't make sense");
    }
  }

  /** A MsgRenderer for plural or select style messages. */
  public static final class PlrSelMsgRenderer extends MsgRenderer {
    private boolean resolvedCases;
    // only one plural is allowed per message so we only need to track one remainder.
    private double remainder = -1;

    public PlrSelMsgRenderer(
        long msgId,
        ImmutableList<SoyMsgPart> msgParts,
        @Nullable ULocale locale,
        int numPlaceholders,
        boolean htmlEscape) {
      super(msgId, msgParts, locale, numPlaceholders, htmlEscape);
    }

    @Override
    public RenderResult renderAndResolve(LoggingAdvisingAppendable out) throws IOException {
      if (!resolvedCases) {
        // plural/select messages always start with a sequence of plural and select values.
        // Additionally, we are guaranteed (by contract with the gencode) that the plural/select
        // variables are resolved.  So we need to do that now.
        // NOTE: that in the most common case, this loop only executes once and at maximum it will
        // loop 3 times.  We do know statically what the first iteration will be, but it is not
        // possible to know anything beyond that.
        ImmutableList<SoyMsgPart> parts = this.msgParts;
        RenderResult caseSelectionResult = RenderResult.done();
        while (!parts.isEmpty()) {
          SoyMsgPart first = parts.get(0);
          if (first instanceof SoyMsgSelectPart) {
            SoyMsgSelectPart selectPart = (SoyMsgSelectPart) first;
            SoyValueProvider selectPlaceholder = placeholders.get(selectPart.getSelectVarName());
            caseSelectionResult = selectPlaceholder.status();
            if (caseSelectionResult.isDone()) {
              // Handle null results by coercing to 'null' for compatibility with javascript
              parts = selectPart.lookupCase(coerceToString(selectPlaceholder.resolve()));
            } else {
              break;
            }
          } else if (first instanceof SoyMsgPluralPart) {
            SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) first;
            SoyValueProvider pluralPlaceholder = placeholders.get(pluralPart.getPluralVarName());
            caseSelectionResult = pluralPlaceholder.status();
            if (caseSelectionResult.isDone()) {
              double pluralValue = pluralPlaceholder.resolve().numberValue();
              parts = pluralPart.lookupCase(pluralValue, locale);
              // precalculate and store the remainder.
              remainder = pluralValue - pluralPart.getOffset();
            } else {
              break;
            }
          } else {
            break;
          }
        }
        // Store any progress we have made in calculating sub-parts.
        this.msgParts = parts;
        if (!caseSelectionResult.isDone()) {
          return caseSelectionResult;
        }
        resolvedCases = true;
      }
      // render the cases.
      return super.renderAndResolve(out);
    }

    @Override
    double getPluralRemainder() {
      return remainder;
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
        protected void doAppend(DeferredText supplier) {
          System.out.append(supplier.getStringForCoercion());
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
  public static boolean coerceToBoolean(SoyValue v) {
    return v.coerceToBoolean();
  }

  @Keep
  public static boolean coerceToBoolean(@Nullable String v) {
    return v != null && !v.isEmpty();
  }

  @Keep
  @Nonnull
  public static String coerceToString(@Nullable SoyValue v) {
    return v == null ? "null" : v.coerceToString();
  }

  /** Function to execute after rendering of a section of buffered template is done. */
  @Immutable
  public static interface BufferedRenderDoneFn {
    public void exec(LoggingAdvisingAppendable appendable, BufferingAppendable buffer)
        throws IOException;
  }

  /** Replays commands to the main appendable, including logging. */
  public static class ReplayingBufferedRenderDoneFn implements BufferedRenderDoneFn {
    @Override
    public void exec(LoggingAdvisingAppendable appendable, BufferingAppendable buffer)
        throws IOException {
      buffer.replayOn(appendable);
    }
  }

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
      appendable.append(resultData.coerceToString());
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
            methodType(void.class, RenderContext.class, int.class, BufferingAppendable.class);
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
                .getTarget();
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

    @Override
    public RenderResult render(
        ParamStore params,
        ParamStore ij,
        LoggingAdvisingAppendable appendable,
        RenderContext context)
        throws IOException {
      StackFrame frame = context.popFrame();
      BufferingAppendable buffer;
      switch (frame.stateNumber) {
        case 0:
          buffer = LoggingAdvisingAppendable.buffering();
          break;
        case 1:
          try {
            buffer =
                (BufferingAppendable)
                    BufferedCompiledTemplate.SaveRestoreState.RESTORE_APPENDABLE_HANDLE.invokeExact(
                        frame);
          } catch (Throwable t) {
            throw new AssertionError(t);
          }
          break;
        default:
          throw unexpectedStateError(frame);
      }
      RenderResult result;
      try {
        result = delegate.render(params, ij, buffer, context);
      } catch (RuntimeException e) {
        if (ignoreExceptions) {
          return RenderResult.done();
        }
        throw e;
      }
      if (result.isDone()) {
        bufferedRenderDoneFn.exec(appendable, buffer);
      } else {
        try {
          BufferedCompiledTemplate.SaveRestoreState.SAVE_STATE_METHOD_HANDLE.invokeExact(
              context, 1, buffer);
        } catch (Throwable t) {
          throw new AssertionError(t);
        }
      }
      return result;
    }
  }

  @Keep
  public static LogStatement createLogStatement(boolean logOnly, SoyValue value) {
    if (value == null || value.isNullish()) {
      throw new NullPointerException();
    }
    SoyVisualElementData veData = (SoyVisualElementData) value;
    return LogStatement.create(veData.ve().id(), veData.data(), logOnly);
  }

  private static SanitizedContent doFlushLogsAndRender(
      SoyValueProvider delegate, SoyLogger logger) {
    Preconditions.checkState(
        delegate.status().isDone(),
        "Soy generated code should ensure this is done before calling.");
    StringBuilder output = new StringBuilder();
    // Create our own OutputAppendable so we can use the current state of the SoyLogger, but
    // render to our own StringBuilder to return the rendered content.
    OutputAppendable content = OutputAppendable.create(output, logger);
    try {
      // Go through renderAndResolve because we need to replay the logging commands. This is
      // already fully resolved so this will replay everything.
      RenderResult result = delegate.renderAndResolve(content);
      checkState(result.isDone());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    // The result is the same HTML that came in, except with logging statements removed. So it's
    // safe to ordain as HTML (with an assert just to make sure).
    checkState(content.getSanitizedContentKind() == ContentKind.HTML);
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        output.toString(), ContentKind.HTML, content.getSanitizedContentDirectionality());
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
    var newTemplate =
        new PartiallyBoundTemplate(boundParams, (CompiledTemplate) template.getCompiledTemplate());
    return TemplateValue.createWithBoundParameters(
        template.getTemplateName(), newTemplate.boundParams, newTemplate);
  }

  @Immutable
  private static final class PartiallyBoundTemplate implements CompiledTemplate {
    @SuppressWarnings("Immutable") // this is never mutated
    private final ParamStore boundParams;

    private final CompiledTemplate delegate;

    PartiallyBoundTemplate(ParamStore boundParams, CompiledTemplate delegate) {
      // unwrap delegation by eagerly merging params, this removes layers of indirection at call
      // time
      if (delegate instanceof PartiallyBoundTemplate) {
        PartiallyBoundTemplate partiallyBoundTemplate = (PartiallyBoundTemplate) delegate;
        boundParams = ParamStore.merge(partiallyBoundTemplate.boundParams, boundParams);
        delegate = partiallyBoundTemplate.delegate;
      }
      this.delegate = delegate;
      this.boundParams = boundParams;
    }

    @Override
    public RenderResult render(
        ParamStore params,
        ParamStore ij,
        LoggingAdvisingAppendable appendable,
        RenderContext context)
        throws IOException {
      return delegate.render(ParamStore.merge(boundParams, params), ij, appendable, context);
    }
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
          logger.atWarning().withStackTrace(StackSize.FULL).log(
              "blocking to resolve a SoyValueProvider: %s", future);
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

  public static SoyValue emptyToNull(SoyValue value) {
    return value.stringValue().isEmpty() ? NullData.INSTANCE : value;
  }

  /**
   * Holds some rarely needed constants so that we can defer initializing them in the common case
   */
  @VisibleForTesting
  public static final class EveryDetachStateForTesting {
    private static final Set<Object> visited = Sets.newConcurrentHashSet();

    private static final RenderResult TRIVIAL_PENDING =
        RenderResult.continueAfter(Futures.immediateVoidFuture());

    public static void clear() {
      visited.clear();
    }

    public static boolean maybeForceLimited(boolean actual, Object callsite) {
      return actual || EveryDetachStateForTesting.visited.add(callsite);
    }

    public static RenderResult maybeForceContinueAfter(RenderResult actual, Object callsite) {
      if (actual.isDone() && EveryDetachStateForTesting.visited.add(callsite)) {
        actual = EveryDetachStateForTesting.TRIVIAL_PENDING;
      }
      return actual;
    }

    private EveryDetachStateForTesting() {}
  }

  /**
   * A functional interface implemented by the code generator purely to pass to `appendAsSupplier`
   */
  @FunctionalInterface
  public interface DeferredHtmlFactory {
    SoyValue invoke(SoyValue deferredHtml);
  }

  public static void appendAsDeferredText(
      LoggingAdvisingAppendable appendable,
      RenderContext ctx,
      DeferredHtmlFactory factory,
      SoyValueProvider provider)
      throws IOException {
    appendable.append(
        (isOutputAppendable) -> {
          if (isOutputAppendable) {
            try (RenderContext.DeferredLoggingContext logContext = ctx.beginDeferredLogging()) {
              SanitizedContent content = doFlushLogsAndRender(provider, logContext.logger());
              if (logContext.isReentrant()) {
                return content.stringValue();
              }
              return factory.invoke(content).stringValue();
            }
          } else {
            logger.atWarning().withStackTrace(StackSize.MEDIUM).log(
                "evaluating deferred_html in a context that needs to be escaped, Logging behavior"
                    + " is undefined and deferral is skipped when this happens.");
            return provider.resolve().stringValue();
          }
        });
  }

  private JbcSrcRuntime() {}
}
