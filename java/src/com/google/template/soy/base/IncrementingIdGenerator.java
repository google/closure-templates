/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.base;


/**
 * A generator of incrementing unique integer ids, starting from 0.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class IncrementingIdGenerator implements IdGenerator {

  /** The current next id value to generate. */
  private int currId;

  public IncrementingIdGenerator() {
    currId = 0;
  }

  protected IncrementingIdGenerator(IncrementingIdGenerator orig) {
    this.currId = orig.currId;
  }

  @Override public int genId() {
    return currId++;
  }

  @Override public IncrementingIdGenerator clone() {
    IncrementingIdGenerator clone = new IncrementingIdGenerator();
    clone.currId = this.currId;
    return clone;
  }
}
