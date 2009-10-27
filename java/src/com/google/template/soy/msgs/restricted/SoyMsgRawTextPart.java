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
 * Represents a raw text string within a message (the stuff that translators change).
 *
 * @author Kai Huang
 */
public class SoyMsgRawTextPart extends SoyMsgPart {


  /** The raw text string. */
  private final String rawText;


  /**
   * @param rawText The raw text string.
   */
  public SoyMsgRawTextPart(String rawText) {
    this.rawText = rawText;
  }


  /** Returns the raw text string. */
  public String getRawText() {
    return rawText;
  }

}
