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

import com.google.template.soy.data.SoyValueProvider;
import java.util.Map;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyDict in terms of a map. Do not use directly; instead, use {@link
 * SoyValueConverter#convert}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
public final class DictImpl extends AbstractDict {

  /**
   * Creates a SoyDict implementation for a particular underlying provider map.
   *
   * <p>The map may be mutable, but will not be mutated by the DictImpl.
   */
  public static DictImpl forProviderMap(Map<String, ? extends SoyValueProvider> providerMap) {
    return new DictImpl(providerMap);
  }

  private DictImpl(Map<String, ? extends SoyValueProvider> providerMap) {
    super(providerMap);
  }
}
