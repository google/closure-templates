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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
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
    // When checking compatibility, normalize the locale using toLanguageTag() to convert deprecated
    // locale language codes ("iw") to new ones ("he"). Thus, for backward compatiblity, we thus
    // avoid wrapping the original bundle when its language code isn't preserved.
    ULocale ulocale = new ULocale(locale.toLanguageTag());
    return ulocale.getLanguage().equals(bundle.getLocale().getLanguage())
        ? new SoyMsgBundleWithFullLocale(bundle, ulocale, ulocale.toLanguageTag())
        : bundle;
  }

  private final SoyMsgBundle delegate;
  private final String localeString;
  private final ULocale locale;
  private final boolean isRtl;

  @VisibleForTesting
  SoyMsgBundleWithFullLocale(SoyMsgBundle delegate, ULocale locale, String localeString) {
    // unwrap the delegate
    while (delegate instanceof SoyMsgBundleWithFullLocale) {
      delegate = ((SoyMsgBundleWithFullLocale) delegate).delegate;
    }
    this.delegate = delegate;
    this.locale = locale;
    this.localeString = localeString;
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;
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
  public boolean isRtl() {
    return isRtl;
  }

  @Override
  public SoyMsg getMsg(long msgId) {
    return delegate.getMsg(msgId);
  }

  @Override
  public ImmutableList<SoyMsgPart> getMsgParts(long msgId) {
    return delegate.getMsgParts(msgId);
  }

  @Override
  public int getNumMsgs() {
    return delegate.getNumMsgs();
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    return delegate.iterator();
  }

  @VisibleForTesting
  public SoyMsgBundle getInnerSoyMsgBundle() {
    return delegate;
  }
}
