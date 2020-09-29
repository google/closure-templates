/*
 * Copyright 2013 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.data.SoyValueUnconverter.unconvert;
import static com.google.template.soy.data.UnsafeSanitizedContentOrdainer.ordainAsSafe;

import com.google.common.collect.Lists;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.testing.SomeEmbeddedMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyValueConverter.
 *
 */
@RunWith(JUnit4.class)
public class SoyValueUnconverterTest {

  private static final SoyValueConverter CONVERTER = SoyValueConverter.INSTANCE;

  @Test
  public void testUnconvert() {
    assertThat(unconvert(NullData.INSTANCE)).isNull();
    assertThat(unconvert(StringData.forValue("boo"))).isEqualTo("boo");
    assertThat(unconvert(BooleanData.TRUE)).isEqualTo(true);
    assertThat(unconvert(IntegerData.forValue(8))).isEqualTo(8L);
    assertThat(unconvert(FloatData.forValue(8.2))).isEqualTo(8.2D);

    Message message = SomeEmbeddedMessage.newBuilder().setSomeEmbeddedString("foo").build();
    assertThat(unconvert(SoyProtoValue.create(message))).isEqualTo(message);

    List<String> list = Lists.newArrayList("a", "b", null, "c");
    assertThat((List<?>) unconvert(CONVERTER.convert(list))).isEqualTo(list);

    Map<String, String> map = new HashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    map.put("k3", null);
    assertThat((Map<?, ?>) unconvert(CONVERTER.convert(map))).isEqualTo(map);

    SafeHtml safeHtml = ordainAsSafe("<p>foo</p>", ContentKind.HTML).toSafeHtml();
    assertThat(unconvert(CONVERTER.convert(safeHtml))).isEqualTo(safeHtml);

    SafeStyle style = ordainAsSafe("background-color: red;", ContentKind.CSS).toSafeStyle();
    assertThat(unconvert(CONVERTER.convert(style))).isEqualTo(style);

    SafeStyleSheet styleSheet =
        ordainAsSafe(".foo { background-color: red; }", ContentKind.CSS).toSafeStyleSheet();
    assertThat(unconvert(CONVERTER.convert(styleSheet))).isEqualTo(styleSheet);

    SafeScript script = SafeScript.EMPTY;
    assertThat(unconvert(CONVERTER.convert(script))).isEqualTo(script);

    TrustedResourceUrl resourceUrl =
        ordainAsSafe("https://google.com/a", ContentKind.TRUSTED_RESOURCE_URI)
            .toTrustedResourceUrl();
    assertThat(unconvert(CONVERTER.convert(resourceUrl))).isEqualTo(resourceUrl);

    SafeUrl url = ordainAsSafe("https://google.com/a", ContentKind.URI).toSafeUrl();
    assertThat(unconvert(CONVERTER.convert(url))).isEqualTo(url);
  }
}
