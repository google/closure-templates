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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SoyAstCache}.
 *
 */
@RunWith(JUnit4.class)
public final class SoyAstCacheTest {
  @AutoValue
  abstract static class FakeVersion implements Version {
    abstract int version();
  }

  private SoyAstCache cache = new SoyAstCache();
  private final FakeVersion version1 = new AutoValue_SoyAstCacheTest_FakeVersion(1);
  private final FakeVersion version2 = new AutoValue_SoyAstCacheTest_FakeVersion(2);

  private SoyFileNode fileNode1 =
      new SoyFileNode(
          0xdeadbeef,
          "test.soy",
          SoyFileKind.SRC,
          new NamespaceDeclaration(
              Identifier.create("fake.namespace", SourceLocation.UNKNOWN),
              ImmutableList.<CommandTagAttribute>of(),
              ErrorReporter.exploding()),
          new TemplateNode.SoyFileHeaderInfo("fake.namespace"));

  @Test
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

  @Test
  public void testIdGenerator() {

    // Make sure it always returns the same generator.
    assertThat(cache.getNodeIdGenerator()).isSameAs(cache.getNodeIdGenerator());
  }
}
