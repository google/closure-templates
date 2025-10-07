/*
 * Copyright 2025 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, softwarejava int
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jbcsrc.shared;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyValue;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

/** Context API */
public interface ContextStore {

  public void pushContext(ImmutableMap<String, SoyValue> childValues);

  public void popContext();

  @Nullable
  public SoyValue getContextValue(String key);

  /** One context node. */
  @AutoValue
  abstract static class ContextNode {
    abstract ImmutableMap<String, SoyValue> values();

    @Nullable
    abstract ContextNode parent();

    static ContextNode createRoot() {
      return new AutoValue_ContextStore_ContextNode(ImmutableMap.of(), null);
    }

    @Nullable
    SoyValue getContextValue(String key) {
      if (values().containsKey(key)) {
        return values().get(key);
      }
      return parent() == null ? null : parent().getContextValue(key);
    }

    ContextNode pushContext(ImmutableMap<String, SoyValue> childValues) {
      return new AutoValue_ContextStore_ContextNode(childValues, this);
    }

    ContextNode popContext() {
      if (parent() == null) {
        throw new NoSuchElementException("ContextNode underrun");
      }
      return parent();
    }
  }

  /** Test ContextStore */
  public static class TestContextStore implements ContextStore {
    private ContextNode currentContext = ContextNode.createRoot();

    public TestContextStore() {}

    @Override
    public void pushContext(ImmutableMap<String, SoyValue> childValues) {
      currentContext = currentContext.pushContext(childValues);
    }

    @Override
    public void popContext() {
      currentContext = currentContext.popContext();
    }

    @Override
    @Nullable
    public SoyValue getContextValue(String key) {
      return currentContext.getContextValue(key);
    }
  }
}
