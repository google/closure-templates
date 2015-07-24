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

package com.google.template.soy.html;


/**
 * The possible states that HTML content can be in. These are based on contextautoesc's states
 * states, found in {@link com.google.template.soy.parsepasses.contextautoesc.Context.State}.
 * @see <a href="http://www.w3.org/TR/html5/syntax.html#tokenization">w3 tokenization spec</a>
 */
public enum HtmlState {
  /** Within a tag - the next valid thing is either an attribute or the end of the tag. */
  TAG(true),
  
  /** Within a tag name. */
  TAG_NAME(false),
  
  /** Within an attribute name. */
  ATTRIBUTE_NAME(true),
 
  /** Just before an attribute value, i.e. just after the equals sign after an attribute name. */
  BEFORE_ATTRIBUTE_VALUE(true),
  
  /** Within an attribute value. */
  ATTR_VALUE(true),
  
  /** Within a html element's children - either another element or a text node could appear here. */
  PCDATA(false);
  
  /** If the state is one where an attribute might be found or within an attribute's declaration. */
  private final boolean isAttributeState;
  
  HtmlState(boolean isAttributeState) {
    this.isAttributeState = isAttributeState;
  }

  public boolean isAttributeState() {
    return isAttributeState;
  }
}
