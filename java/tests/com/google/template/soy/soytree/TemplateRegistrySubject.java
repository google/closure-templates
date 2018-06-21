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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Truth subject for {@link TemplateRegistry}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class TemplateRegistrySubject extends Subject<TemplateRegistrySubject, TemplateRegistry> {

  private static final Subject.Factory<TemplateRegistrySubject, TemplateRegistry>
      TEMPLATE_REGISTRY = TemplateRegistrySubject::new;

  private TemplateRegistrySubject(FailureMetadata failureMetadata, TemplateRegistry registry) {
    super(failureMetadata, registry);
  }

  static TemplateRegistrySubject assertThatRegistry(TemplateRegistry registry) {
    return Truth.assertAbout(TEMPLATE_REGISTRY).that(registry);
  }

  TemplateBasicNodeSubject containsBasicTemplate(String name) {
    Truth.assertThat(actual().getBasicTemplatesMap()).containsKey(name);
    TemplateBasicNode templateBasicNode = actual().getBasicTemplatesMap().get(name);
    return Truth.assertAbout(TemplateBasicNodeSubject.TEMPLATE_BASIC_NODE).that(templateBasicNode);
  }

  void doesNotContainBasicTemplate(String name) {
    Truth.assertThat(actual().getBasicTemplatesMap()).doesNotContainKey(name);
  }

  TemplateDelegateNodesSubject containsDelTemplate(String name) {
    ImmutableList<TemplateDelegateNode> delTemplates =
        actual().getDelTemplateSelector().delTemplateNameToValues().get(name);
    Truth.assertThat(delTemplates).isNotEmpty();
    return Truth.assertAbout(TemplateDelegateNodesSubject.TEMPLATE_DELEGATE_NODES)
        .that(delTemplates);
  }

  void doesNotContainDelTemplate(String name) {
    Truth.assertThat(actual().getDelTemplateSelector().hasDelTemplateNamed(name)).isFalse();
  }

  static class TemplateBasicNodeSubject
      extends Subject<TemplateBasicNodeSubject, TemplateBasicNode> {

    private static final Subject.Factory<TemplateBasicNodeSubject, TemplateBasicNode>
        TEMPLATE_BASIC_NODE = TemplateBasicNodeSubject::new;

    TemplateBasicNodeSubject(FailureMetadata failureMetadata, TemplateBasicNode templateBasicNode) {
      super(failureMetadata, templateBasicNode);
    }

    void definedAt(SourceLocation srcLocation) {
      Truth.assertThat(actual().getSourceLocation()).isEqualTo(srcLocation);
    }
  }

  static class TemplateDelegateNodesSubject
      extends Subject<TemplateDelegateNodesSubject, List<TemplateDelegateNode>> {

    private static final Subject.Factory<TemplateDelegateNodesSubject, List<TemplateDelegateNode>>
        TEMPLATE_DELEGATE_NODES = TemplateDelegateNodesSubject::new;

    TemplateDelegateNodesSubject(
        FailureMetadata failureMetadata, List<TemplateDelegateNode> nodes) {
      super(failureMetadata, nodes);
    }

    void definedAt(SourceLocation sourceLocation) {
      List<SourceLocation> locations = new ArrayList<>();
      for (TemplateDelegateNode delegateNode : actual()) {
        locations.add(delegateNode.getSourceLocation());
      }
      Truth.assertThat(locations).contains(sourceLocation);
    }
  }
}
