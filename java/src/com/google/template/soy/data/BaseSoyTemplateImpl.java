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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.data.internal.ListImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    final SoyValueConverter soyValueConverter;
    private final Map<SoyTemplateParam<?>, SoyValueProvider> data;

    protected AbstractBuilder() {
      this.soyValueConverter = SoyValueConverter.INSTANCE;
      // Use IdentityHashMaps instead of HashMaps since:
      //  1. They use less memory internally
      //  2. We have an appropriate key object that we know will be a singleton
      //  3. They tend to be faster.
      //  One downside is that they have less efficient entrySet() implementations, but we can
      // easily workaround that.
      // fairly strong contract with our subclass so it is ok.  We know that this method is just
      // returning a static final field.
      data = new IdentityHashMap<>(/* expectedMaxSize= */ allParams().size());
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
    protected final B setParamInternal(SoyTemplateParam<?> name, Object value) {
      Preconditions.checkNotNull(name);
      SoyValueProvider soyValue = soyValueConverter.convert(value);
      data.put(name, soyValue);
      return (B) this;
    }

    @Override
    public final <V> B setParam(SoyTemplateParam<? super V> param, V value) {
      if (!allParams().contains(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParamInternal(param, value);
    }

    @Override
    public final <V> B setParamFuture(
        SoyTemplateParam<? super V> param, ListenableFuture<V> value) {
      if (!allParams().contains(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParamInternal(param, value);
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
    protected final <I> Collection<I> asCollection(Iterable<I> iterable) {
      return iterable instanceof Collection
          ? (Collection<I>) iterable
          : ImmutableList.copyOf(iterable);
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains a type of Number supported
     * by Soy.
     */
    protected final ImmutableList<Number> asNumberCollection(Iterable<? extends Number> iterable) {
      return Streams.stream(iterable).map(this::asNumber).collect(toImmutableList());
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains Doubles. Used by
     * ListJavaType.
     */
    protected final ImmutableList<Double> asListOfDoubles(Iterable<? extends Number> value) {
      return Streams.stream(value).map(Number::doubleValue).collect(toImmutableList());
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains Longs. Used by
     * ListJavaType.
     */
    protected final ImmutableList<Long> asListOfLongs(Iterable<? extends Number> value) {
      return Streams.stream(value).map(Number::longValue).collect(toImmutableList());
    }

    /**
     * Used in code generated for Soy record types. The parameters are interleaved key-value pairs.
     */
    protected final ImmutableMap<String, Object> asRecord(
        String firstKey, Object firstValue, Object... more) {
      Preconditions.checkArgument((more.length % 2) == 0);
      // Uses soyValueConverter.convert to allow NULL values in the ImmutableMap.
      ImmutableMap.Builder<String, Object> map =
          ImmutableMap.<String, Object>builder()
              .put(firstKey, soyValueConverter.convert(firstValue));
      for (int i = 0; i < more.length; i += 2) {
        map.put((String) more[i], soyValueConverter.convert(more[i + 1]));
      }
      return map.build();
    }

    /**
     * Converts a {@code Number} into a number type supported by Soy.
     *
     * @throws NullPointerException if n is null.
     */
    protected final Number asNumber(Number n) {
      return n instanceof Float || n instanceof Double ? n.doubleValue() : n.longValue();
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected final Number asNullableNumber(@Nullable Number n) {
      return n == null ? null : asNumber(n);
    }

    /**
     * Validates that {@code content} is of type ATTRIBUTES.
     *
     * @throws NullPointerException if content is null.
     */
    protected final SanitizedContent asAttributes(SanitizedContent content) {
      Preconditions.checkArgument(
          content.getContentKind() == SanitizedContent.ContentKind.ATTRIBUTES);
      return content;
    }

    /** Validates that {@code content} is of type ATTRIBUTES. */
    protected final SanitizedContent asNullableAttributes(@Nullable SanitizedContent content) {
      return content == null ? null : asAttributes(content);
    }

    /**
     * Converts CssParam into a value that Soy can use.
     *
     * @throws NullPointerException if css is null.
     */
    protected final Object asCss(CssParam css) {
      return css.toSoyValue();
    }

    /** Converts CssParam into a value that Soy can use. */
    protected final Object asNullableCss(@Nullable CssParam css) {
      return css == null ? null : asCss(css);
    }

    protected final Function<Object, Object> longMapper = t -> ((Number) t).longValue();
    protected final Function<Object, Object> doubleMapper = t -> ((Number) t).doubleValue();
    protected final Function<Object, Object> numberMapper = t -> asNumber((Number) t);

    /**
     * Makes sure that a Map with key or value type {@code <? extends Number>} has keys and/or
     * values that are actually Long/Double. This is necessary because Soy doesn't support any
     * implementation of Number.
     *
     * @see #longMapper
     * @see #doubleMapper
     */
    protected final ImmutableMap<?, ?> asMapOfNumbers(
        Map<?, ?> map,
        @Nullable Function<Object, Object> keyMapper,
        @Nullable Function<Object, Object> valueMapper) {
      Function<Object, Object> key = keyMapper != null ? keyMapper : l -> l;
      Function<Object, Object> value = valueMapper != null ? valueMapper : l -> l;
      return map.entrySet().stream()
          .collect(toImmutableMap(e -> key.apply(e.getKey()), e -> value.apply(e.getValue())));
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
        Set<String> missingParams = getMissingParamNames(finalData);
        if (!missingParams.isEmpty()) {
          throw new IllegalStateException(
              "Missing required params: " + Joiner.on(", ").join(missingParams));
        }
      }
      return finalData;
    }

    private Set<String> getMissingParamNames(Map<String, ?> data) {
      Set<String> missing = ImmutableSet.of();
      for (SoyTemplateParam<?> param : allParams()) {
        if (param.isRequired() && !data.containsKey(param.getName())) {
          if (missing.isEmpty()) {
            missing = new HashSet<>();
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

    protected AbstractBuilderWithAccumulatorParameters() {}

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
    protected final B addToListParam(SoyTemplateParam<?> name, Object value) {
      Preconditions.checkNotNull(name);
      SoyValueProvider soyValue = soyValueConverter.convert(value);
      // for required parameters the list will be eagerly initialized via initListParam, for others
      // we need to check when adding
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>()).add(soyValue);
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    protected final B initListParam(SoyTemplateParam<?> name) {
      Preconditions.checkNotNull(name);
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>());
      return (B) this;
    }
  }
}
