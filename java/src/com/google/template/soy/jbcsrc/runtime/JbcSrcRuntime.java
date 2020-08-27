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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.template.soy.data.AbstractLoggingAdvisingAppendable;
import com.google.template.soy.data.ForwardingLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyRecords;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.SoyLegacyObjectMapImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.internal.ShortCircuitables;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
  public static final SoyValueProvider NULL_PROVIDER =
      new SoyValueProvider() {
        @Override
        public RenderResult status() {
          return RenderResult.done();
        }

        @Override
        public SoyValue resolve() {
          return null;
        }

        @Override
        public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
            throws IOException {
          appendable.append("null");
          return RenderResult.done();
        }

        @Override
        public String toString() {
          return "NULL_PROVIDER";
        }
      };

  public static AssertionError unexpectedStateError(int state) {
    return new AssertionError("Unexpected state requested: " + state);
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

  /** Helper function to translate NullData -> null when resolving a SoyValueProvider. */
  public static SoyValue resolveSoyValueProvider(SoyValueProvider provider) {
    SoyValue value = provider.resolve();
    return handleTofuNull(value);
  }

  public static SoyValueProvider soyValueProviderOrNull(SoyValueProvider provider) {
    if (provider == null || resolveSoyValueProvider(provider) == null) {
      return null;
    }
    return provider;
  }

  private static SoyValue handleTofuNull(SoyValue value) {
    if (value instanceof NullData | value instanceof UndefinedData) {
      return null;
    }
    return value;
  }

  /**
   * Helper function to make SoyRecord.getFieldProvider a non-nullable function by returning {@link
   * #NULL_PROVIDER} for missing fields.
   */
  public static SoyValueProvider getFieldProvider(
      SoyRecord record, String field, @Nullable SoyValue defaultValue) {
    checkNotNull(record, "Attempted to access field '%s' of null", field);
    // TODO(lukes): ideally this would be the behavior of getFieldProvider, but Tofu relies on it
    // returning null to interpret it as 'undefined'. http://b/20537225 describes the issues in Tofu
    SoyValueProvider provider = record.getFieldProvider(field);
    if (provider == null) {
      if (defaultValue == null) {
        return NULL_PROVIDER;
      }
      return defaultValue;
    } else if (provider instanceof NullData) {
      return NULL_PROVIDER;
    }
    return provider;
  }

  public static SoyValueProvider getFieldProvider(SoyRecord record, String field) {
    return getFieldProvider(record, field, /* defaultValue= */ null);
  }

  /** Casts the given type to SoyString or throws a ClassCastException. */
  public static SoyString checkSoyString(Object o) {
    // if it isn't a sanitized content we don't want to warn and if it isn't a soystring we should
    // always fail.
    if (o instanceof SoyString
        && o instanceof SanitizedContent
        && logger.isLoggable(Level.WARNING)) {
      logger.log(
          Level.WARNING,
          String.format(
              "Passing in sanitized content into a template that accepts only string is forbidden. "
                  + " Please modify the template to take in %s.",
              ((SanitizedContent) o).getContentKind()),
          new Exception());
    }
    return (SoyString) o;
  }

  /**
   * Helper function to translate null -> NullData when calling LegacyFunctionAdapters that may
   * expect it.
   *
   * <p>In the long run we should either fix ToFu (and all SoyJavaFunctions) to not use NullData or
   * we should introduce custom SoyFunction implementations for have come from SoyValueProvider.
   */
  public static SoyValue callLegacySoyFunction(
      LegacyFunctionAdapter fnAdapter, List<SoyValue> args) {
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) == null) {
        args.set(i, NullData.INSTANCE);
      }
    }
    return handleTofuNull(fnAdapter.computeForJava(args));
  }

  /**
   * Helper function to translate null -> NullData when calling SoyJavaPrintDirectives that may
   * expect it.
   */
  public static SoyValue applyPrintDirective(
      SoyJavaPrintDirective directive, SoyValue value, List<SoyValue> args) {
    value = value == null ? NullData.INSTANCE : value;
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) == null) {
        args.set(i, NullData.INSTANCE);
      }
    }
    return directive.applyForJava(value, args);
  }

  /**
   * Wraps a given template with a collection of escapers to apply.
   *
   * @param delegate The delegate template to render
   * @param directives The set of directives to apply
   */
  public static CompiledTemplate applyEscapers(
      CompiledTemplate delegate, ImmutableList<SoyJavaPrintDirective> directives) {
    checkState(!directives.isEmpty());
    ContentKind kind = delegate.kind();
    directives = ShortCircuitables.filterDirectivesForKind(kind, directives);
    if (directives.isEmpty()) {
      // everything was filtered, common for for delcalls from compatible contexts (html -> html)
      return delegate;
    }
    return new EscapedCompiledTemplate(delegate, directives, kind);
  }

  public static SoyValueProvider getSoyListItem(List<SoyValueProvider> list, long index) {
    if (list == null) {
      throw new NullPointerException("Attempted to access list item '" + index + "' of null");
    }
    int size = list.size();
    // use & instead of && to avoid a branch
    if (index < size & index >= 0) {
      SoyValueProvider soyValueProvider = list.get((int) index);
      return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
    }
    return NULL_PROVIDER;
  }

  public static RenderResult getListStatus(List<? extends SoyValueProvider> soyValueProviders) {
    // avoid allocating an iterator
    int size = soyValueProviders.size();
    for (int i = 0; i < size; i++) {
      RenderResult result = soyValueProviders.get(i).status();
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

  public static SoyValueProvider getSoyMapItem(SoyMap soyMap, SoyValue key) {
    if (soyMap == null) {
      throw new NullPointerException("Attempted to access map item '" + key + "' of null");
    }
    if (key == null) {
      key = NullData.INSTANCE;
    }
    SoyValueProvider soyValueProvider = soyMap.getProvider(key);
    return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
  }

  public static SoyValueProvider getSoyLegacyObjectMapItem(
      SoyLegacyObjectMap legacyObjectMap, SoyValue key) {
    if (legacyObjectMap == null) {
      throw new NullPointerException("Attempted to access map item '" + key + "' of null");
    }
    SoyValueProvider soyValueProvider = legacyObjectMap.getItemProvider(key);
    return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
  }

  /**
   * A Message renderer represents a message to be rendered. It encapsulates the placeholders and
   * message parts and can dynamically render them. This manages a small state machine that allows
   * for rendering to proceed.
   */
  public static class MsgRenderer extends DetachableContentProvider {
    ImmutableList<SoyMsgPart> msgParts;
    final ULocale locale;
    private int partIndex;
    private SoyValueProvider pendingRender;
    final Map<String, Object> placeholders;

    // Some placeholders have ordering constraints.  This is necessary for the velog to function
    // correctly in the face of translators reordering things.
    // The constraints are simply that an end tag must come after a start tag
    @Nullable Set<String> startPlaceholders;
    @Nullable Multiset<String> startPlaceholderRenderCount;
    // an optional map from a placeholder to another placeholder that must precede it.
    @Nullable SetMultimap<String, String> endPlaceholderToStartPlaceholder;
    private final long msgId;
    private final boolean htmlEscape;

    public MsgRenderer(
        long msgId,
        ImmutableList<SoyMsgPart> msgParts,
        @Nullable ULocale locale,
        int numPlaceholders,
        boolean htmlEscape) {
      // using a TEXT content kind which will cause our base class to box the value in a StringData
      // object
      super(ContentKind.TEXT);
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
     * @param placeholderValue The placeholder value. For a normal placeholder this will be a
     *     SoyValueProvider but for plurals this will be an IntegerData and for selects this will be
     *     a string.
     */
    public void setPlaceholder(String placeholderName, Object placeholderValue) {
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
    }

    public static String escapeHtml(String s) {
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
     *     SoyValueProvider but for plurals this will be an IntegerData and for selects this will be
     *     a string.
     * @param endPlaceholder The name of another placeholder that _must_ come _after_ this one.
     */
    public void setPlaceholderAndOrdering(
        String placeholderName, Object placeholderValue, String endPlaceholder) {
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
                "%s is supposed to come after %s but before %s. Order contraints should not be "
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
                "%s is supposed to come after %s but before %s. Order contraints should not be "
                    + "transitive.",
                endPlaceholder, placeholderName, beforePlaceholder));
      }
      setPlaceholder(placeholderName, placeholderValue);
      endPlaceholderToStartPlaceholder.put(endPlaceholder, placeholderName);
      startPlaceholders.add(placeholderName);
    }

    /**
     * Renders the message to the given output stream incrementally.
     *
     * <p>Currently this doesn't check for {@link LoggingAdvisingAppendable#softLimitReached()}
     * though such support could be easily added. The justification is that messages tend to be
     * small.
     */
    @Override
    public RenderResult doRender(LoggingAdvisingAppendable out) throws IOException {
      // if we were in the middle of a placeholder render, finish that.
      if (pendingRender != null) {
        RenderResult result = pendingRender.renderAndResolve(out, /* isLast= */ false);
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
          SoyValueProvider placeholderValue = (SoyValueProvider) placeholders.get(placeholderName);
          if (placeholderValue == null) {
            throw new IllegalStateException(
                "No value provided for placeholder: '"
                    + placeholderValue
                    + "', expected one of "
                    + placeholders.keySet());
          }
          try {
            RenderResult result = placeholderValue.renderAndResolve(out, /* isLast= */ false);
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
    public RenderResult doRender(LoggingAdvisingAppendable out) throws IOException {
      if (!resolvedCases) {
        // plural/select messages always start with a sequence of plural and select values.
        // Additionally, we are guaranteed (by contract with the gencode) that the plural/select
        // variables are resolved.  So we need to do that now.
        // NOTE: that in the most common case, this loop only executes once and at maximum it will
        // loop 3 times.  We do know statically what the first iteration will be, but it is not
        // possible to know anything beyond that.
        ImmutableList<SoyMsgPart> parts = this.msgParts;
        while (!parts.isEmpty()) {
          SoyMsgPart first = parts.get(0);
          if (first instanceof SoyMsgSelectPart) {
            SoyMsgSelectPart selectPart = (SoyMsgSelectPart) first;
            String selectCase = getSelectCase(selectPart.getSelectVarName());
            parts = selectPart.lookupCase(selectCase);
          } else if (first instanceof SoyMsgPluralPart) {
            SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) first;
            double pluralValue = getPlural(pluralPart.getPluralVarName());
            parts = pluralPart.lookupCase(pluralValue, locale);
            // precalculate and store the remainder.
            remainder = pluralValue - pluralPart.getOffset();
          } else {
            break;
          }
        }
        // now that the final case has been selected, stash those parts in our parent so it can run
        // a simple non-recursive loop.
        this.msgParts = parts;
        resolvedCases = true;
      }
      // render the cases.
      return super.doRender(out);
    }

    @Override
    double getPluralRemainder() {
      return remainder;
    }

    /** Returns the select case variable value. */
    private String getSelectCase(String selectVarName) {
      String selectCase = (String) placeholders.get(selectVarName);
      if (selectCase == null) {
        throw new IllegalArgumentException(
            "No value provided for select: '"
                + selectVarName
                + "', expected one of "
                + placeholders.keySet());
      }
      return selectCase;
    }

    /** Returns the plural case variable value. */
    private double getPlural(String pluralVarName) {
      NumberData pluralValue = (NumberData) placeholders.get(pluralVarName);
      if (pluralValue == null) {
        throw new IllegalArgumentException(
            "No value provided for plural: '"
                + pluralVarName
                + "', expected one of "
                + placeholders.keySet());
      }
      return pluralValue.numberValue();
    }
  }

  private static final LoggingAdvisingAppendable LOGGER =
      new AbstractLoggingAdvisingAppendable() {
        @Override
        public final boolean softLimitReached() {
          return false;
        }

        @Override
        protected final void doAppend(char c) throws IOException {
          System.out.append(c);
        }

        @Override
        protected final void doAppend(CharSequence csq, int start, int end) throws IOException {
          System.out.append(csq, start, end);
        }

        @Override
        protected final void doAppend(CharSequence csq) throws IOException {
          System.out.append(csq);
        }

        @Override
        protected final void doEnterLoggableElement(LogStatement statement) {}

        @Override
        protected final void doExitLoggableElement() {}

        @Override
        protected void doAppendLoggingFunctionInvocation(
            LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
            throws IOException {
          String val = funCall.placeholderValue();
          for (Function<String, String> directive : escapers) {
            val = directive.apply(val);
          }
          System.out.append(val);
        }
      };

  /** Determines if the operand's string form can be equality-compared with a string. */
  public static boolean compareNullableString(@Nullable String string, SoyValue other) {
    // This is a parallel version of SharedRuntime.compareString except it can handle a null LHS.

    // This follows similarly to the Javascript specification, to ensure similar operation
    // over Javascript and Java: http://www.ecma-international.org/ecma-262/5.1/#sec-11.9.3
    if (other instanceof StringData || other instanceof SanitizedContent) {
      return Objects.equals(string, other.toString());
    }
    if (other instanceof NumberData) {
      if (string == null) {
        return false;
      }

      try {
        // Parse the string as a number.
        return Double.parseDouble(string) == other.numberValue();
      } catch (NumberFormatException nfe) {
        // Didn't parse as a number.
        return false;
      }
    }
    return false;
  }

  public static LoggingAdvisingAppendable logger() {
    return LOGGER;
  }

  public static int rangeLoopLength(int start, int end, int step) {
    int length = end - start;
    if ((length ^ step) < 0) {
      return 0;
    }
    return length / step + (length % step == 0 ? 0 : 1);
  }

  public static boolean coerceToBoolean(double v) {
    // NaN and 0 should both be falsy, all other numbers are truthy
    // use & instead of && to avoid a branch
    return v != 0.0 & !Double.isNaN(v);
  }

  public static String coerceToString(@Nullable SoyValue v) {
    return v == null ? "null" : v.coerceToString();
  }

  /** Wraps a compiled template to apply escaping directives. */
  private static final class EscapedCompiledTemplate implements CompiledTemplate {
    private final CompiledTemplate delegate;
    private final ImmutableList<SoyJavaPrintDirective> directives;
    private final ContentKind kind;

    // Note: render() may be called multiple times as part of a render operation that detaches
    // halfway through.  So we need to store the buffer in a field, but we never need to reset it.
    private final LoggingAdvisingAppendable buffer = LoggingAdvisingAppendable.buffering();

    EscapedCompiledTemplate(
        CompiledTemplate delegate, List<SoyJavaPrintDirective> directives, ContentKind kind) {
      this.delegate = checkNotNull(delegate);
      this.directives = ImmutableList.copyOf(directives);
      this.kind = checkNotNull(kind);
    }

    @Override
    public RenderResult render(LoggingAdvisingAppendable appendable, RenderContext context)
        throws IOException {
      RenderResult result = delegate.render(buffer, context);
      if (result.isDone()) {
        SoyValue resultData =
            kind == ContentKind.TEXT
                ? StringData.forValue(buffer.toString())
                : UnsafeSanitizedContentOrdainer.ordainAsSafe(buffer.toString(), kind);
        for (SoyJavaPrintDirective directive : directives) {
          resultData = directive.applyForJava(resultData, ImmutableList.of());
        }
        appendable.append(resultData.coerceToString());
      }
      return result;
    }

    @Override
    public ContentKind kind() {
      return kind;
    }
  }

  /**
   * Returns a {@link LoggingAdvisingAppendable} that:
   *
   * <ul>
   *   <li>Forwards all {@link LoggingAdvisingAppendable} methods to {@code delegate}
   *   <li>Implements {@link Closeable} and forwards all {@link Closeable#close} calls to the given
   *       closeables in order.
   * </ul>
   *
   * <p>This strategy allows us to make certain directives closeable without requiring them all to
   * be since this wrapper can propagate the close signals in the rare case that a closeable
   * directive is wrapped with a non closeable one (or multiple closeable wrappers are composed)
   */
  public static ClosePropagatingAppendable propagateClose(
      LoggingAdvisingAppendable delegate, ImmutableList<Closeable> closeables) {
    return new ClosePropagatingAppendable(delegate, closeables);
  }

  private static final class ClosePropagatingAppendable extends ForwardingLoggingAdvisingAppendable
      implements Closeable {
    final ImmutableList<Closeable> closeables;

    ClosePropagatingAppendable(
        LoggingAdvisingAppendable delegate, ImmutableList<Closeable> closeables) {
      super(delegate);
      this.closeables = closeables;
    }

    @Override
    public void close() throws IOException {
      // This looks buggy since we don't catch IOExceptions and then propagate close to the
      // remaining closeables, but that is fine given our usecase since all these closeables are
      // really wrapping each other and the close command is only to flush a buffer, so if any
      // throw we just abort anyway and if any of them throw it doesn't really matter if some data
      // is stuck in a buffer since the whole render is going to fail.
      for (Closeable c : closeables) {
        c.close();
      }
    }
  }

  public static LogStatement createLogStatement(boolean logOnly, SoyVisualElementData veData) {
    return LogStatement.create(veData.ve().id(), veData.data(), logOnly);
  }

  public static SanitizedContent flushLogsAndRender(
      SoyValueProvider valueProvider, SoyLogger logger) throws IOException {
    StringBuilder output = new StringBuilder();
    // Create our own OutputAppendable so we can use the current state of the SoyLogger, but render
    // to our own StringBuilder to return the rendered content.
    OutputAppendable appendable = OutputAppendable.create(output, logger);
    valueProvider.renderAndResolve(appendable, false);

    // The result is the same HTML that came in, except with logging statements removed. So it's
    // safe to ordain as HTML (with an assert just to make sure).
    checkState(appendable.getSanitizedContentKind() == ContentKind.HTML);
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        output.toString(), ContentKind.HTML, appendable.getSanitizedContentDirectionality());
  }

  /** Asserts that all members of the list are resolved. */
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

  public static SoyMap boxJavaMapAsSoyMap(Map<?, ?> javaMap) {
    Map<SoyValue, SoyValueProvider> map = Maps.newHashMapWithExpectedSize(javaMap.size());
    for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
      map.put(
          SoyValueConverter.INSTANCE.convert(entry.getKey()).resolve(),
          SoyValueConverter.INSTANCE.convert(entry.getValue()));
    }
    return SoyMapImpl.forProviderMap(map);
  }

  public static SoyRecord boxJavaMapAsSoyRecord(Map<String, ?> javaMap) {
    return new SoyRecordImpl(javaMapAsProviderMap(javaMap));
  }

  public static SoyLegacyObjectMap boxJavaMapAsSoyLegacyObjectMap(Map<String, ?> javaMap) {
    return new SoyLegacyObjectMapImpl(javaMapAsProviderMap(javaMap));
  }

  private static ImmutableMap<String, SoyValueProvider> javaMapAsProviderMap(
      Map<String, ?> javaMap) {
    return javaMap.entrySet().stream()
        .collect(
            toImmutableMap(e -> e.getKey(), e -> SoyValueConverter.INSTANCE.convert(e.getValue())));
  }

  /** For repeated extensions, returns all of the extensions values as a list. */
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

  public static CompiledTemplate.Factory bindTemplateParams(
      CompiledTemplate.Factory template, SoyRecord boundParams) {
    return new CompiledTemplate.Factory() {
      @Override
      public CompiledTemplate create(SoyRecord params, SoyRecord ij) {
        return template.create(SoyRecords.merge(boundParams, params), ij);
      }
    };
  }

  private JbcSrcRuntime() {}
}
