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

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.Map;


/**
 * Scopes a single execution of a block of code.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * Apply this scope with a try/finally block:
 * <pre>
 *   scope.enter();
 *   try {
 *     // explicitly seed some seed objects
 *     scope.seed(SomeObject.class, someObject);
 *     // create and access scoped objects
 *     ...
 *   } finally {
 *     scope.exit();
 *   }
 * </pre>
 *
 * The scope can be initialized with one or more seed values by calling {@code seed(key, value)} or
 * {@code seed(class, value)} before the injector will be called upon to provide for this key.
 *
 * For each key seeded with seed(), you must include a corresponding binding:
 * <pre>
 *   bind(key)
 *       .toProvider(GuiceSimpleScope.&lt;KeyClass&gt;getUnscopedProvider())
 *       .in(ScopeAnnotation.class);
 * </pre>
 *
 * @author Jesse Wilson
 * @author Fedor Karpelevitch
 * @author Kai Huang
 */
public class GuiceSimpleScope implements Scope {


  /** Provider to use as the unscoped provider for scoped parameters. Always throws exception. */
  private static final Provider<Object> UNSCOPED_PROVIDER =
      new Provider<Object>() {
        @Override public Object get() {
          throw new IllegalStateException(
              "If you got here then it means that your code asked for scoped object which should" +
              " have been explicitly seeded in this scope by calling GuiceSimpleScope.seed()," +
              " but was not.");
        }
      };


  /**
   * Returns a provider that always throws exception complaining that the object
   * in question must be seeded before it can be injected.
   *
   * @return typed provider
   */
  @SuppressWarnings("unchecked")
  public static <T> Provider<T> getUnscopedProvider() {
    return (Provider<T>) UNSCOPED_PROVIDER;
  }


  /** The ThreadLocal holding all the values in scope. */
  private final ThreadLocal<Map<Key<?>, Object>> scopedValuesTl =
      new ThreadLocal<Map<Key<?>, Object>>();


  /**
   * Enters an occurrence of this scope.
   */
  public void enter() {
    checkState(!isActive(), "A scoping block is already in progress");
    scopedValuesTl.set(Maps.<Key<?>, Object>newHashMap());
  }


  /**
   * Exits the current occurrence of this scope.
   */
  public void exit() {
    checkState(isActive(), "No scoping block in progress");
    scopedValuesTl.remove();
  }


  /**
   * Whether we're currently in an occurrence of this scope.
   */
  public boolean isActive() {
    return scopedValuesTl.get() != null;
  }


  /**
   * Seeds a value in the current occurrence of this scope.
   * @param key The key to seed.
   * @param value The value for the key.
   */
  public <T> void seed(Key<T> key, T value) {

    Map<Key<?>, Object> scopedObjects = getScopedValues(key);
    checkState(
        !scopedObjects.containsKey(key),
        "A value for the key %s was already seeded in this scope. Old value: %s New value: %s",
        key, scopedObjects.get(key), value);
    scopedObjects.put(key, value);
  }


  /**
   * Seeds a value in the current occurrence of this scope.
   * @param class0 The class to seed.
   * @param value The value for the key.
   */
  public <T> void seed(Class<T> class0, T value) {
    seed(Key.get(class0), value);
  }


  /**
   * Gets a value in the current occurrence of this scope.
   * @param key The key to get.
   * @return The scoped value for the given key.
   */
  public <T> T getForTesting(Key<T> key) {

    Map<Key<?>, Object> scopedValues = getScopedValues(key);
    @SuppressWarnings("unchecked")
    T value = (T) scopedValues.get(key);
    if (value == null && !scopedValues.containsKey(key)) {
      throw new IllegalStateException("The key " + key + " has not been seeded in this scope");
    }
    return value;
  }


  /**
   * Gets a value in the current occurrence of this scope.
   * @param class0 The class to get.
   * @return The scoped value for the given class.
   */
  public <T> T getForTesting(Class<T> class0) {
    return getForTesting(Key.get(class0));
  }


  @Override public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscopedProvider) {

    return new Provider<T>() {
      @Override public T get() {
        Map<Key<?>, Object> scopedValues = getScopedValues(key);
        @SuppressWarnings("unchecked")
        T value = (T) scopedValues.get(key);
        if (value == null && !scopedValues.containsKey(key)) {
          value = unscopedProvider.get();
          scopedValues.put(key, value);
        }
        return value;
      }
    };
  }


  /**
   * Private helper to get the map of scoped values specific to the current thread.
   * @param key The key that is intended to be retrieved from the returned map.
   */
  private <T> Map<Key<?>, Object> getScopedValues(Key<T> key) {

    Map<Key<?>, Object> scopedValues = scopedValuesTl.get();
    if (scopedValues == null) {
      throw new OutOfScopeException("Cannot access " + key + " outside of a scoping block");
    }
    return scopedValues;
  }

}
