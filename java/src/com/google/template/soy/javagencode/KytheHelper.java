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
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.ParamInfo;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.TemplateInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Helper for constructing GeneratedCodeInfo proto */
public class KytheHelper {

  private final String kytheCorpus;
  private final SourceFilePath sourceFilePath;

  public KytheHelper(String kytheCorpus, SourceFilePath sourceFilePath) {
    this.kytheCorpus = kytheCorpus;
    this.sourceFilePath = sourceFilePath;
  }

  public KytheHelper(String kytheCorpus) {
    this.kytheCorpus = kytheCorpus;
    this.sourceFilePath = null;
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

  public void addKytheLinkTo(ByteSpan classNameSpan, SoyFileNode file) {
    if (!isEnabled()) {
      return;
    }
  }

  public void addKytheLinkTo(ByteSpan templateNameSpan, TemplateInfo template) {
    addKytheLinkTo(templateNameSpan, template.sourceLocation(), template.template());
  }

  public void addKytheLinkTo(ByteSpan templateNameSpan, TemplateNode template) {
    addKytheLinkTo(templateNameSpan, template.getTemplateNameLocation(), template);
  }

  private void addKytheLinkTo(
      ByteSpan templateNameSpan, SourceLocation sourceLocation, TemplateNode template) {
    if (!isEnabled()) {
      return;
    }
  }

  public void addKytheLinkTo(ByteSpan paramSpan, ParamInfo paramInfo, TemplateInfo template) {
    addKytheLinkTo(
        paramSpan, template.sourceLocation(), template.template(), paramInfo.param().getName());
  }

  public void addKytheLinkTo(ByteSpan paramSpan, TemplateNode template, TemplateParam param) {
    addKytheLinkTo(paramSpan, param.nameLocation(), template, param.name());
  }

  public void addKytheLinkTo(int sourceStart, int sourceEnd, int targetStart, int targetEnd) {
  }

  private void addKytheLinkTo(
      ByteSpan paramSpan, SourceLocation location, TemplateNode template, String paramName) {
    if (!isEnabled()) {
      return;
    }
  }

  /**
   * Appends each member of {@code parts} to {@code ilb} by concatenating the parts and calling
   * {@link IndentedLinesBuilder#appendLineStart}. For every part, calculate a {@link ByteSpan} of
   * the appended text and return these spans as a list.
   *
   * @return a list of spans, one for every member of {@code parts}.
   */
  public List<ByteSpan> appendLineStartAndGetSpans(IndentedLinesBuilder ilb, String... parts) {
    return appendAndGetSpans(ilb, false, parts);
  }

  /**
   * Like {@link #appendLineStartAndGetSpans} but appends the parts with {@link
   * IndentedLinesBuilder#appendLine}.
   */
  public List<ByteSpan> appendLineAndGetSpans(IndentedLinesBuilder ilb, String... parts) {
    return appendAndGetSpans(ilb, true, parts);
  }

  private List<ByteSpan> appendAndGetSpans(
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

    List<ByteSpan> spans = new ArrayList<>(parts.length);
    for (String part : parts) {
      int partEnd = partStart + Utf8.encodedLength(part); // end is exclusive
      spans.add(new ByteSpan(partStart, partEnd));
      partStart = partEnd;
    }
    return spans;
  }

}
