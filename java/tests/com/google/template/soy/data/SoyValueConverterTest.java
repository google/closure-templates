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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyValueConverter.
 *
 */
@RunWith(JUnit4.class)
public class SoyValueConverterTest {

  private static final SoyValueConverter CONVERTER = SoyValueConverter.UNCUSTOMIZED_INSTANCE;

  @Test
  public void testDictCreation() {
    SoyEasyDict dict1 = CONVERTER.newEasyDict();
    assertEquals(0, dict1.getItemCnt());
    dict1.set("boo", 111);
    dict1.set("foo.goo", 222);

    SoyEasyDict dict2 = CONVERTER.newEasyDict("foo", 3.14, "too", true);
    assertEquals(3.14, dict2.get("foo").floatValue(), 0.0);
    assertEquals(true, dict2.get("too").booleanValue());

    SoyEasyDict dict3 = CONVERTER.newEasyDictFromDict(dict1);
    assertEquals(111, dict3.get("boo").integerValue());
    assertEquals(222, ((SoyEasyDict) dict3.get("foo")).get("goo").integerValue());

    SoyEasyDict dict4 =
        CONVERTER.newEasyDictFromJavaStringMap(ImmutableMap.of("foo", 3.14, "too", true));
    assertEquals(3.14, dict4.get("foo").floatValue(), 0.0);
    assertEquals(true, dict4.get("too").booleanValue());
  }

  @Test
  public void testListCreation() {
    SoyEasyList list2 = CONVERTER.newEasyList(3.14, true);
    assertEquals(3.14, list2.get(0).floatValue(), 0.0);
    assertEquals(true, list2.get(1).booleanValue());

    SoyEasyList list4 = CONVERTER.newEasyListFromJavaIterable(ImmutableList.of(3.14, true));
    assertEquals(3.14, list4.get(0).floatValue(), 0.0);
    assertEquals(true, list4.get(1).booleanValue());
  }

  @Test
  public void testConvertBasic() {
    assertEquals(NullData.INSTANCE, CONVERTER.convert(null));
    assertEquals("boo", CONVERTER.convert(StringData.forValue("boo")).resolve().stringValue());
    assertEquals("boo", CONVERTER.convert("boo").resolve().stringValue());
    assertEquals(true, CONVERTER.convert(true).resolve().booleanValue());
    assertEquals(8, CONVERTER.convert(8).resolve().integerValue());
    assertEquals(
        "foo",
        ((SoyDict) CONVERTER.convert(ImmutableMap.of("boo", "foo"))).getField("boo").stringValue());
    assertEquals(
        "goo", ((SoyList) CONVERTER.convert(ImmutableList.of("goo"))).get(0).stringValue());
    assertEquals("hoo", ((SoyList) CONVERTER.convert(ImmutableSet.of("hoo"))).get(0).stringValue());
    assertEquals(3.14, CONVERTER.convert(3.14).resolve().floatValue(), 0.0);
    assertEquals(3.14F, (float) CONVERTER.convert(3.14F).resolve().floatValue(), 0.0f);
  }

  @Test
  public void testConvertFuture() {
    assertTrue(
        CONVERTER.convert(Futures.immediateFuture("future")) instanceof SoyFutureValueProvider);
    assertEquals("soy", CONVERTER.convert(Futures.immediateFuture("soy")).resolve().stringValue());
  }

  @Test
  public void testConvertSoyGlobalsValue() {
    assertEquals(
        "foo",
        CONVERTER
            .convert(
                new SoyGlobalsValue() {
                  @Override
                  public Object getSoyGlobalValue() {
                    return "foo";
                  }
                })
            .resolve()
            .stringValue());
  }

  @Test
  public void testConvertWithCustomConverter() {
    Module guiceModuleWithSoyCustomValueConverters =
        new Module() {
          @Override
          public void configure(Binder binder) {
            // Do nothing.
          }

          @Provides
          List<SoyCustomValueConverter> provideSoyCustomValueConverters() {
            return ImmutableList.<SoyCustomValueConverter>of(
                // This converter converts non-primitive arrays to SoyList.
                new SoyCustomValueConverter() {
                  @Override
                  public SoyValueProvider convert(SoyValueConverter valueConverter, Object obj) {
                    if (obj instanceof Object[]) {
                      return valueConverter.convert(Arrays.asList((Object[]) obj));
                    } else {
                      return null;
                    }
                  }
                });
          }
        };

    Injector injector = Guice.createInjector(guiceModuleWithSoyCustomValueConverters);
    SoyValueConverter converter = injector.getInstance(SoyValueConverter.class);

    // Test convert non-primitive arrays.
    assertEquals(
        "foo", ((SoyList) converter.convert(new String[] {"boo", "foo"})).get(1).stringValue());
    assertEquals(
        5, ((SoyList) converter.convert(new Integer[] {1, 3, 5, 7})).get(2).integerValue());

    // Test convert primitive arrays (expected to error).
    try {
      converter.convert(new int[] {1, 3, 5, 7});
      fail();
    } catch (SoyDataException expected) {
    }

    // Test that basic conversions still work.
    assertEquals(NullData.INSTANCE, converter.convert(null));
    assertEquals("boo", converter.convert("boo").resolve().stringValue());
    assertEquals(3.14, converter.convert(3.14).resolve().floatValue(), 0.0);
  }
}
