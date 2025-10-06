/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import com.google.template.soy.data.SoyValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.annotation.Nullable;

/** Context API */
public final class ContextStore {

  Deque<Map<String, SoyValue>> contextStack = new ArrayDeque<>();

  public ContextStore() {}

  public void pushContext(Map<String, SoyValue> context) {
    contextStack.addFirst(context);
  }

  public void popContext() {
    contextStack.removeFirst();
  }

  @Nullable
  public SoyValue getContextValue(String key) {
    for (Map<String, SoyValue> context : contextStack) {
      SoyValue value = context.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }
}
