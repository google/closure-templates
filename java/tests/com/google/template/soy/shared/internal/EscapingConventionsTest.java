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

package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Make sure that the escapers preserve containment consistently in both Java and JavaScript.
 *
 * <p>
 */
@RunWith(JUnit4.class)
public class EscapingConventionsTest {
  @Rule public final TestName testName = new TestName();

  @Test
  public void testAllEscapersIterated() {
    // Make sure that all Escapers are present in getAllEscapers().
    Set<String> actual = Sets.newLinkedHashSet();
    Set<String> expected = Sets.newLinkedHashSet();
    for (EscapingConventions.CrossLanguageStringXform directive :
        EscapingConventions.getAllEscapers()) {
      expected.add(directive.getClass().getSimpleName());
    }
    for (Class<?> clazz : EscapingConventions.class.getClasses()) {
      if (EscapingConventions.CrossLanguageStringXform.class.isAssignableFrom(clazz)
          && !Modifier.isAbstract(clazz.getModifiers())) {
        actual.add(clazz.getSimpleName());
      }
    }
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testEscaperInterface() throws Exception {
    // Test the escape method.
    assertThat(EscapingConventions.EscapeUri.INSTANCE.escape("Hello")).isEqualTo("Hello");
    assertThat(EscapingConventions.EscapeUri.INSTANCE.escape("\nletters\u0085\u1234\u2028"))
        .isEqualTo("%0Aletters%C2%85%E1%88%B4%E2%80%A8");

    StringBuilder sb;

    // And the Appendable version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append("Hello")
        .append("\nletters\u0085\u1234\u2028");
    assertThat(sb.toString()).isEqualTo("Hello%0Aletters%C2%85%E1%88%B4%E2%80%A8");

    // And the Appendable substring version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append("--Hello--", 2, 7)
        .append("--\nletters\u0085\u1234\u2028--", 2, 13);
    assertThat(sb.toString()).isEqualTo("Hello%0Aletters%C2%85%E1%88%B4%E2%80%A8");

    // And the Appendable char version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append('H')
        .append('i')
        .append('\n')
        .append('\u0085')
        .append('\u1234');
    assertThat(sb.toString()).isEqualTo("Hi%0A%C2%85%E1%88%B4");
  }

  @Test
  public void testFilterTelUri() throws Exception {
    String[] shouldReject =
        new String[] {
          "",
          "tel:",
          "tel:%",
          "tel:+12345678901%,1234#",
          "tel:+12345678901,1234%#",
          "tel:+12345678901%01234%#",
          "tel:+12345678901%,1234%#",
          "tel:+12345678901%00,1234" + URLEncoder.encode("#", "UTF-8"),
          "tel" + URLEncoder.encode(":", "UTF-8") + "+12345678901"
        };
    String[] shouldAccept =
        new String[] {
          "tel:0",
          "tel:+",
          "tel:+12345678901",
          "tel:+12345678901,1234#",
          "tel:+12345678901"
              + URLEncoder.encode(",", "UTF-8")
              + "1234"
              + URLEncoder.encode("#", "UTF-8"),
          "tel:+12345678901;1234#",
          "tel:+12345678901"
              + URLEncoder.encode(";", "UTF-8")
              + "1234"
              + URLEncoder.encode("#", "UTF-8"),
        };

    for (String uri : shouldReject) {
      assertThat(uri).doesNotMatch(EscapingConventions.FilterTelUri.INSTANCE.getValueFilter());
    }
    for (String uri : shouldAccept) {
      assertThat(uri).matches(EscapingConventions.FilterTelUri.INSTANCE.getValueFilter());
    }
  }

  @Test
  public void testFilterCss() {
    String[] shouldReject =
        new String[] {
          "linear-gradient(rgba(calc())", "Arial,'Roboto Medium'", "Arial,\"Roboto Medium\""
        };

    String[] shouldAccept =
        new String[] {
          "drop-shadow(1px 2px 3px #000)",
          "linear-gradient(180deg, rgba(0, 0, 0, 0) 55%, rgba(0, 0, 0, 0.5) 80%,rgba(0, 0, 0,"
              + " 0.75) 100%)",
          "invert(1) hue-rotate(180deg)",
          "translateY(-50%) translate(0) translate3d(-10px, 40px, 0)",
          "translateX(calc(-10.55% - 8px))",
          "rotateZ(180deg) rotate(90deg)",
        };

    for (String uri : shouldReject) {
      assertThat(uri).doesNotMatch(EscapingConventions.FilterCssValue.INSTANCE.getValueFilter());
    }
    for (String uri : shouldAccept) {
      assertThat(uri).matches(EscapingConventions.FilterCssValue.INSTANCE.getValueFilter());
    }
  }
}
