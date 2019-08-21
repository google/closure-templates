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

import com.google.auto.value.AutoValue;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.ForOverride;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The abstract superclass for generated per-template parameters classes. Each public template will
 * have a corresponding generated subtype of this class. Do not extend outside of the Soy compiler.
 *
 * <p>This class name purposefully does not end with "Params" so that it cannot collide with the
 * names of generated subclasses.
 */
public abstract class BaseParamsImpl implements TemplateParameters {

  private final String name;
  private final ImmutableMap<String, SoyValueProvider> data;

  protected BaseParamsImpl(String name, Map<String, SoyValueProvider> data) {
    this.name = name;
    this.data = ImmutableMap.copyOf(data);
  }

  @Override
  public String getTemplateName() {
    return name;
  }

  @Override
  public Map<String, SoyValueProvider> getParamsAsMap() {
    return data;
  }

  /**
   * The abstract superclass for generated per-template parameter builders. Each public template
   * will have a corresponding generated subtype of this class. Do not extend outside of Soy
   * compiler.
   *
   * <p>Instances of this abstract class are not thread safe.
   */
  public abstract static class AbstractBuilder<
      B extends AbstractBuilder<?, T>, T extends BaseParamsImpl> {
    private final String templateName;
    private final ImmutableMap<String, Param> params;
    private final SoyValueConverter soyValueConverter;
    private final Map<String, SoyValueProvider> data;

    protected AbstractBuilder(String templateName, Iterable<Param> params) {
      this.templateName = templateName;
      this.params =
          Streams.stream(params)
              .collect(ImmutableMap.toImmutableMap(Param::getName, Functions.identity()));
      this.soyValueConverter = SoyValueConverter.INSTANCE;
      this.data = new HashMap<>();
    }

    public T build() {
      ImmutableMap<String, SoyValueProvider> finalData = buildDataMapWithChecks(true, false);
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

    private ImmutableMap<String, SoyValueProvider> buildDataMapWithChecks(
        boolean checkRequired, boolean checkNoExtras) {
      // checkNoExtras=true only needed in the future if we add a public setter that takes an
      // arbitrary String param name.
      // checkRequired=false could be used in the future for "build partial"
      ImmutableMap<String, SoyValueProvider> finalData = ImmutableMap.copyOf(data);
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
      for (Param param : params.values()) {
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

  /**
   * An internal representation of a parameter to a Soy template. This should not be used outside of
   * {@link BaseParamsImpl}.
   */
  @AutoValue
  public abstract static class Param {
    /** Creates an optional param with the given name. */
    public static Param optional(String name) {
      return new AutoValue_BaseParamsImpl_Param(name, false);
    }

    /** Creates a required param with the given name. */
    public static Param required(String name) {
      return new AutoValue_BaseParamsImpl_Param(name, true);
    }

    abstract String getName();

    abstract boolean isRequired();
  }
}
