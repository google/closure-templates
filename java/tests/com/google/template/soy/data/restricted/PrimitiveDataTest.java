/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.data.restricted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.template.soy.data.SoyDataException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for subclasses of PrimitiveData.
 *
 */
@RunWith(JUnit4.class)
public class PrimitiveDataTest {

  @Test
  public void testUndefinedData() {

    UndefinedData ud = UndefinedData.INSTANCE;
    try {
      ud.coerceToString();
      fail();
    } catch (SoyDataException ste) {
      // Test passes.
    }
    assertEquals(false, ud.coerceToBoolean());
    assertTrue(ud.equals(UndefinedData.INSTANCE));
  }

  @Test
  public void testNullData() {

    NullData nd = NullData.INSTANCE;
    assertEquals("null", nd.coerceToString());
    assertEquals(false, nd.coerceToBoolean());
    assertTrue(nd.equals(NullData.INSTANCE));
  }

  @Test
  public void testBooleanData() {

    BooleanData bd0 = BooleanData.FALSE;
    assertEquals(false, bd0.getValue());
    assertEquals("false", bd0.coerceToString());
    assertEquals(false, bd0.coerceToBoolean());
    assertTrue(bd0.equals(BooleanData.FALSE));

    BooleanData bd1 = BooleanData.TRUE;
    assertEquals(true, bd1.getValue());
    assertEquals("true", bd1.coerceToString());
    assertEquals(true, bd1.coerceToBoolean());
    assertTrue(bd1.equals(BooleanData.TRUE));

    assertFalse(bd0.equals(bd1));
    assertFalse(bd1.equals(bd0));
  }

  @Test
  public void testIntegerData() {

    IntegerData id0 = IntegerData.ZERO;
    assertEquals(0, id0.getValue());
    assertEquals("0", id0.coerceToString());
    assertEquals(false, id0.coerceToBoolean());
    assertTrue(id0.equals(IntegerData.ZERO));

    IntegerData id1 = IntegerData.forValue(26);
    assertEquals(26, id1.getValue());
    assertEquals("26", id1.coerceToString());
    assertEquals(true, id1.coerceToBoolean());
    assertTrue(id1.equals(IntegerData.forValue(26)));

    assertFalse(id0.equals(id1));
    assertFalse(id1.equals(id0));
  }

  @Test
  public void testFloatData() {

    FloatData fd0 = FloatData.forValue(0.0);
    assertEquals(0.0, fd0.getValue(), 0.0);
    assertEquals("0", fd0.coerceToString());
    assertEquals(false, fd0.coerceToBoolean());
    assertTrue(fd0.equals(FloatData.forValue(0.0)));

    FloatData fd1 = FloatData.forValue(3.14);
    assertEquals(3.14, fd1.getValue(), 0.0);
    assertEquals("3.14", fd1.coerceToString());
    assertEquals(true, fd1.coerceToBoolean());
    assertTrue(fd1.equals(FloatData.forValue(3.14)));

    assertFalse(fd0.equals(fd1));
    assertFalse(fd1.equals(fd0));
  }

  @Test
  public void testFloatDataToString() {
    // Tests that our toString is similar to Javascript's number toString.
    assertEquals("0", FloatData.toString(0.0));
    assertEquals("0", FloatData.toString(-0.0));
    assertEquals("1", FloatData.toString(1.0));
    assertEquals("-1", FloatData.toString(-1.0));
    assertEquals("1000000000000000", FloatData.toString(1.0e15));
    assertEquals("-1000000000000000", FloatData.toString(-1.0e15));
    assertEquals("-1000000000000000", FloatData.toString(-1.0e15));
    assertEquals("1.51e32", FloatData.toString(1.51e32));
    assertEquals("NaN", FloatData.toString(Double.NaN));
    assertEquals("Infinity", FloatData.toString(Double.POSITIVE_INFINITY));
    assertEquals("-Infinity", FloatData.toString(Double.NEGATIVE_INFINITY));
  }

  @Test
  public void testNumberData() {

    IntegerData id0 = IntegerData.ZERO;
    IntegerData id1 = IntegerData.forValue(2);
    FloatData fd0 = FloatData.forValue(0.0);
    FloatData fd1 = FloatData.forValue(2.0);

    assertEquals(0.0, id0.toFloat(), 0.0);
    assertEquals(2.0, id1.toFloat(), 0.0);
    assertEquals(0.0, fd0.toFloat(), 0.0);
    assertEquals(2.0, fd1.toFloat(), 0.0);

    assertTrue(id0.equals(fd0));
    assertTrue(fd0.equals(id0));
    assertTrue(id1.equals(fd1));
    assertTrue(fd1.equals(id1));
    assertFalse(id0.equals(fd1));
    assertFalse(fd1.equals(id0));
    assertFalse(id1.equals(fd0));
    assertFalse(fd0.equals(id1));
  }

  @Test
  public void testStringData() {

    StringData sd0 = StringData.EMPTY_STRING;
    assertEquals("", sd0.getValue());
    assertEquals("", sd0.coerceToString());
    assertEquals(false, sd0.coerceToBoolean());
    assertTrue(sd0.equals(StringData.EMPTY_STRING));

    StringData sd1 = StringData.forValue("boo");
    assertEquals("boo", sd1.getValue());
    assertEquals("boo", sd1.coerceToString());
    assertEquals(true, sd1.coerceToBoolean());
    assertTrue(sd1.equals(StringData.forValue("boo")));

    assertFalse(sd0.equals(sd1));
    assertFalse(sd1.equals(sd0));
  }
}
