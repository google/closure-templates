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
import com.google.template.soy.data.SoyValueProvider;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyEasyList. Do not use directly. Instead, use
 * SoyValueConverter.newEasyList*().
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
@Deprecated
public final class EasyListImpl extends ListBackedList implements SoyEasyList {

  /** Whether this instance is still mutable (immutability cannot be undone, of course). */
  private boolean isMutable;

  /** Important: Do not use outside of Soy code (treat as superpackage-private). */
  public EasyListImpl() {
    super(Lists.<SoyValueProvider>newArrayList());
    this.isMutable = true;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyEasyList.

  /** Returns a concretely-genericized list that supports adding entries. */
  @SuppressWarnings("unchecked") // Cast is consistent with constructor.
  private List<SoyValueProvider> getMutableList() {
    return (List<SoyValueProvider>) providerList;
  }

  @Override
  public void add(SoyValueProvider valueProvider) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyList.");
    getMutableList().add(checkNotNull(valueProvider));
  }
}
