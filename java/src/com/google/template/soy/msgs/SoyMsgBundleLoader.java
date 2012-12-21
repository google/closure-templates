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

package com.google.template.soy.msgs;

import java.util.Locale;

/**
 * Contract for any object that can load a SoyMsgBundle given a locale.
 */
public interface SoyMsgBundleLoader {

  /**
   * Gets the Message bundle for a particular locale.
   *
   * @return The bundle. The Soy API expects SoyMsgBundle.EMPTY if there are
   *     no translations and the in-template messages should be used.
   */
  public SoyMsgBundle getSoyMsgBundleForLocale(Locale locale);
}
