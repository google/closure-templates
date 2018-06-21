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

import static com.google.common.truth.Truth.assertThat;

import com.ibm.icu.util.ULocale;
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
    assertThat(bundle.getLocaleString()).isEqualTo("en-US");
    // Even though the bundle is empty.
    assertThat(((SoyMsgBundleWithFullLocale) bundle).getInnerSoyMsgBundle())
        .isEqualTo(SoyMsgBundle.EMPTY);
  }

  @Test
  public void testSoyMsgBundleWithFullLocale_incompatibleLanguage() throws Exception {
    // Given a locale whose language is incompatible with SoyMsgBundle.EMPTY.
    SoyMsgBundle bundle =
        SoyMsgBundleWithFullLocale.preservingLocaleIfAllowed(SoyMsgBundle.EMPTY, Locale.FRANCE);
    // Expect SoyMsgBundle.EMPTY's locale.
    assertThat(bundle.getLocaleString()).isEqualTo("en");
    // Even though the bundle is empty.
    assertThat(bundle).isEqualTo(SoyMsgBundle.EMPTY);
  }

  @Test
  public void testSoyMsgBundleWithFullLocale_deprecatedLanguageCode() throws Exception {
    // Given a message bundle with a deprecated language code ("iw").
    SoyMsgBundle iwBundle =
        new SoyMsgBundleWithFullLocale(SoyMsgBundle.EMPTY, new ULocale("iw"), "iw");

    // Expect the original soy bundle and locale to be preserved.
    SoyMsgBundle bundle =
        SoyMsgBundleWithFullLocale.preservingLocaleIfAllowed(iwBundle, new Locale("iw"));
    assertThat(bundle.getLocaleString()).isEqualTo("iw");
    assertThat(bundle).isEqualTo(iwBundle);
  }
}
