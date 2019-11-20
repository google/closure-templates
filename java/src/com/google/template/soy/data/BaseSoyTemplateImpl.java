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

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.ForOverride;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

  private final String name;
  private final ImmutableMap<String, SoyValueProvider> data;

  protected BaseSoyTemplateImpl(String name, Map<String, SoyValueProvider> data) {
    this.name = name;
    this.data = ImmutableMap.copyOf(data);
  }

  @Override
  public String getTemplateName() {
    return name;
  }

  @Override
  public Map<String, ?> getParamsAsMap() {
    return data;
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
    private final String templateName;
    private final ImmutableMap<String, SoyTemplateParam<?>> params;
    private final SoyValueConverter soyValueConverter;
    private final Map<String, SoyValueProvider> data;
    private final Map<String, List<SoyValueProvider>> accummulatorData;

    protected AbstractBuilder(String templateName, Iterable<SoyTemplateParam<?>> params) {
      this.templateName = templateName;
      this.params =
          Streams.stream(params)
              .collect(
                  ImmutableMap.toImmutableMap(SoyTemplateParam::getName, Functions.identity()));
      this.soyValueConverter = SoyValueConverter.INSTANCE;
      this.data = new HashMap<>();
      this.accummulatorData = new HashMap<>();
    }

    @Override
    public T build() {
      ImmutableMap<String, SoyValueProvider> finalData = buildDataMapWithChecks(true, false);
      return buildInternal(templateName, finalData);
    }

    T buildPartialForTests() {
      ImmutableMap<String, SoyValueProvider> finalData = buildDataMapWithChecks(false, false);
      return buildInternal(templateName, finalData);
    }

    @ForOverride
    protected abstract T buildInternal(String name, ImmutableMap<String, SoyValueProvider> data);

    /**
     * Sets an arbitrary parameter to an arbitrary value.
     *
     * @throws NullPointerException if {@code name} is null
     * @throws SoyDataException if {@code value} is not convertable to a {@link SoyValueProvider}
     */
    @SuppressWarnings("unchecked")
    protected B setParam(String name, Object value) {
      Preconditions.checkNotNull(name);
      SoyValueProvider soyValue = soyValueConverter.convert(value);
      data.put(name, soyValue);
      return (B) this;
    }

    @Override
    public <V> B setParam(SoyTemplateParam<? super V> param, V value) {
      if (!params.containsValue(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParam(param.getName(), value);
    }

    @Override
    public <V> B setParamFuture(SoyTemplateParam<? super V> param, ListenableFuture<V> value) {
      if (!params.containsValue(param)) {
        throw new IllegalArgumentException(
            "No param in " + this.getClass().getName() + " like " + param);
      }
      return setParam(param.getName(), value);
    }

    //
    // The following are protected utility methods used by generated code in order to make the
    // generated code more succinct and less error prone.
    //

    /**
     * Adds an arbitrary value to a list valued named parameter.
     *
     * @throws NullPointerException if {@code name} is null
     * @throws SoyDataException if {@code value} is not convertable to a {@link SoyValueProvider}
     */
    @SuppressWarnings("unchecked")
    protected B addToListParam(String name, Object value) {
      Preconditions.checkNotNull(name);
      SoyValueProvider soyValue = soyValueConverter.convert(value);
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>()).add(soyValue);
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    protected B initListParam(String name) {
      Preconditions.checkNotNull(name);
      accummulatorData.computeIfAbsent(name, s -> new ArrayList<>());
      return (B) this;
    }

    /** Converts any Iterable to a Collection. Used by ListJavaType. */
    protected <I> Collection<I> asCollection(Iterable<I> iterable) {
      return iterable instanceof Collection
          ? (Collection<I>) iterable
          : ImmutableList.copyOf(iterable);
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains a type of Number supported
     * by Soy.
     */
    protected Collection<Number> asNumberCollection(Iterable<? extends Number> iterable) {
      return Streams.stream(iterable).map(this::asNumber).collect(toImmutableList());
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains Doubles. Used by
     * ListJavaType.
     */
    protected List<Double> asListOfDoubles(Iterable<? extends Number> value) {
      return Streams.stream(value).map(Number::doubleValue).collect(toImmutableList());
    }

    /**
     * Makes sure that a {@code List<? extends Number>} actually contains Longs. Used by
     * ListJavaType.
     */
    protected List<Long> asListOfLongs(Iterable<? extends Number> value) {
      return Streams.stream(value).map(Number::longValue).collect(toImmutableList());
    }

    /**
     * Used in code generated for Soy record types. The parameters are interleaved key-value pairs.
     */
    protected Map<String, Object> asRecord(String firstKey, Object firstValue, Object... more) {
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
    protected Number asNumber(Number n) {
      return n instanceof Float || n instanceof Double ? n.doubleValue() : n.longValue();
    }

    /** Converts a {@code Number} into a number type supported by Soy. */
    protected Number asNullableNumber(@Nullable Number n) {
      return n == null ? null : asNumber(n);
    }

    /**
     * Validates that {@code content} is of type ATTRIBUTES.
     *
     * @throws NullPointerException if content is null.
     */
    protected SanitizedContent asAttributes(SanitizedContent content) {
      Preconditions.checkArgument(
          content.getContentKind() == SanitizedContent.ContentKind.ATTRIBUTES);
      return content;
    }

    /** Validates that {@code content} is of type ATTRIBUTES. */
    protected SanitizedContent asNullableAttributes(@Nullable SanitizedContent content) {
      return content == null ? null : asAttributes(content);
    }

    /**
     * Converts CssParam into a value that Soy can use.
     *
     * @throws NullPointerException if css is null.
     */
    protected Object asCss(CssParam css) {
      return css.toSoyValue();
    }

    /** Converts CssParam into a value that Soy can use. */
    protected Object asNullableCss(@Nullable CssParam css) {
      return css == null ? null : asCss(css);
    }

    protected Function<Object, Object> longMapper = t -> ((Number) t).longValue();
    protected Function<Object, Object> doubleMapper = t -> ((Number) t).doubleValue();
    protected Function<Object, Object> numberMapper = t -> asNumber((Number) t);

    /**
     * Makes sure that a Map with key or value type {@code <? extends Number>} has keys and/or
     * values that are actually Long/Double. This is necessary because Soy doesn't support any
     * implementation of Number.
     *
     * @see #longMapper
     * @see #doubleMapper
     */
    protected Map<?, ?> asMapOfNumbers(
        Map<?, ?> map,
        @Nullable Function<Object, Object> keyMapper,
        @Nullable Function<Object, Object> valueMapper) {
      Function<Object, Object> key = keyMapper != null ? keyMapper : l -> l;
      Function<Object, Object> value = valueMapper != null ? valueMapper : l -> l;
      return map.entrySet().stream()
          .collect(
              ImmutableMap.toImmutableMap(
                  e -> key.apply(e.getKey()), e -> value.apply(e.getValue())));
    }

    private ImmutableMap<String, SoyValueProvider> buildDataMapWithChecks(
        boolean checkRequired, boolean checkNoExtras) {
      // checkNoExtras=true only needed in the future if we add a public setter that takes an
      // arbitrary String param name.
      // checkRequired=false could be used in the future for "build partial"
      ImmutableMap.Builder<String, SoyValueProvider> finalDataBuilder =
          ImmutableMap.<String, SoyValueProvider>builder().putAll(data);
      for (Map.Entry<String, List<SoyValueProvider>> entry : accummulatorData.entrySet()) {
        finalDataBuilder.put(entry.getKey(), soyValueConverter.convert(entry.getValue()));
      }
      ImmutableMap<String, SoyValueProvider> finalData = finalDataBuilder.build();

      if (checkRequired) {
        Set<String> missingParams = getMissingParamNames(finalData);
        if (!missingParams.isEmpty()) {
          throw new IllegalStateException(
              "Missing required params: " + Joiner.on(", ").join(missingParams));
        }
      }
      if (checkNoExtras) {
        Set<String> extraParams = getExtraParamNames(finalData);
        if (!extraParams.isEmpty()) {
          throw new IllegalStateException("Illegal params: " + Joiner.on(", ").join(extraParams));
        }
      }
      return finalData;
    }

    private Set<String> getMissingParamNames(Map<String, ?> data) {
      Set<String> missing = new HashSet<>();
      for (SoyTemplateParam<?> param : params.values()) {
        if (param.isRequired() && !data.containsKey(param.getName())) {
          missing.add(param.getName());
        }
      }
      return missing;
    }

    private Set<String> getExtraParamNames(Map<String, ?> data) {
      Set<String> extra = new HashSet<>();
      for (String name : data.keySet()) {
        if (!params.containsKey(name)) {
          extra.add(name);
        }
      }
      return extra;
    }
  }
}
