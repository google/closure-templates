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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link SoyAstCache}.
 *
 */
public final class SoyAstCacheTest extends TestCase {
  private SoyAstCache cache = new SoyAstCache();
  private Version version1 = EasyMock.createMock(Version.class);
  private Version version2 = EasyMock.createMock(Version.class);
  private SoyFileNode fileNode1 =
      new SoyFileNode(
          0xdeadbeef,
          "test.soy",
          SoyFileKind.SRC,
          NamespaceDeclaration.NULL,
          new TemplateNode.SoyFileHeaderInfo("fake.namespace"));
  private SoyFileSupplier supplier1 = EasyMock.createMock(SoyFileSupplier.class);
  private SoyFileSupplier supplier2 = EasyMock.createMock(SoyFileSupplier.class);

  @Override public void setUp() throws Exception {
    super.setUp();

    EasyMock.expect(supplier1.hasChangedSince(version2)).andStubReturn(false);
    EasyMock.expect(supplier1.hasChangedSince(version1)).andStubReturn(true);
    EasyMock.expect(supplier1.getFilePath()).andStubReturn("supplier1.soy");
    EasyMock.replay(supplier1);

    EasyMock.expect(supplier2.hasChangedSince(version2)).andStubReturn(false);
    EasyMock.expect(supplier2.hasChangedSince(version1)).andStubReturn(true);
    EasyMock.expect(supplier2.getFilePath()).andStubReturn("supplier2.soy");
    EasyMock.replay(supplier2);
  }

  public void testGetSet() {

    // Matching version.
    cache.put("foo", VersionedFile.of(fileNode1, version2));
    VersionedFile versionedFile = cache.get("foo", version2);
    assertThat(versionedFile.file().getId()).isEqualTo(0xdeadbeef);
    assertThat(versionedFile.file()).isNotSameAs(fileNode1);
    assertThat(versionedFile.version()).isEqualTo(version2);

    assertThat(cache.get("bar", version1)).isNull();

    versionedFile = cache.get("foo", version2);
    assertThat(versionedFile.file().getId()).isEqualTo(0xdeadbeef);
    assertThat(versionedFile.file()).isNotSameAs(fileNode1);
    assertThat(versionedFile.version()).isEqualTo(version2);

    // Non matching version.
    cache.put("foo", VersionedFile.of(fileNode1, version1));
    assertThat(cache.get("foo", version2)).isNull();
    assertThat(cache.get("bar", version1)).isNull();
  }

  public void testIdGenerator() {

    // Make sure it always returns the same generator.
    assertThat(cache.getNodeIdGenerator()).isSameAs(cache.getNodeIdGenerator());
  }
}
