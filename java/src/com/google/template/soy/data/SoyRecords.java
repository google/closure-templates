/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.data.internal.AugmentedParamStore;
import com.google.template.soy.data.internal.ParamStore;

/** Utility methods dealing with {@link SoyRecord}s. */
public final class SoyRecords {
  /** Merges two soy records into one. Throws an exception in the case of a key conflict. */
  public static SoyRecord merge(SoyRecord a, SoyRecord b) {
    ParamStore merged = new AugmentedParamStore(a, b.recordAsMap().size());
    for (String key : b.recordAsMap().keySet()) {
      checkArgument(!merged.hasField(key));
      merged.setField(key, b.getFieldProvider(key));
    }

    return merged;
  }

  /** Non-instantiable. */
  private SoyRecords() {}
}
