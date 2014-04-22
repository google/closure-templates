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

import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Abstract implementation of SoyRecord that caches previously retrieved field value providers.
 *
 * This class does not cache field values (i.e. does not cache the resolve() operation on value
 * providers, which is often a noop). Each value provider is expected to implement caching of its
 * own resolve() operation if desired.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that extend this class.
 *
 */
@ParametersAreNonnullByDefault
public abstract class SoyAbstractCachingRecord extends SoyAbstractRecord {


  /** Map of previously retrieved field providers. */
  private final Map<String, SoyValueProvider> cachedFieldProviders = Maps.newHashMap();


  @Override public final SoyValueProvider getFieldProvider(String name) {

    if (cachedFieldProviders.containsKey(name)) {
      return cachedFieldProviders.get(name);
    } else {
      SoyValueProvider result = getFieldProviderInternal(name);
      cachedFieldProviders.put(name, result);
      return result;
    }
  }


  /**
   * Gets a provider of a field value of this SoyRecord.
   * @param name The field name to get.
   * @return A provider of the field value for the given field name, or null if no such field name.
   */
  public abstract SoyValueProvider getFieldProviderInternal(String name);

}
