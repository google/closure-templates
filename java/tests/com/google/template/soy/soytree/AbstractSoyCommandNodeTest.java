/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import junit.framework.TestCase;


/**
 * Unit tests for AbstractCommandNode.
 *
 * @author Kai Huang
 */
public class AbstractSoyCommandNodeTest extends TestCase {


  public void testGetTagString() {

    DummyNode dn = new DummyNode(8, "blah blah");
    assertEquals("{dummy blah blah}", dn.getTagString());
    assertEquals("{dummy blah blah}", dn.toSourceString());

    dn = new DummyNode(8, "{blah} blah");
    assertEquals("{{dummy {blah} blah}}", dn.getTagString());
    assertEquals("{{dummy {blah} blah}}", dn.toSourceString());

    dn = new DummyNode(8, "blah {blah}");
    assertEquals("{{dummy blah {blah} }}", dn.getTagString());
    assertEquals("{{dummy blah {blah} }}", dn.toSourceString());
  }


  private static class DummyNode extends AbstractCommandNode {

    public DummyNode(int id, String commandText) {
      super(id, "dummy", commandText);
    }

    @Override public Kind getKind() {
      throw new UnsupportedOperationException();
    }

    @Override public SoyNode clone() {
      throw new UnsupportedOperationException();
    }
  }

}
