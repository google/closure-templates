/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.javasrc.codedeps.SoyUtils;

import junit.framework.TestCase;
import junit.framework.AssertionFailedError;

public class EscapingModeTest extends TestCase {
  public final void testDirectiveNames() {
    assertEquals("|escapeHtml", EscapingMode.ESCAPE_HTML.directiveName);
    assertEquals("|escapeJsValue", EscapingMode.ESCAPE_JS_VALUE.directiveName);
    assertEquals("|noAutoescape", EscapingMode.NO_AUTOESCAPE.directiveName);
    assertEquals("|normalizeUri", EscapingMode.NORMALIZE_URI.directiveName);
  }

  private static final Set<String> BLANK_ESCAPES = ImmutableSet.of("", "\"\"", "''");
  private static final Set<String> INNOCUOUS_ESCAPES = ImmutableSet.of("z", "\"z\"", "'z'");

  public final void testEscapingModesExist() throws Throwable {
    for (EscapingMode mode : EscapingMode.values()) {
      assertTrue("Bad directive " + mode.directiveName, mode.directiveName.startsWith("|"));
      if (mode == EscapingMode.NO_AUTOESCAPE || mode == EscapingMode.TEXT) {
        continue;
      }
      // Some sanity checks.
      String blankEscape = escape(mode, "");
      assertTrue(blankEscape + " for " + mode, BLANK_ESCAPES.contains(blankEscape));
      String innocuousEscape = escape(mode, "z");
      assertTrue(innocuousEscape + " for " + mode, INNOCUOUS_ESCAPES.contains(innocuousEscape));
    }
  }

  public final void testHtmlEmbeddable() throws Throwable {
    for (EscapingMode mode : EscapingMode.values()) {
      if (mode == EscapingMode.NO_AUTOESCAPE || mode == EscapingMode.TEXT) {
        continue;
      }

      String quotedEscapes = escape(mode, "'\"");
      if (mode.isHtmlEmbeddable) {
        assertFalse(mode + " output contains \'", quotedEscapes.contains("'"));
      }
      assertFalse(mode + " output contains \"", quotedEscapes.contains("\""));

      // Make sure that quotes are not added around innocuous strings.
      String innocuousEscape = escape(mode, "z");
      if (mode.isHtmlEmbeddable) {
        assertFalse(mode + " output contains \'", innocuousEscape.contains("'"));
      }
      assertFalse(mode + " output contains \"", innocuousEscape.contains("\""));

      if (mode.isHtmlEmbeddable) {
        // Make sure that ampersands are properly escaped.
        // Any mode that advertises itself as HTML embeddable should not pass & though unchanged
        // since that is an HTML special character.
        // The string "?foo=bar&amp=baz" is interpreted by browsers as the plain text
        // "?foo=bar&=baz" since browsers insert semicolons after otherwise well-formed HTML
        // entities.
        String urlQueryEscape = escape(mode, "?foo=bar&amp=baz");
        assertFalse(mode + " output contains unescaped &amp", urlQueryEscape.contains("&amp="));
      }
    }
  }

  private static String escape(EscapingMode mode, String s) throws Throwable {
    Method method = getSoyUtilsMethodForEscapingMode(mode);
    try {
      return (String) method.invoke(null, SoyData.createFromExistingData(s));
      // TODO: make sure this is consistent with the JavaScript version so we don't break if
      // someone uses a string escaping scheme that uses double quotes for escape{Css,Js}Value.
    } catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    }
  }

  private static final EnumMap<EscapingMode, Method> SOY_UTILS_METHODS =
      new EnumMap<EscapingMode, Method>(EscapingMode.class);

  private static Method getSoyUtilsMethodForEscapingMode(EscapingMode mode) {
    Method method = SOY_UTILS_METHODS.get(mode);

    String methodName = "$$" + mode.directiveName.substring(1);
    try {
      method = SoyUtils.class.getDeclaredMethod(methodName, SoyData.class);
    } catch (NoSuchMethodException exStr) {
      throw new AssertionFailedError("No handler in SoyUtils for " + mode + " : " + methodName);
    }
    assertEquals("return type of " + methodName, String.class, method.getReturnType());
    assertTrue(methodName + " not static", Modifier.isStatic(method.getModifiers()));
    assertTrue(methodName + " not public", Modifier.isPublic(method.getModifiers()));

    SOY_UTILS_METHODS.put(mode, method);

    return method;
  }
}
