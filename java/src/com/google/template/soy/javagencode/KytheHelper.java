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


import com.google.common.base.Preconditions;
import com.google.protobuf.Message;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.KytheMode;
import javax.annotation.Nullable;

/** Helper for constructing GeneratedCodeInfo proto */
public class KytheHelper {

  private final String kytheCorpus;
  private final SourceFilePath sourceFilePath;

  public KytheHelper(SourceFilePath sourceFilePath) {
    this("", sourceFilePath);
  }

  public KytheHelper(String kytheCorpus, SourceFilePath sourceFilePath) {
    this.kytheCorpus = kytheCorpus;
    this.sourceFilePath = Preconditions.checkNotNull(sourceFilePath);
  }

  @Nullable
  public Message getGeneratedCodeInfo() {
    return null;
  }

  public void appendGeneratedCodeInfo(KytheMode kytheMode, Appendable to) {
  }

  public void addKytheLinkTo(ByteSpan source, ByteSpan target) {
    if (source.isKnown() && target.isKnown()) {
      addKytheLinkTo(source.getStart(), source.getEnd(), target.getStart(), target.getEnd());
    }
  }

  public void addKytheLinkTo(int sourceStart, int sourceEnd, int targetStart, int targetEnd) {
  }
}
