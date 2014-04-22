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

package com.google.template.soy.data.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyEasyList;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.SoyValueProvider;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyEasyList. Do not use directly. Instead, use
 * SoyValueHelper.newEasyList*().
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
public final class EasyListImpl extends ListBackedList implements SoyEasyList {


  /** The instance of SoyValueHelper to use for internal conversions. */
  private final SoyValueHelper valueHelper;

  /** Whether this instance is still mutable (immutability cannot be undone, of course). */
  private boolean isMutable;


  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * @param valueHelper The instance of SoyValueHelper to use for internal conversions.
   */
  public EasyListImpl(SoyValueHelper valueHelper) {
    super(Lists.<SoyValueProvider>newArrayList());
    this.valueHelper = valueHelper;
    this.isMutable = true;
  }


  // -----------------------------------------------------------------------------------------------
  // SoyEasyList.


  /** Returns a concretely-genericized list that supports adding entries. */
  @SuppressWarnings("unchecked")  // Cast is consistent with constructor.
  private List<SoyValueProvider> getMutableList() {
    return (List<SoyValueProvider>) providerList;
  }


  @Override public void add(SoyValueProvider valueProvider) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyList.");
    getMutableList().add(checkNotNull(valueProvider));
  }


  @Override public void add(@Nullable Object value) {
    add(valueHelper.convert(value));
  }


  @Override public void add(int index, SoyValueProvider valueProvider) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyList.");
    getMutableList().add(index, checkNotNull(valueProvider));
  }


  @Override public void add(int index, @Nullable Object value) {
    add(index, valueHelper.convert(value));
  }


  @Override public void set(int index, SoyValueProvider valueProvider) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyList.");
    getMutableList().set(index, checkNotNull(valueProvider));
  }


  @Override public void set(int index, @Nullable Object value) {
    set(index, valueHelper.convert(value));
  }


  @Override public void del(int index) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyList.");
    providerList.remove(index);
  }


  @Override public void addAllFromList(SoyList list) {
    for (SoyValueProvider valueProvider : list.asJavaList()) {
      add(valueProvider);
    }
  }


  @Override public void addAllFromJavaIterable(Iterable<?> javaIterable) {
    for (Object value : javaIterable) {
      add(value);
    }
  }


  @Override public SoyEasyList makeImmutable() {
    this.isMutable = false;
    return this;
  }
}
