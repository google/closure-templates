/*
 * Copyright 2021 Google Inc.
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

/**
 * Represents a detached stack frame.
 *
 * <p>For trivial stack frames (no state is saved/restored) this class will be used directly, for
 * non-trivial ones a subclass will be generated on the fly by {@link SaveStateMetaFactory}.
 */
public class StackFrame {

  /**
   * The initial state for every detachable method.
   *
   * <p>This frame is linked to itself to simplify state restoration logic.
   */
  static final StackFrame INIT;

  static {
    INIT = new StackFrame(0);
    INIT.child = INIT;
  }

  /** The logical position in the suspended frame. A non-negative number. */
  public final int stateNumber;
  /** The child frame in the stack. */
  StackFrame child;

  public StackFrame(int stateNumber) {
    this.stateNumber = stateNumber;
  }
}
