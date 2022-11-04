/*
 * Copyright 2022 Google Inc.
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

package com.google.template.soy.javagencode;

import com.google.common.base.Joiner;
import com.google.common.base.Utf8;
import com.google.protobuf.Message;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.ParamInfo;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.TemplateInfo;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

class KytheHelper {

  private final String kytheCorpus;

  public KytheHelper(String kytheCorpus) {
    this.kytheCorpus = kytheCorpus;
  }

  public boolean isEnabled() {
    return !kytheCorpus.isEmpty();
  }

  @Nullable
  public Message getGeneratedCodeInfo() {
    if (!isEnabled()) {
      return null;
    }
    return null;
  }

  public void addKytheLinkTo(Span classNameSpan, SoyFileNode file) {
    if (!isEnabled()) {
      return;
    }
  }

  public void addKytheLinkTo(Span classNameSpan, TemplateInfo template) {
    if (!isEnabled()) {
      return;
    }
  }

  public void addKytheLinkTo(Span methodNameSpan, ParamInfo paramInfo, TemplateInfo template) {
    if (!isEnabled()) {
      return;
    }
  }

  /**
   * Appends each member of {@code parts} to {@code ilb} by concatenating the parts and calling
   * {@link IndentedLinesBuilder#appendLineStart}. For every part, calculate a {@link Span} of the
   * appended text and return these spans as a list.
   *
   * @return a list of spans, one for every member of {@code parts}.
   */
  public List<Span> appendLineStartAndGetSpans(IndentedLinesBuilder ilb, String... parts) {
    return appendAndGetSpans(ilb, false, parts);
  }

  /**
   * Like {@link #appendLineStartAndGetSpans} but appends the parts with {@link
   * IndentedLinesBuilder#appendLine}.
   */
  public List<Span> appendLineAndGetSpans(IndentedLinesBuilder ilb, String... parts) {
    return appendAndGetSpans(ilb, true, parts);
  }

  private List<Span> appendAndGetSpans(
      IndentedLinesBuilder ilb, boolean fullLine, String... parts) {
    String line = Joiner.on("").join(parts);
    if (fullLine) {
      ilb.appendLine(line);
    } else {
      ilb.appendLineStart(line);
    }

    // Count backwards from end. ilb may insert whitespace before line.
    int endingLength = ilb.getByteLength();
    int partStart = endingLength - Utf8.encodedLength(line) - /* newline */ (fullLine ? 1 : 0);

    List<Span> spans = new ArrayList<>(parts.length);
    for (String part : parts) {
      int partEnd = partStart + Utf8.encodedLength(part) - 1;
      spans.add(new Span(partStart, partEnd));
      partStart = partEnd + 1;
    }
    return spans;
  }

  static final class Span {
    private final int start;
    private final int end;

    public Span(int startOffset, int endOffset) {
      this.start = startOffset;
      this.end = endOffset;
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }
  }
}
