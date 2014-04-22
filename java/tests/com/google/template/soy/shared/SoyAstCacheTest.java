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

package com.google.template.soy.shared;

import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyFileNode;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for SoyAstCache.
 *
 */
public class SoyAstCacheTest extends TestCase {
  private SoyAstCache cache = new SoyAstCache();
  private Version version1 = EasyMock.createMock(Version.class);
  private Version version2 = EasyMock.createMock(Version.class);
  private SoyFileNode fileNode1 = EasyMock.createMock(SoyFileNode.class);
  private SoyFileNode fileNode1Clone = EasyMock.createMock(SoyFileNode.class);
  private SoyFileSupplier supplier1 = EasyMock.createMock(SoyFileSupplier.class);
  private SoyFileSupplier supplier2 = EasyMock.createMock(SoyFileSupplier.class);

  public void setUp() throws Exception {
    super.setUp();

    EasyMock.expect(supplier1.hasChangedSince(version2)).andStubReturn(false);
    EasyMock.expect(supplier1.hasChangedSince(version1)).andStubReturn(true);
    EasyMock.expect(supplier1.getFilePath()).andStubReturn("supplier1.soy");
    EasyMock.replay(supplier1);

    EasyMock.expect(supplier2.hasChangedSince(version2)).andStubReturn(false);
    EasyMock.expect(supplier2.hasChangedSince(version1)).andStubReturn(true);
    EasyMock.expect(supplier2.getFilePath()).andStubReturn("supplier2.soy");
    EasyMock.replay(supplier2);

    EasyMock.expect(fileNode1.clone()).andStubReturn(fileNode1Clone);
    EasyMock.replay(fileNode1);
    EasyMock.expect(fileNode1Clone.clone()).andStubReturn(fileNode1Clone);
    EasyMock.replay(fileNode1Clone);
  }

  public void testGetSet() {

    // Matching version.
    cache.put(supplier1, version2, fileNode1);
    assertEquals(Pair.of(fileNode1Clone, version2), cache.get(supplier1));
    assertNull(cache.get(supplier2));
    assertEquals(Pair.of(fileNode1Clone, version2), cache.get(supplier1));

    // Non matching version.
    cache.put(supplier1, version1, fileNode1);
    assertNull(cache.get(supplier1));
    assertNull(cache.get(supplier2));
  }

  public void testIdGenerator() {

    // Make sure it always returns the same generator.
    assertTrue(cache.getNodeIdGenerator() == cache.getNodeIdGenerator());
  }
}
