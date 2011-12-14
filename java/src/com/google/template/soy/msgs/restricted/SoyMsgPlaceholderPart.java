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

package com.google.template.soy.msgs.restricted;


/**
 * Represents a placeholder within a message.
 *
 */
public class SoyMsgPlaceholderPart extends SoyMsgPart {


  /** The placeholder name (as seen by translators). */
  private final String placeholderName;


  /**
   * @param placeholderName The placeholder name (as seen by translators).
   */
  public SoyMsgPlaceholderPart(String placeholderName) {
    this.placeholderName = placeholderName;
  }


  /** Returns the placeholder name (as seen by translators). */
  public String getPlaceholderName() {
    return placeholderName;
  }

}
