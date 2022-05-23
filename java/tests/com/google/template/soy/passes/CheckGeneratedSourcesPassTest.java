/*
 * Copyright 2021 Google Inc.
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
package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.Comment;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckGeneratedSourcesPass}. */
@RunWith(JUnit4.class)
public final class CheckGeneratedSourcesPassTest {

  private static final SourceFilePath PATH1 = SourceFilePath.create("file1.soy");
  private static final SourceFilePath PATH2 = SourceFilePath.create("file2.soy");

  private final ErrorReporter errorReporter = ErrorReporter.createForTest();
  private final IdGenerator idGenerator = new FixedIdGenerator(-1);

  @Test
  public void testNoCommentNoGenerated() throws Exception {
    CheckGeneratedSourcesPass pass =
        new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of());
    assertThat(pass.run(ImmutableList.of(withComments("", PATH1)), idGenerator))
        .isEqualTo(Result.CONTINUE);
  }

  @Test
  public void commentNoGenerated() throws Exception {
    CheckGeneratedSourcesPass pass =
        new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of());
    assertThat(
            pass.run(ImmutableList.of(withComments("@SoySourceGenerator=Foo", PATH1)), idGenerator))
        .isEqualTo(Result.CONTINUE);
  }

  @Test
  public void noCommentGenerated() throws Exception {
    CheckGeneratedSourcesPass pass =
        new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of(PATH1));
    assertThat(pass.run(ImmutableList.of(withComments("", PATH1)), idGenerator))
        .isEqualTo(Result.STOP);
    assertThat(errorReporter.getErrors()).hasSize(1);
  }

  @Test
  public void commentGenerated() throws Exception {
    // Matching comment.
    CheckGeneratedSourcesPass pass =
        new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of(PATH1));
    assertThat(
            pass.run(ImmutableList.of(withComments("@SoySourceGenerator=Foo", PATH1)), idGenerator))
        .isEqualTo(Result.CONTINUE);

    // Don't fail if generated file is not in parse set (shouldn't be possible).
    pass = new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of(PATH2));
    assertThat(
            pass.run(ImmutableList.of(withComments("@SoySourceGenerator=Foo", PATH1)), idGenerator))
        .isEqualTo(Result.CONTINUE);

    // Comment in one file can't save some other file.
    pass = new CheckGeneratedSourcesPass(errorReporter, ImmutableSet.of(PATH2));
    assertThat(
            pass.run(
                ImmutableList.of(
                    withComments("@SoySourceGenerator=Foo", PATH1), withComments("", PATH2)),
                idGenerator))
        .isEqualTo(Result.STOP);
    assertThat(errorReporter.getErrors()).hasSize(1);
  }

  private static SoyFileNode withComments(String comment, SourceFilePath path) {
    return new SoyFileNode(
        1,
        new SourceLocation(path, 1, 1, 2, 1),
        new NamespaceDeclaration(
            Identifier.create("ns", SourceLocation.UNKNOWN),
            ImmutableList.of(),
            null,
            SourceLocation.UNKNOWN),
        SoyFileHeaderInfo.EMPTY,
        ImmutableList.of(Comment.create(Comment.Type.LINE, comment, SourceLocation.UNKNOWN)));
  }
}
