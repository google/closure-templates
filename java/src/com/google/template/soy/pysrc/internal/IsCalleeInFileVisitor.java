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

package com.google.template.soy.pysrc.internal;

import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * Visitor to determine whether the given callee string is a call to a template in the same file.
 *
 * <p>Important: This class is in {@link ApiCallScope} because it memoizes results that are reusable
 * for the same parse tree. If we change the parse tree between uses of the scoped instance, then
 * the results may not be correct. (In that case, we would need to take this class out of {@code
 * ApiCallScope} and rewrite the code somehow to still take advantage of the memoized results to the
 * extent that they remain correct.)
 *
 */
@ApiCallScope
final class IsCalleeInFileVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {

  /** The memoized results of past visits to nodes. */
  private final Map<CallBasicNode, Boolean> memoizedResults;

  @Inject
  IsCalleeInFileVisitor() {
    memoizedResults = new HashMap<>();
  }

  @Override
  protected Boolean visitCallBasicNode(CallBasicNode node) {
    if (memoizedResults.containsKey(node)) {
      return memoizedResults.get(node);
    } else {
      SoyFileNode file = node.getNearestAncestor(SoyFileNode.class);
      Set<String> templatesInFile = new HashSet<>();
      for (TemplateNode template : file.getChildren()) {
        templatesInFile.add(template.getTemplateName());
      }

      String calleeName = node.getCalleeName();
      Boolean result = templatesInFile.contains(calleeName);
      memoizedResults.put(node, result);
      return result;
    }
  }
}
