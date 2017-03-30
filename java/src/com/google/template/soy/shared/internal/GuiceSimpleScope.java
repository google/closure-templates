/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckReturnValue;

/**
 * Scopes a single execution of a block of code.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>Apply this scope with a try/finally block:
 *
 * <pre>
 *   scope.enter();
 *   try (GuiceSimpleScope.InScope inScope = scope.enter()) {
 *     // explicitly seed some seed objects
 *     inScope.seed(SomeObject.class, someObject);
 *     // create and access scoped objects
 *     ...
 *   }
 * </pre>
 *
 * The scope can be initialized with one or more seed values by calling {@code seed(key, value)} or
 * {@code seed(class, value)} before the injector will be called upon to provide for this key.
 *
 * <p>For each key seeded with seed(), you must include a corresponding binding:
 *
 * <pre>
 *   bind(key)
 *       .toProvider(GuiceSimpleScope.&lt;KeyClass&gt;getUnscopedProvider())
 *       .in(ScopeAnnotation.class);
 * </pre>
 *
 */
public final class GuiceSimpleScope implements Scope {
  /** Represents {@code null} in the scope map. */
  private static final Object NULL_SENTINEL = new Object();

  /**
   * An autoclosable object that can be used to seed and exit scopes.
   *
   * <p>Obtain an instance with {@link GuiceSimpleScope#enter()}.
   */
  public final class InScope implements AutoCloseable {
    private boolean isClosed;
    private final Thread openThread = Thread.currentThread();
    private final ArrayDeque<HashMap<Key<?>, Object>> deque;

    InScope(ArrayDeque<HashMap<Key<?>, Object>> deque) {
      this.deque = deque;
    }

    /**
     * Seeds a value in the current occurrence of this scope.
     *
     * @param key The key to seed.
     * @param value The value for the key.
     */
    public <T> void seed(Key<T> key, T value) {
      checkOpenAndOnCorrectThread();
      HashMap<Key<?>, Object> scopedObjects = deque.peek();
      Object prev = scopedObjects.put(key, value == null ? NULL_SENTINEL : value);
      if (prev != null) {
        throw new IllegalStateException(
            String.format(
                "A value for the key %s was already seeded in this scope. Old value: %s "
                    + "New value: %s",
                key, prev, value));
      }
    }

    /**
     * Seeds a value in the current occurrence of this scope.
     *
     * @param clazz The class to seed.
     * @param value The value for the key.
     */
    public <T> void seed(Class<T> clazz, T value) {
      seed(Key.get(clazz), value);
    }

    /** Exits the scope */
    @Override
    public void close() {
      checkOpenAndOnCorrectThread();
      isClosed = true;
      deque.pop();
    }

    private void checkOpenAndOnCorrectThread() {
      if (isClosed) {
        throw new IllegalStateException("called close() more than once!");
      }
      if (Thread.currentThread() != openThread) {
        throw new IllegalStateException("cannot move the scope to another thread");
      }
    }

  }

  /** Provider to use as the unscoped provider for scoped parameters. Always throws exception. */
  private static final Provider<Object> UNSCOPED_PROVIDER =
      new Provider<Object>() {
        @Override
        public Object get() {
          throw new IllegalStateException(
              "If you got here then it means that your code asked for scoped object which should"
                  + " have been explicitly seeded in this scope by calling GuiceSimpleScope.seed(),"
                  + " but was not.");
        }
      };

  /**
   * Returns a provider that always throws exception complaining that the object in question must be
   * seeded before it can be injected.
   *
   * @return typed provider
   */
  @SuppressWarnings("unchecked")
  public static <T> Provider<T> getUnscopedProvider() {
    return (Provider<T>) UNSCOPED_PROVIDER;
  }

  /** The ThreadLocal holding all the values in scope. */
  private final ThreadLocal<ArrayDeque<HashMap<Key<?>, Object>>> scopedValuesTl =
      new ThreadLocal<>();

  /** Enters an occurrence of this scope. */
  @CheckReturnValue
  public InScope enter() {
    ArrayDeque<HashMap<Key<?>, Object>> stack = scopedValuesTl.get();
    if (stack == null) {
      stack = new ArrayDeque<>();
      scopedValuesTl.set(stack);
    }
    stack.push(new HashMap<Key<?>, Object>());
    return new InScope(stack);
  }

  @Override
  public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscopedProvider) {

    return new Provider<T>() {
      @Override
      public T get() {
        ArrayDeque<HashMap<Key<?>, Object>> arrayDeque = scopedValuesTl.get();
        if (arrayDeque == null || arrayDeque.isEmpty()) {
          throw new OutOfScopeException("Cannot access " + key + " outside of a scoping block");
        }
        Map<Key<?>, Object> scopedValues = arrayDeque.peek();
        Object value = scopedValues.get(key);
        if (value == null) {
          value = unscopedProvider.get();
          scopedValues.put(key, value == null ? NULL_SENTINEL : value);
        }
        @SuppressWarnings("unchecked")
        T typedValue = value == NULL_SENTINEL ? null : (T) value;
        return typedValue;
      }
    };
  }
}
