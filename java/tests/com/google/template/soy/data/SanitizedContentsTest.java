/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.data;

import com.google.template.soy.data.SanitizedContent.ContentKind;

import junit.framework.TestCase;


/**
 * Unit tests for SanitizedContents utility class.
 *
 * @author Garrett Boyer
 */
public class SanitizedContentsTest extends TestCase {

  private void assertResourceNameValid(boolean valid, String resourceName, ContentKind kind) {
    try {
      SanitizedContents.pretendValidateResource(resourceName, kind);
      assertTrue("No exception was thrown, but wasn't expected to be valid.", valid);
    } catch (IllegalArgumentException e) {
      assertFalse("Exception was thrown, but was expected to be valid.", valid);
    }
  }

  public void testPretendValidateResource() {
    // Correct resources.
    assertResourceNameValid(true, "test.js", ContentKind.JS);
    assertResourceNameValid(true, "/test/foo.bar.js", ContentKind.JS);
    assertResourceNameValid(true, "test.html", ContentKind.HTML);
    assertResourceNameValid(true, "test.css", ContentKind.CSS);

    // Wrong resource kind.
    assertResourceNameValid(false, "test.css", ContentKind.HTML);
    assertResourceNameValid(false, "test.html", ContentKind.JS);
    assertResourceNameValid(false, "test.js", ContentKind.CSS);

    // No file extensions supported for these kinds.
    assertResourceNameValid(false, "test.attributes", ContentKind.ATTRIBUTES);

    // Missing extension entirely.
    assertResourceNameValid(false, "test", ContentKind.JS);
  }

}
