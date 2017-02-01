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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import javax.annotation.ParametersAreNonnullByDefault;

/** TODO(user): Convert all uses to SoyValueConverter, then delete. */
@ParametersAreNonnullByDefault
@Singleton
@Deprecated
public final class SoyValueHelper extends SoyValueConverter {

  /** Static instance of this class that does not include any custom value converters. */
  public static final SoyValueHelper UNCUSTOMIZED_INSTANCE = new SoyValueHelper();

  /** An immutable empty dict. */
  public static final SoyDict EMPTY_DICT =
      DictImpl.forProviderMap(ImmutableMap.<String, SoyValueProvider>of());

  /** An immutable empty list. */
  public static final SoyList EMPTY_LIST =
      ListImpl.forProviderList(ImmutableList.<SoyValueProvider>of());

  @Inject
  public SoyValueHelper() {
    super();
  }
}
