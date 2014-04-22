/*
 * Copyright 2013 Google Inc.
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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Abstract implementation of SoyValue.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that extend this class.
 *
 */
@ParametersAreNonnullByDefault
public abstract class SoyAbstractValue implements SoyValue {


  @Override @Nonnull public SoyValue resolve() {
    return this;
  }


  /**
   * Note: Even though we provide a default implementation for equals(SoyValueProvider), subclasses
   * must still implement equals(SoyValue).
   *
   * {@inheritDoc}
   */
  @Override public boolean equals(SoyValueProvider other) {
    if (other instanceof SoyValue) {
      return this.equals((SoyValue) other);
    } else {
      // Since 'other' is an unresolved SoyValueProvider, we let it decide how to handle equals(),
      // e.g. it may decide to resolve itself before doing the comparison.
      return other.equals(this);
    }
  }


  @Override public boolean booleanValue() {
    throw new SoyDataException(
        "Expecting boolean value but instead encountered type " + getClass().getSimpleName());
  }


  @Override public int integerValue() {
    throw new SoyDataException(
        "Expecting integer value but instead encountered type " + getClass().getSimpleName());
  }


  @Override public long longValue() {
    return integerValue();
  }


  @Override public double floatValue() {
    throw new SoyDataException(
        "Expecting float value but instead encountered type " + getClass().getSimpleName());
  }


  @Override public double numberValue() {
    throw new SoyDataException(
        "Expecting number value but instead encountered type " + getClass().getSimpleName());
  }


  @Override public String stringValue() {
    throw new SoyDataException(
        "Expecting string value but instead encountered type " + getClass().getSimpleName());
  }

}
