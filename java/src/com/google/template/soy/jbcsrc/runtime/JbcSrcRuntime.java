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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.common.io.BaseEncoding;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.AbstractLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyRecords;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyValueUnconverter;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.LazyProtoToSoyValueList;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.SoyLegacyObjectMapImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.SaveStateMetaFactory;
import com.google.template.soy.jbcsrc.shared.StackFrame;
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
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
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

  private static final class NullProvider implements SoyValueProvider {
    private final String nameForDebugging;

    NullProvider(String nameForDebugging) {
      this.nameForDebugging = nameForDebugging;
    }

    @Override
    public RenderResult status() {
      return RenderResult.done();
    }

    @Nullable
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
      return nameForDebugging;
    }
  }

  /** Represents a provider for the value {@code null} in jbcsrc. */
  public static final SoyValueProvider NULL_PROVIDER = new NullProvider("NULL_PROVIDER");

  @Nonnull
  public static AssertionError unexpectedStateError(StackFrame frame) {
    return new AssertionError("Unexpected state requested: " + frame.stateNumber);
  }

  @Nonnull
  public static NoSuchMethodException noExternJavaImpl() {
    return new NoSuchMethodException("No Java implementation for extern.");
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

  @Nonnull
  public static SoyValueProvider convertObjectToSoyValueProvider(Object o) {
    return SoyValueConverter.INSTANCE.convert(o);
  }

  @Nonnull
  public static SoyValueProvider convertFutureToSoyValueProvider(Future<?> future) {
    return SoyValueConverter.INSTANCE.convert(future);
  }

  /** Helper function to translate NullData -> null when resolving a SoyValueProvider. */
  public static SoyValue resolveSoyValueProvider(SoyValueProvider provider) {
    SoyValue value = provider.resolve();
    return handleTofuNull(value);
  }

  @Nullable
  public static SoyValueProvider soyValueProviderOrNull(SoyValueProvider provider) {
    if (resolveSoyValueProvider(provider) == null) {
      return null;
    }
    return provider;
  }

  @Nullable
  private static SoyValue handleTofuNull(SoyValue value) {
    if (value instanceof NullData | value instanceof UndefinedData) {
      return null;
    }
    return value;
  }

  public static SoyValue getField(SoyRecord record, String field) {
    Preconditions.checkNotNull(record, "Attempted to access field '%s' of null", field);
    return handleTofuNull(record.getField(field));
  }

  public static boolean hasField(SoyRecord record, String field) {
    Preconditions.checkNotNull(record, "Attempted to access field '%s' of null", field);
    return record.hasField(field);
  }

  @Nonnull
  public static ParamStore setField(ParamStore store, String field, SoyValueProvider provider) {
    return store.setField(field, provider == null ? NullData.INSTANCE : provider);
  }

  /**
   * Helper function to make SoyRecord.getFieldProvider a non-nullable function by returning {@link
   * #NULL_PROVIDER} for missing fields.
   */
  @Nonnull
  public static SoyValueProvider getFieldProvider(
      SoyRecord record, String field, @Nullable SoyValue defaultValue) {
    checkNotNull(record, "Attempted to access field '%s' of null", field);
    return paramOrDefault(record.getFieldProvider(field), defaultValue);
  }

  @Nonnull
  public static SoyValueProvider getFieldProvider(SoyRecord record, String field) {
    return getFieldProvider(record, field, /* defaultValue= */ null);
  }

  /**
   * Interprets a passed parameter. Handling tofu null and reinterpreting null as MISSING_PARAMETER
   */
  @Nonnull
  public static SoyValueProvider param(SoyValueProvider provider) {
    return paramOrDefault(provider, null);
  }

  /**
   * Interprets a passed parameter with an optional default. Handling tofu null and reinterpreting
   * null as MISSING_PARAMETER
   */
  @Nonnull
  public static SoyValueProvider paramOrDefault(
      SoyValueProvider provider, @Nullable SoyValue defaultValue) {
    // TODO(lukes): ideally this would be the behavior of getFieldProvider, but Tofu relies on it
    // returning null to interpret it as 'undefined'. http://b/20537225 describes the issues in Tofu
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

  /** Returns true if the value is derived from a missing parameter */
  @Nullable
  public static SafeUrl unboxSafeUrl(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return ((SanitizedContent) soyValue).toSafeUrl();
  }

  @Nullable
  public static SafeUrlProto unboxSafeUrlProto(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return SafeUrls.toProto(((SanitizedContent) soyValue).toSafeUrl());
  }

  @Nullable
  public static SafeHtml unboxSafeHtml(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return ((SanitizedContent) soyValue).toSafeHtml();
  }

  @Nullable
  public static SafeHtmlProto unboxSafeHtmlProto(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return SafeHtmls.toProto(((SanitizedContent) soyValue).toSafeHtml());
  }

  @Nullable
  public static TrustedResourceUrl unboxTrustedResourceUrl(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return ((SanitizedContent) soyValue).toTrustedResourceUrl();
  }

  @Nullable
  public static TrustedResourceUrlProto unboxTrustedResourceUrlProto(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return TrustedResourceUrls.toProto(((SanitizedContent) soyValue).toTrustedResourceUrl());
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

  public static int longToInt(long value) {
    Preconditions.checkState(
        value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE,
        "Casting long to integer results in overflow: %s",
        value);
    return (int) value;
  }

  @Nullable
  public static ImmutableList<String> listUnboxStrings(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::coerceToString).collect(toImmutableList());
  }

  @Nullable
  public static ImmutableList<Long> listUnboxInts(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::longValue).collect(toImmutableList());
  }

  @Nullable
  public static ImmutableList<Double> listUnboxFloats(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::floatValue).collect(toImmutableList());
  }

  @Nullable
  public static ImmutableList<Double> listUnboxNumbers(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::numberValue).collect(toImmutableList());
  }

  @Nullable
  public static ImmutableList<Boolean> listUnboxBools(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::coerceToBoolean).collect(toImmutableList());
  }

  @Nullable
  public static ImmutableList<Message> listUnboxProtos(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(v -> ((SoyProtoValue) v).getProto()).collect(toImmutableList());
  }

  @Nullable
  public static <T extends ProtocolMessageEnum> ImmutableList<T> listUnboxEnums(
      List<SoyValue> values, Class<T> type) {
    if (values == null) {
      return null;
    }
    return values.stream()
        .map(v -> getEnumValue(type, (int) v.longValue()))
        .collect(toImmutableList());
  }

  public static Integer toBoxedInteger(SoyValue value) {
    if (value == null) {
      return null;
    }
    return value.integerValue();
  }

  public static Long toBoxedLong(SoyValue value) {
    if (value == null) {
      return null;
    }
    return value.longValue();
  }

  public static Double toBoxedDouble(SoyValue value) {
    if (value == null) {
      return null;
    } else if (value instanceof NumberData) {
      return value.numberValue();
    }
    // This is probably an error, in which case this call with throw an appropriate exception.
    return value.floatValue();
  }

  public static Float toBoxedFloat(SoyValue value) {
    if (value == null) {
      return null;
    } else if (value instanceof NumberData) {
      return (float) value.numberValue();
    }
    // This is probably an error, in which case this call with throw an appropriate exception.
    return (float) value.floatValue();
  }

  public static Boolean toBoxedBoolean(SoyValue value) {
    if (value == null) {
      return null;
    }
    return value.coerceToBoolean();
  }

  public static <T> T toEnum(SoyValue value, Class<T> clazz) {
    if (value == null) {
      return null;
    }
    return getEnumValue(clazz, value.integerValue());
  }

  static <T> T getEnumValue(Class<T> clazz, int enumValue) {
    try {
      Method forNumber = clazz.getMethod("forNumber", int.class);
      return clazz.cast(forNumber.invoke(null, enumValue));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static ImmutableMap<?, ?> unboxMap(SoyMap map, Class<?> keyType, Class<?> valueType) {
    if (map == null) {
      return null;
    }
    return map.entrySet().stream()
        .collect(
            toImmutableMap(
                e -> unboxMapItem(e.getKey(), keyType),
                e -> unboxMapItem(e.getValue().resolve(), valueType)));
  }

  public static Object unboxMapItem(SoyValue value, Class<?> type) {
    if (value == null) {
      return null;
    } else if (type == Long.class) {
      return value.longValue();
    } else if (type == String.class) {
      return value.coerceToString();
    } else if (type == Boolean.class) {
      return value.coerceToBoolean();
    } else if (type == Double.class) {
      return value.floatValue();
    } else if (Message.class.isAssignableFrom(type)) {
      return ((SoyProtoValue) value).getProto();
    } else if (ProtocolMessageEnum.class.isAssignableFrom(type)) {
      return getEnumValue(type, value.integerValue());
    } else {
      throw new IllegalArgumentException("unsupported type: " + type);
    }
  }

  @Nullable
  public static ImmutableMap<?, ?> unboxRecord(SoyRecord map) {
    if (map == null) {
      return null;
    }
    return map.recordAsMap().entrySet().stream()
        .collect(toImmutableMap(Entry::getKey, e -> SoyValueUnconverter.unconvert(e.getValue())));
  }

  @Nullable
  public static List<SoyValueProvider> listBoxValues(List<?> javaValues) {
    if (javaValues == null) {
      return null;
    }
    return javaValues.stream().map(SoyValueConverter.INSTANCE::convert).collect(toList());
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
    return new EscapedCompiledTemplate(delegate, directives);
  }

  public static SoyValue getSoyListItem(List<SoyValueProvider> list, long index) {
    return resolveSoyValueProvider(getSoyListItemProvider(list, index));
  }

  public static SoyValueProvider getSoyListItemProvider(List<SoyValueProvider> list, long index) {
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

  public static SoyValue getSoyMapItem(SoyMap soyMap, SoyValue key) {
    Preconditions.checkNotNull(soyMap, "Attempted to access map item '%s' of null", key);
    return soyMap.get(key);
  }

  @Nonnull
  public static SoyValueProvider getSoyMapItemProvider(SoyMap soyMap, SoyValue key) {
    Preconditions.checkNotNull(soyMap, "Attempted to access map item '%s' of null", key);
    if (key == null) {
      key = NullData.INSTANCE;
    }
    SoyValueProvider soyValueProvider = soyMap.getProvider(key);
    return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
  }

  public static SoyValue getSoyLegacyObjectMapItem(
      SoyLegacyObjectMap legacyObjectMap, SoyValue key) {
    Preconditions.checkNotNull(legacyObjectMap, "Attempted to access map item '%s' of null", key);
    return legacyObjectMap.getItem(key);
  }

  public static SoyValueProvider getSoyLegacyObjectMapItemProvider(
      SoyLegacyObjectMap legacyObjectMap, SoyValue key) {
    if (legacyObjectMap == null) {
      throw new NullPointerException("Attempted to access map item '" + key + "' of null");
    }
    SoyValueProvider soyValueProvider = legacyObjectMap.getItemProvider(key);
    return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
  }

  @Nonnull
  public static String handleBasicTranslation(List<SoyMsgPart> parts) {
    return ((SoyMsgRawTextPart) parts.get(0)).getRawText();
  }

  @Nonnull
  public static String handleBasicTranslationAndEscapeHtml(List<SoyMsgPart> parts) {
    return MsgRenderer.escapeHtml(handleBasicTranslation(parts));
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
      return this;
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
          SoyValueProvider placeholderValue = placeholders.get(placeholderName);
          if (placeholderValue == null) {
            throw new IllegalStateException(
                "No value provided for placeholder: '"
                    + placeholderName
                    + "', expected one of "
                    + placeholders.keySet());
          }
          try {
            // TODO(lukes): we could set the isLast flag by scanning forward in msgParts for more
            // occurrences of this placeholder
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
      return super.doRender(out);
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
  public static boolean compareNullableString(@Nullable String string, @Nullable SoyValue other) {
    // This is a parallel version of SharedRuntime.compareString except it can handle a null LHS.
    if (string == null && other == null) {
      return true;
    }
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

  @Nonnull
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

  public static boolean coerceToBoolean(@Nullable SoyValue v) {
    return v != null && v.coerceToBoolean();
  }

  public static boolean coerceToBoolean(@Nullable String v) {
    return v != null && !v.isEmpty();
  }

  @Nonnull
  public static String coerceToString(@Nullable SoyValue v) {
    return v == null ? "null" : v.coerceToString();
  }

  /** Wraps a compiled template to apply escaping directives. */
  @Immutable
  private static final class EscapedCompiledTemplate implements CompiledTemplate {
    private final CompiledTemplate delegate;
    // these directives are builtin escaping directives which are all pure
    // functions but not annotated.
    @SuppressWarnings("Immutable")
    private final ImmutableList<SoyJavaPrintDirective> directives;

    static class SaveRestoreState {
      static final MethodHandle saveStateMethodHandle;
      static final MethodHandle restoreAppendableHandle;

      static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType saveMethodType =
            methodType(void.class, RenderContext.class, BufferingAppendable.class);
        saveStateMethodHandle =
            SaveStateMetaFactory.bootstrapSaveState(lookup, "saveState", saveMethodType, 1)
                .getTarget();
        restoreAppendableHandle =
            SaveStateMetaFactory.bootstrapRestoreState(
                    lookup,
                    "restoreLocal",
                    methodType(BufferingAppendable.class, StackFrame.class),
                    saveMethodType,
                    0)
                .getTarget();
      }
    }

    EscapedCompiledTemplate(CompiledTemplate delegate, List<SoyJavaPrintDirective> directives) {
      this.delegate = checkNotNull(delegate);
      this.directives = ImmutableList.copyOf(directives);
    }

    @Override
    public RenderResult render(
        SoyRecord params, SoyRecord ij, LoggingAdvisingAppendable appendable, RenderContext context)
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
                (BufferingAppendable) SaveRestoreState.restoreAppendableHandle.invokeExact(frame);
          } catch (Throwable t) {
            throw new AssertionError(t);
          }
          break;
        default:
          throw unexpectedStateError(frame);
      }
      RenderResult result = delegate.render(params, ij, buffer, context);
      if (result.isDone()) {
        SoyValue resultData = buffer.getAsSoyValue();
        for (SoyJavaPrintDirective directive : directives) {
          resultData = directive.applyForJava(resultData, ImmutableList.of());
        }
        appendable.append(resultData.coerceToString());
      } else {
        try {
          SaveRestoreState.saveStateMethodHandle.invokeExact(context, buffer);
        } catch (Throwable t) {
          throw new AssertionError(t);
        }
      }
      return result;
    }
  }

  public static LogStatement createLogStatement(boolean logOnly, SoyVisualElementData veData) {
    return LogStatement.create(veData.ve().id(), veData.data(), logOnly);
  }

  /** Asserts that all members of the list are resolved. */
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
            toImmutableMap(
                Map.Entry::getKey, e -> SoyValueConverter.INSTANCE.convert(e.getValue())));
  }

  /** For repeated extensions, returns all of the extensions values as a list. */
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
  public static TemplateValue bindTemplateParams(TemplateValue template, SoyRecord boundParams) {
    var newTemplate =
        new PartiallyBoundTemplate(boundParams, (CompiledTemplate) template.getCompiledTemplate());
    return TemplateValue.createWithBoundParameters(
        template.getTemplateName(), newTemplate.boundParams, newTemplate);
  }

  @Immutable
  private static final class PartiallyBoundTemplate implements CompiledTemplate {
    @SuppressWarnings("Immutable") // this is never mutated
    private final SoyRecord boundParams;

    private final CompiledTemplate delegate;

    PartiallyBoundTemplate(SoyRecord boundParams, CompiledTemplate delegate) {
      // unwrap delegation by eagerly merging params, this removes layers of indirection at call
      // time
      if (delegate instanceof PartiallyBoundTemplate) {
        PartiallyBoundTemplate partiallyBoundTemplate = (PartiallyBoundTemplate) delegate;
        boundParams = SoyRecords.merge(partiallyBoundTemplate.boundParams, boundParams);
        delegate = partiallyBoundTemplate.delegate;
      }
      this.delegate = delegate;
      this.boundParams = boundParams;
    }

    @Override
    public RenderResult render(
        SoyRecord params, SoyRecord ij, LoggingAdvisingAppendable appendable, RenderContext context)
        throws IOException {
      return delegate.render(SoyRecords.merge(boundParams, params), ij, appendable, context);
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

  @Nonnull
  public static <T> T checkExpressionNotNull(T value, String expression) {
    if (value == null) {
      throw new NullPointerException("'" + expression + "' evaluates to null");
    }
    return value;
  }

  @Nonnull
  public static String base64Encode(ByteString byteString) {
    return BaseEncoding.base64().encode(byteString.toByteArray());
  }

  @Nonnull
  public static ByteString base64Decode(String base64) {
    return ByteString.copyFrom(BaseEncoding.base64().decode(base64));
  }

  private JbcSrcRuntime() {}
}
