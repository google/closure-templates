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

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.ibm.icu.util.ULocale;
import java.util.Iterator;
import java.util.Locale;

/**
 * Wraps SoyMsgBundle along with the original locale that was used to look it up. This helps
 * preserve fully-qualified locales for Guice-injection into `SoyJavaFunction`s via `@LocaleString`.
 * Fully-qualified locales with country code are required, for example, for accurate date, number,
 * and currency localization.
 */
public final class SoyMsgBundleWithFullLocale extends SoyMsgBundle {
  /**
   * Returns a soy message bundle that exposes `locale` as its locale IF `locale` corresponds to the
   * same language as `bundle.getLocale()`. Intuitively, the returned bundle retains the passed-in
   * locale if it's compatible with the passed-in translation bundle.
   *
   * <p>Examples
   *
   * <ul>
   *   <li>If our translation bundle includes "es-419" (Spanish Latin America) but not "es-MX"
   *       (Spanish Mexico) specifically, then the returned bundle would use the translations for
   *       "es-419", but still expose "es-MX" as its locale to soy plugins for accurate
   *       localization.
   *   <li>If, for a completely unsupported language, the passed-in bundle is the default one with
   *       "en" locale, then the returned bundle exposes "en" for localization.
   * </ul>
   */
  public static SoyMsgBundle preservingLocaleIfAllowed(SoyMsgBundle bundle, Locale locale) {
    ULocale ulocale = ULocale.forLocale(locale);
    return ulocale.getLanguage().equals(bundle.getLocale().getLanguage())
        ? new SoyMsgBundleWithFullLocale(bundle, ulocale, ulocale.toLanguageTag())
        : bundle;
  }

  private final SoyMsgBundle bundle;
  private final String localeString;
  private final ULocale locale;

  private SoyMsgBundleWithFullLocale(SoyMsgBundle bundle, ULocale locale, String localeString) {
    this.bundle = bundle;
    this.locale = locale;
    this.localeString = localeString;
  }

  @Override
  public String getLocaleString() {
    return localeString;
  }

  @Override
  public ULocale getLocale() {
    return locale;
  }

  @Override
  public SoyMsg getMsg(long msgId) {
    return bundle.getMsg(msgId);
  }

  @Override
  public int getNumMsgs() {
    return bundle.getNumMsgs();
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    return bundle.iterator();
  }

  @VisibleForTesting
  public SoyMsgBundle getInnerSoyMsgBundle() {
    return bundle;
  }
}
