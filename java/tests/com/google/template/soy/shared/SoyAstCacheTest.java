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
import com.google.template.soy.base.internal.SoyFileSupplier.Version;
import com.google.template.soy.error.ErrorReporter;
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
    static FakeVersion create(int version) {
      return new AutoValue_SoyAstCacheTest_FakeVersion(version);
    }

    abstract int version();
  }

  private final SoyAstCache cache = new SoyAstCache();
  private final FakeVersion version1 = FakeVersion.create(1);
  private final FakeVersion version2 = FakeVersion.create(2);

  private final SoyFileNode fileNode1 =
      new SoyFileNode(
          0xdeadbeef,
          SourceLocation.UNKNOWN,
          new NamespaceDeclaration(
              Identifier.create("fake.namespace", SourceLocation.UNKNOWN),
              ImmutableList.of(),
              ErrorReporter.exploding(),
              SourceLocation.UNKNOWN),
          new TemplateNode.SoyFileHeaderInfo("fake.namespace"),
          ImmutableList.of());

  @Test
  public void testGetSet() {
    // Matching version.
    cache.put("foo", version2, fileNode1);
    SoyFileNode file = cache.get("foo", version2);
    // ids aren't modified
    assertThat(file.getId()).isEqualTo(0xdeadbeef);
    // the cache doesn't make copies
    assertThat(file).isSameInstanceAs(fileNode1);

    assertThat(cache.get("bar", version1)).isNull();

    file = cache.get("foo", version2);
    assertThat(file.getId()).isEqualTo(0xdeadbeef);
    assertThat(file).isSameInstanceAs(fileNode1);

    // Non matching version.
    cache.put("foo", version1, fileNode1);
    assertThat(cache.get("foo", version2)).isNull();
    assertThat(cache.get("bar", version1)).isNull();
  }
}
