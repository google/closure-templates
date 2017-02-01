/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.soyparse;

import static com.google.template.soy.soyparse.SoyFileParserTokenManager.lexStateNames;

import java.util.NoSuchElementException;

/**
 * A simple stack data structure for managing lexical states.
 *
 * <p>In the parser we need to enter/exit states recursively and an explicit stack is the best way
 * to go. We are using a custom datastructure to avoid boxing since this is called by the parser
 * very frequently.
 */
final class LexicalStateStack {
  // We could use a more space efficient encoding if necessary. For example, a bit set.
  // The universe of possible states is quite small <32.... but the depth of this stack should never
  // get bigger than ~30 so it probably doesn't matter.
  private int[] elements = new int[16];
  private int size = 0;

  LexicalStateStack() {}

  /** Pushes a new item onto the stack. */
  void push(int element) {
    if (element < 0 || element >= lexStateNames.length) {
      throw new IllegalArgumentException("Invalid lexical state: " + element);
    }
    // copy into locals to control reads and writes.
    int localSize = size;
    int[] localElements = elements;
    if (localSize + 1 == localElements.length) {
      localElements = doubleCapacity();
    }
    localElements[localSize] = element;
    size = localSize + 1;
  }

  /** Removes the current head of the stack. */
  int pop() {
    int localSize = size;
    if (localSize == 0) {
      throw new NoSuchElementException();
    }
    size = localSize = localSize - 1;
    return elements[localSize];
  }

  /** Removes all elements from the stack. */
  void clear() {
    size = 0;
  }

  /** Returns the current size of the stack. */
  int size() {
    return size;
  }

  /** Returns the state at the top of the stack or -1 if the stack is empty. */
  int peek() {
    int localSize = size;
    if (localSize == 0) {
      return -1;
    }
    return elements[localSize - 1];
  }

  /** Doubles the capacity of the stack and returns it. */
  private int[] doubleCapacity() {
    int oldCapacity = elements.length;
    int newCapacity = oldCapacity << 1;
    if (newCapacity < 0) {
      if (oldCapacity == Integer.MAX_VALUE) {
        throw new IllegalStateException("Sorry, stack too big");
      } else {
        newCapacity = Integer.MAX_VALUE;
      }
    }
    int[] newElements = new int[newCapacity];
    System.arraycopy(elements, 0, newElements, 0, oldCapacity);
    return elements = newElements;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < size; i++) {
      sb.append(SoyFileParserTokenManager.lexStateNames[elements[i]]);
      if (i < size - 1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }
}
