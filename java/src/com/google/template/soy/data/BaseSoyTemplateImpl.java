/*
 * Copyright 2019 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.ForOverride;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.SoyLegacyObjectMapImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The abstract superclass for generated per-template parameters classes. Each public template will
 * have a corresponding generated subtype of this class. Do not extend outside of the Soy compiler.
 *
 * <p>This class name purposefully does not end with "Params" so that it cannot collide with the
 * names of generated subclasses.
 */
public abstract class BaseSoyTemplateImpl implements SoyTemplate {

  private final ImmutableMap<String, SoyValueProvider> data;

  protected BaseSoyTemplateImpl(ImmutableMap<String, SoyValueProvider> data) {
    this.data = data;
  }

  @Override
  public final ImmutableMap<String, ?> getParamsAsMap() {
    return data;
  }

  /**
   * Returns the parameters as a map. Values are not wrapped with SoyValueProvider. This method is
   * intended to be called only by test code.
   */
  public final ImmutableMap<String, Object> getRawParamsAsMap() {
    // This is the only place where SoyValueUnconverter escapes this package.
    return data.entrySet().stream()
        .collect(
            toImmutableMap(Map.Entry::getKey, e -> SoyValueUnconverter.unconvert(e.getValue())));
  }

  @Override
  @SuppressWarnings("EqualsGetClass") // All subclasses are final.
  public boolean equals(Object o) {
    return o != null
        && getClass().equals(o.getClass())
        && data.equals(((BaseSoyTemplateImpl) o).data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getClass(), data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("name", getTemplateName())
        .add("data", getParamsAsMap())
        .toString();
  }

  /**
   * The abstract superclass for generated per-template parameter builders. Each public template
   * will have a corresponding generated subtype of this class. Do not extend outside of Soy
   * compiler.
   *
   * <p>Instances of this abstract class are not thread safe.
   */
  public abstract static class AbstractBuilder<
          B extends AbstractBuilder<?, T>, T extends SoyTemplate>
      implements Builder<T> {
    // Use IdentityHashMaps instead of HashMaps since:
    //  1. They use less memory internally
    //  2. We have an appropriate key object that we know will be a singleton
    //  3. They tend to be faster.
    //  One downside is that they have less efficient entrySet() implementations, but we can
    // easily workaround that.
    private final IdentityHashMap<SoyTemplateParam<?>, SoyValueProvider> data;

    protected AbstractBuilder(int numParams) {
      this.data = new IdentityHashMap<>(/* expectedMaxSize= */ numParams);
    }

    @Override
    public final T build() {
      ImmutableMap<String, SoyValueProvider> finalData =
          buildDataMapWithChecks(/* checkRequired= */ true);
      return buildInternal(finalData);
    }

    final T buildPartialForTests() {
      ImmutableMap<String, SoyValueProvider> finalData =
          buildDataMapWithChecks(/* checkRequired= */ false);
      return buildInternal(finalData);
    }

    @ForOverride
    protected abstract ImmutableSet<SoyTemplateParam<?>> allParams();

    @ForOverride
    protected abstract T buildInternal(ImmutableMap<String, SoyValueProvider> data);

    /**
     * Sets an arbitrary parameter to an arbitrary value.
     *
     * @throws NullPointerException if {@code name} is null
     * @throws SoyDataException if {@code value} is not convertable to a {@link SoyValueProvider}
     */
    @SuppressWarnings("unchecked")
    protected final B setParamInternal(SoyTemplateParam<?> name, SoyValueProvider soyValue) {
      checkNotNull(name);
      checkNotNull(soyValue);
      data.put(name, soyValue);
      return (B) this;
    }

    @Override
    public final <V> B setParam(SoyTemplateParam<? super V> param, V value) {
      // TODO(lukes): allParams uses .equals, perhaps we should use == so people don't use one
      // templates param that happens to have the same name/type in a different template.
      // Or maybe we should add some kind of 'cast' method to adapt one builder to another?
      if (!allParams().contains(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParamInternal(param, SoyValueConverter.INSTANCE.convert(value));
    }

    @Override
    public final <V> B setParamFuture(
        SoyTemplateParam<? super V> param, ListenableFuture<V> value) {
      if (!allParams().contains(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParamInternal(param, SoyValueConverter.INSTANCE.convert(value));
    }

    @Override
    public final boolean hasParam(SoyTemplateParam<?> param) {
      return allParams().contains(param);
    }

    //
    // The following are protected utility methods used by generated code in order to make the
    // generated code more succinct and less error prone.
    //

    /** Converts any Iterable to a Collection. Used by ListJavaType. */
    protected static <T> SoyList asList(
        Iterable<T> iterable, Function<? super T, ? extends SoyValueProvider> mapper) {
      return ListImpl.forProviderList(stream(iterable).map(mapper).collect(toImmutableList()));
    }

    protected static <T> SoyValue asNullableList(
        @Nullable Iterable<T> iterable, Function<? super T, ? extends SoyValueProvider> mapper) {
      return iterable == null ? NullData.INSTANCE : asList(iterable, mapper);
    }

    protected static SoyProtoValue asProto(Message proto) {
      return SoyProtoValue.create(proto);
    }

    protected static SoyValue asNullableProto(@Nullable Message proto) {
      return proto == null ? NullData.INSTANCE : SoyProtoValue.create(proto);
    }

    protected static BooleanData asBool(boolean b) {
      return b ? BooleanData.TRUE : BooleanData.FALSE;
    }

    protected static SoyValue asNullableBool(@Nullable Boolean b) {
      return b == null ? NullData.INSTANCE : asBool(b);
    }

    protected static StringData asString(String s) {
      return StringData.forValue(s);
    }

    protected static SoyValue asNullableString(@Nullable String s) {
      return s == null ? NullData.INSTANCE : asString(s);
    }

    /**
     * Used in code generated for Soy record types. The parameters are interleaved key-value pairs.
     */
    protected static SoyRecord asRecord(
        String firstKey, SoyValueProvider firstValue, Object... more) {
      checkArgument((more.length % 2) == 0);
      ImmutableMap.Builder<String, SoyValueProvider> map =
          ImmutableMap.<String, SoyValueProvider>builderWithExpectedSize(1 + more.length / 2)
              .put(firstKey, firstValue);
      for (int i = 0; i < more.length; i += 2) {
        map.put((String) more[i], (SoyValueProvider) more[i + 1]);
      }
      return new SoyRecordImpl(map.build());
    }

    /**
     * Converts a {@code Number} into a number type supported by Soy.
     *
     * @throws NullPointerException if n is null.
     */
    protected static NumberData asNumber(Number n) {
      return n instanceof Float || n instanceof Double
          ? FloatData.forValue(n.doubleValue())
          : IntegerData.forValue(n.longValue());
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected static SoyValue asNullableNumber(@Nullable Number n) {
      return n == null ? NullData.INSTANCE : asNumber(n);
    }

    protected static IntegerData asInt(long n) {
      return IntegerData.forValue(n);
    }

    protected static IntegerData asBoxedInt(Number n) {
      return asInt(n.longValue());
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected static SoyValue asNullableInt(@Nullable Number n) {
      return n == null ? NullData.INSTANCE : asInt(n.longValue());
    }

    protected static FloatData asFloat(double n) {
      return FloatData.forValue(n);
    }

    protected static FloatData asBoxedFloat(Number n) {
      return asFloat(n.doubleValue());
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected static SoyValue asNullableFloat(@Nullable Number n) {
      return n == null ? NullData.INSTANCE : asFloat(n.doubleValue());
    }

    protected static IntegerData asProtoEnum(ProtocolMessageEnum protoEnum) {
      return IntegerData.forValue(protoEnum.getNumber());
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected static SoyValue asNullableProtoEnum(@Nullable ProtocolMessageEnum protoEnum) {
      return protoEnum == null ? NullData.INSTANCE : asProtoEnum(protoEnum);
    }

    @SuppressWarnings("unchecked")
    protected static <T> SoyFutureValueProvider asFuture(
        Future<? extends T> future, Function<? super T, SoyValueProvider> mapper) {
      return new SoyFutureValueProvider(future, (Function) mapper);
    }

    protected static SanitizedContent asHtml(SafeHtml html) {
      return SanitizedContents.fromSafeHtml(html);
    }

    protected static SoyValue asNullableHtml(@Nullable SafeHtml html) {
      return html == null ? NullData.INSTANCE : asHtml(html);
    }

    protected static SanitizedContent asUri(SafeUrl url) {
      return SanitizedContents.fromSafeUrl(url);
    }

    protected static SoyValue asNullableUri(@Nullable SafeUrl url) {
      return url == null ? NullData.INSTANCE : asUri(url);
    }

    protected static SanitizedContent asJs(SafeScript script) {
      return SanitizedContents.fromSafeScript(script);
    }

    protected static SoyValue asNullableJs(@Nullable SafeScript script) {
      return script == null ? NullData.INSTANCE : asJs(script);
    }

    protected static SanitizedContent asTrustedResourceUri(TrustedResourceUrl url) {
      return SanitizedContents.fromTrustedResourceUrl(url);
    }

    protected static SoyValue asNullableTrustedResourceUri(@Nullable TrustedResourceUrl url) {
      return url == null ? NullData.INSTANCE : asTrustedResourceUri(url);
    }

    /**
     * Validates that {@code content} is of type ATTRIBUTES.
     *
     * @throws NullPointerException if content is null.
     */
    protected static SanitizedContent asAttributes(SanitizedContent content) {
      checkArgument(
          content.getContentKind() == SanitizedContent.ContentKind.ATTRIBUTES,
          "expected %s but got %s",
          SanitizedContent.ContentKind.ATTRIBUTES,
          content.getContentKind());
      return content;
    }

    /** Validates that {@code content} is of type ATTRIBUTES. */
    protected static SoyValue asNullableAttributes(@Nullable SanitizedContent content) {
      return content == null ? NullData.INSTANCE : asAttributes(content);
    }

    /**
     * Converts CssParam into a value that Soy can use.
     *
     * @throws NullPointerException if css is null.
     */
    protected static SanitizedContent asCss(CssParam css) {
      switch (css.type()) {
        case SAFE_STYLE:
          return SanitizedContents.fromSafeStyle(css.safeStyle());
        case SAFE_STYLE_SHEET:
          return SanitizedContents.fromSafeStyleSheet(css.safeStyleSheet());
      }
      throw new AssertionError();
    }

    /** Converts CssParam into a value that Soy can use. */
    protected static SoyValue asNullableCss(@Nullable CssParam css) {
      return css == null ? NullData.INSTANCE : asCss(css);
    }

    protected static <K, V> SoyMap asMap(
        Map<K, V> map,
        Function<? super K, ? extends SoyValue> keyMapper,
        Function<? super V, ? extends SoyValueProvider> valueMapper) {
      ImmutableMap.Builder<SoyValue, SoyValueProvider> builder =
          ImmutableMap.builderWithExpectedSize(map.size());
      map.forEach((k, v) -> builder.put(keyMapper.apply(k), valueMapper.apply(v)));
      return SoyMapImpl.forProviderMap(builder.build());
    }

    protected static <K, V> SoyValue asNullableMap(
        @Nullable Map<K, V> map,
        Function<? super K, ? extends SoyValue> keyMapper,
        Function<? super V, ? extends SoyValueProvider> valueMapper) {
      return map == null ? NullData.INSTANCE : asMap(map, keyMapper, valueMapper);
    }

    protected static <V> SoyLegacyObjectMap asLegacyObjectMap(
        Map<?, V> map, Function<? super V, ? extends SoyValueProvider> valueMapper) {
      ImmutableMap.Builder<String, SoyValueProvider> builder =
          ImmutableMap.builderWithExpectedSize(map.size());
      for (Map.Entry<?, V> entry : map.entrySet()) {
        // coerce key to a string, legacy object maps always coerce keys to strings.
        builder.put(entry.getKey().toString(), valueMapper.apply(entry.getValue()));
      }
      return new SoyLegacyObjectMapImpl(builder.build());
    }

    protected static <K, V> SoyValue asNullableLegacyObjectMap(
        @Nullable Map<?, V> map, Function<? super V, ? extends SoyValueProvider> valueMapper) {
      return map == null ? NullData.INSTANCE : asLegacyObjectMap(map, valueMapper);
    }

    protected static SoyValueProvider asSoyValue(@Nullable Object object) {
      return SoyValueConverter.INSTANCE.convert(object);
    }

    @ForOverride
    void prepareDataForBuild() {}

    /**
     * @param checkRequired Whether or not to enforce that all required parameters are set.
     * @return the fully built parameter map
     */
    private ImmutableMap<String, SoyValueProvider> buildDataMapWithChecks(boolean checkRequired) {
      // checkRequired=false could be used in the future for "build partial"
      prepareDataForBuild();
      ImmutableMap.Builder<String, SoyValueProvider> finalDataBuilder =
          ImmutableMap.<String, SoyValueProvider>builderWithExpectedSize(data.size());
      // Use forEach instead of looping over the entry set to avoid allocating entrySet+entry
      // objects
      data.forEach((k, v) -> finalDataBuilder.put(k.getName(), v));
      ImmutableMap<String, SoyValueProvider> finalData = finalDataBuilder.build();

      if (checkRequired) {
        List<String> missingParams = getMissingParamNames(finalData);
        if (!missingParams.isEmpty()) {
          throw new IllegalStateException(
              "Missing required params: " + Joiner.on(", ").join(missingParams));
        }
      }
      return finalData;
    }

    private List<String> getMissingParamNames(Map<String, ?> data) {
      List<String> missing = ImmutableList.of();
      ImmutableList<SoyTemplateParam<?>> params = allParams().asList();
      for (int i = 0; i < params.size(); i++) {
        SoyTemplateParam<?> param = params.get(i);
        if (param.isRequiredAndNotIndirect() && !data.containsKey(param.getName())) {
          if (missing.isEmpty()) {
            missing = new ArrayList<>();
          }
          missing.add(param.getName());
        }
      }
      return missing;
    }
  }

  /**
   * An {@link AbstractBuilder} that supports accumulator parameters.
   *
   * <p>Instances of this abstract class are not thread safe.
   *
   * @param <B> The type of the concrete Builder subclass
   * @param <T> The type of the concrete SoyTemplate class
   */
  public abstract static class AbstractBuilderWithAccumulatorParameters<
          B extends AbstractBuilderWithAccumulatorParameters<?, T>, T extends SoyTemplate>
      extends AbstractBuilder<B, T> {
    private final Map<SoyTemplateParam<?>, List<SoyValueProvider>> accummulatorData =
        new IdentityHashMap<>();

    protected AbstractBuilderWithAccumulatorParameters(int numParams) {
      super(numParams);
    }

    @Override
    void prepareDataForBuild() {
      accummulatorData.forEach((k, v) -> setParamInternal(k, ListImpl.forProviderList(v)));
    }

    /**
     * Adds an arbitrary value to a list valued named parameter.
     *
     * @throws NullPointerException if {@code name} is null
     * @throws SoyDataException if {@code value} is not convertable to a {@link SoyValueProvider}
     */
    @SuppressWarnings("unchecked")
    protected final B addToListParam(SoyTemplateParam<?> name, SoyValueProvider soyValue) {
      checkNotNull(name);
      // for required parameters the list will be eagerly initialized via initListParam, for others
      // we need to check when adding
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>()).add(soyValue);
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    protected final B initListParam(SoyTemplateParam<?> name) {
      checkNotNull(name);
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>());
      return (B) this;
    }
  }
}
