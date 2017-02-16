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
    SoyDict dict1 = CONVERTER.newDict();
    assertThat(dict1.getItemCnt()).isEqualTo(0);

    SoyDict dict2 = CONVERTER.newDict("foo", 3.14, "too", true);
    assertThat(dict2.getField("foo").floatValue()).isWithin(0.0).of(3.14);
    assertThat(dict2.getField("too").booleanValue()).isTrue();

    SoyDict dict3 = CONVERTER.newDict("boo", 111, "foo.goo", 222);
    assertThat(dict3.getField("boo").integerValue()).isEqualTo(111);
    assertThat(((SoyDict) dict3.getField("foo")).getField("goo").integerValue()).isEqualTo(222);

    SoyDict dict4 = CONVERTER.newDictFromMap(ImmutableMap.of("foo", 3.14, "too", true));
    assertThat(dict4.getField("foo").floatValue()).isWithin(0.0).of(3.14);
    assertThat(dict4.getField("too").booleanValue()).isTrue();
  }

  @Test
  public void testListCreation() {
    SoyList list2 = CONVERTER.newList(3.14, true);
    assertThat(list2.get(0).floatValue()).isWithin(0.0).of(3.14);
    assertThat(list2.get(1).booleanValue()).isTrue();

    SoyList list4 = CONVERTER.newList(3.14, true);
    assertThat(list4.get(0).floatValue()).isWithin(0.0).of(3.14);
    assertThat(list4.get(1).booleanValue()).isTrue();
  }

  @Test
  public void testConvertBasic() {
    assertThat(CONVERTER.convert(null)).isEqualTo(NullData.INSTANCE);
    assertThat(CONVERTER.convert(StringData.forValue("boo")).resolve().stringValue())
        .isEqualTo("boo");
    assertThat(CONVERTER.convert("boo").resolve().stringValue()).isEqualTo("boo");
    assertThat(CONVERTER.convert(true).resolve().booleanValue()).isTrue();
    assertThat(CONVERTER.convert(8).resolve().integerValue()).isEqualTo(8);
    assertThat(
            ((SoyDict) CONVERTER.convert(ImmutableMap.of("boo", "foo")))
                .getField("boo")
                .stringValue())
        .isEqualTo("foo");
    assertThat(((SoyList) CONVERTER.convert(ImmutableList.of("goo"))).get(0).stringValue())
        .isEqualTo("goo");
    assertThat(((SoyList) CONVERTER.convert(ImmutableSet.of("hoo"))).get(0).stringValue())
        .isEqualTo("hoo");
    assertThat(CONVERTER.convert(3.14).resolve().floatValue()).isWithin(0.0).of(3.14);
    assertThat((float) CONVERTER.convert(3.14F).resolve().floatValue()).isWithin(0.0f).of(3.14F);
  }

  @Test
  public void testConvertFuture() {
    assertThat(CONVERTER.convert(Futures.immediateFuture("future")))
        .isInstanceOf(SoyFutureValueProvider.class);
    assertThat(CONVERTER.convert(Futures.immediateFuture("soy")).resolve().stringValue())
        .isEqualTo("soy");
  }

  @Test
  public void testConvertSoyGlobalsValue() {
    assertThat(
            CONVERTER
                .convert(
                    new SoyGlobalsValue() {
                      @Override
                      public Object getSoyGlobalValue() {
                        return "foo";
                      }
                    })
                .resolve()
                .stringValue())
        .isEqualTo("foo");
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
    assertThat(((SoyList) converter.convert(new String[] {"boo", "foo"})).get(1).stringValue())
        .isEqualTo("foo");
    assertThat(((SoyList) converter.convert(new Integer[] {1, 3, 5, 7})).get(2).integerValue())
        .isEqualTo(5);

    // Test convert primitive arrays (expected to error).
    try {
      converter.convert(new int[] {1, 3, 5, 7});
      fail();
    } catch (SoyDataException expected) {
    }

    // Test that basic conversions still work.
    assertThat(converter.convert(null)).isEqualTo(NullData.INSTANCE);
    assertThat(converter.convert("boo").resolve().stringValue()).isEqualTo("boo");
    assertThat(converter.convert(3.14).resolve().floatValue()).isWithin(0.0).of(3.14);
  }
}
