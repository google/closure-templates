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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * An invocation of a Soy template, encapsulating both the template name and all the data parameters
 * passed to the template.
 *
 */
public abstract class SoyTemplate extends TemplateInterface {

  /**
   * The superclass of all generated builders.
   *
   * @param <T> the type of template that this builder builds.
   */
  public abstract static class Builder<T extends SoyTemplate> {

    /**
     * Builds and returns an immutable `SoyTemplate` instance from the state of this builder.
     *
     * @throws IllegalStateException if any required, non-indirect parameter is unset.
     */
    public abstract T build();

    /**
     * Builds and returns a `SoyTemplate` that is partially filled. This can be passed into another
     * template that can fill in the rest of the values, but cannot be used directly in an
     * invocation.
     */
    public abstract PartialSoyTemplate buildPartial();

    /**
     * Sets any template parameter of this builder. SoyTemplateParam ensures type safety.
     *
     * @throws IllegalArgumentException if the template corresponding to this builder does not have
     *     a parameter equal to {@code param}.
     */
    @CanIgnoreReturnValue
    public abstract <V> Builder<T> setParam(SoyTemplateParam<? super V> param, V value);

    /**
     * Sets any template parameter of this builder to a future value. SoyTemplateParam ensures type
     * safety.
     *
     * @throws IllegalArgumentException if the template corresponding to this builder does not have
     *     a parameter equal to {@code param}.
     */
    @CanIgnoreReturnValue
    public abstract <V> Builder<T> setParamFuture(
        SoyTemplateParam<? super V> param, ListenableFuture<V> value);

    /**
     * Sets any template parameter of this builder to an unchecked value. SoyTemplateParam is used
     * purely to indicate the parameter name. The internal implementation should ensure correct
     * conversion of the {@code value} object to an appropriate {@link SoyValueProvider}.
     *
     * @throws IllegalArgumentException if the template corresponding to this builder does not have
     *     a parameter equal to {@code param}.
     */
    @CanIgnoreReturnValue
    public abstract <V> Builder<T> setParamUnchecked(SoyTemplateParam<?> param, Object value);

    /**
     * Returns whether this builder has a param equal to {@code param}. If this method returns true
     * then {@link #setParam} should not throw an {@link IllegalArgumentException}.
     */
    public abstract boolean hasParam(SoyTemplateParam<?> param);
  }

  /**
   * Wraps a {@link SoyTemplate} but grants synchronous access to {@link #getTemplateName()}. This
   * method should only be called by generated implementations of TemplateParameters.
   */
  public static final class AsyncWrapper<T extends SoyTemplate> {

    private final String templateName;
    private final ListenableFuture<T> templateFuture;

    public AsyncWrapper(String templateName, ListenableFuture<T> templateFuture) {
      this.templateName = templateName;
      this.templateFuture = templateFuture;
    }

    public String getTemplateName() {
      return templateName;
    }

    public ListenableFuture<T> getTemplateFuture() {
      return templateFuture;
    }
  }
}
