/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.basetree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * An object that can hold extra state for tree copying operations, passed to {@link
 * Node#copy(CopyState)}.
 *
 * <p>The Soy AST is mostly a tree, but there are some back edges. Since the copying process is
 * recursive and can be started from any node, there is no guarantee whether the back edges will be
 * copied (this means that copying only a subtree may leave you with back edges pointing at the old
 * tree, which is weird).
 */
public final class CopyState {
  /**
   * A simple listener api that can be used to listen to changes to objects via the {@link
   * #updateRefs} api.
   */
  public interface Listener<T> {
    void newVersion(T newObject);
  }

  /** Contains either the new values or a listener that is waiting for the new value. */
  private final IdentityHashMap<Object, Object> mappings = new IdentityHashMap<>();

  /**
   * Registers that the old object has been remapped to the new object.
   *
   * <p>This is useful for auxiliary AST datastructures which may contain back-edges in the AST.
   * When being copied, the auxiliary data structure is registered with this method then AST nodes
   * which have references to the old copy can register via {@link #registerRefListener} so that
   * they can get a reference to the new copy as well.
   */
  public <T> void updateRefs(T oldObject, T newObject) {
    checkNotNull(oldObject);
    checkNotNull(newObject);
    checkArgument(!(newObject instanceof Listener));
    Object previousMapping = mappings.put(oldObject, newObject);
    if (previousMapping != null) {
      if (previousMapping instanceof Listener) {
        @SuppressWarnings("unchecked") // Listener<T> can only be registered with a T
        Listener<T> listener = (Listener<T>) previousMapping;
        listener.newVersion(newObject);
      } else {
        throw new IllegalStateException("found multiple remappings for " + oldObject);
      }
    }
  }

  /**
   * Registers a listener to be invoked if the given object is ever updated via the {@link
   * #updateRefs} method.
   *
   * <p>Note: the listener may be executed inline or not at all.
   */
  public <T> void registerRefListener(T oldObject, final Listener<T> listener) {
    checkNotNull(oldObject);
    checkNotNull(listener);
    Object oldMapping = mappings.get(oldObject);
    if (oldMapping == null) {
      // store the listener so it will be run when the old object mapping is registered
      mappings.put(oldObject, listener);
    } else if (oldMapping instanceof Listener) {
      // chain the listeners
      @SuppressWarnings("unchecked") // Listener<T> can only be registered with a T
      final Listener<T> oldListener = (Listener) oldMapping;
      mappings.put(oldObject, chainListeners(listener, oldListener));
    } else {
      // if there was a value mapped to the old object it must have been a T
      @SuppressWarnings("unchecked")
      T typedValue = (T) oldMapping;
      listener.newVersion(typedValue);
    }
  }

  /**
   * Asserts that there are no pending listeners.
   *
   * <p>This can be useful in a test environment to ensure that the copy worked correctly. N.B. it
   * is possible for a copy to be 'correct' and for not all listeners to fire, this is common when
   * copying small parts of the AST (anything below a template).
   */
  public void checkAllListenersFired() {
    for (Map.Entry<Object, Object> entry : mappings.entrySet()) {
      if (entry.getValue() instanceof Listener) {
        throw new IllegalStateException(
            "Listener for " + entry.getKey() + " never fired: " + entry.getValue());
      }
    }
  }

  private static <T> Listener<T> chainListeners(
      final Listener<T> listener, final Listener<T> oldListener) {
    return new Listener<T>() {
      @Override
      public void newVersion(T newObject) {
        listener.newVersion(newObject);
        oldListener.newVersion(newObject);
      }
    };
  }
}
