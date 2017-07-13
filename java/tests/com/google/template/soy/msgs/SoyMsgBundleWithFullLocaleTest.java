/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.msgs;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyMsgBundleWithFullLocaleTest {
  @Test
  public void testSoyMsgBundleWithFullLocale_compatibleLanguage() throws Exception {
    // Given a locale whose language is compatible with SoyMsgBundle.EMPTY.
    SoyMsgBundle bundle =
        SoyMsgBundleWithFullLocale.preservingLocaleIfAllowed(SoyMsgBundle.EMPTY, Locale.US);
    // Expect original locale in standard formatting.
    assertEquals("en-US", bundle.getLocaleString());
    // Even though the bundle is empty.
    assertEquals(SoyMsgBundle.EMPTY, ((SoyMsgBundleWithFullLocale) bundle).getInnerSoyMsgBundle());
  }

  @Test
  public void testSoyMsgBundleWithFullLocale_incompatibleLanguage() throws Exception {
    // Given a locale whose language is incompatible with SoyMsgBundle.EMPTY.
    SoyMsgBundle bundle =
        SoyMsgBundleWithFullLocale.preservingLocaleIfAllowed(SoyMsgBundle.EMPTY, Locale.FRANCE);
    // Expect SoyMsgBundle.EMPTY's locale.
    assertEquals("en", bundle.getLocaleString());
    // Even though the bundle is empty.
    assertEquals(SoyMsgBundle.EMPTY, bundle);
  }
}
