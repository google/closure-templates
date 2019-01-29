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

  private TemplateRegistrySubject(FailureMetadata failureMetadata, TemplateRegistry registry) {
    super(failureMetadata, registry);
  }

  static TemplateRegistrySubject assertThatRegistry(TemplateRegistry registry) {
    return Truth.assertAbout(TemplateRegistrySubject::new).that(registry);
  }

  TemplateBasicNodeSubject containsBasicTemplate(String name) {
    TemplateMetadata templateBasicNode = actual().getBasicTemplateOrElement(name);
    if (templateBasicNode == null) {
      fail("The registry doesn't contain a template named", name);
    }
    return Truth.assertAbout(TemplateBasicNodeSubject::new).that(templateBasicNode);
  }

  void doesNotContainBasicTemplate(String name) {
    TemplateMetadata templateBasicNode = actual().getBasicTemplateOrElement(name);
    if (templateBasicNode != null) {
      fail("The registry does contain a template named", name);
    }
  }

  TemplateDelegateNodesSubject containsDelTemplate(String name) {
    ImmutableList<TemplateMetadata> delTemplates =
        actual().getDelTemplateSelector().delTemplateNameToValues().get(name);
    Truth.assertThat(delTemplates).isNotEmpty();
    return Truth.assertAbout(TemplateDelegateNodesSubject::new).that(delTemplates);
  }

  void doesNotContainDelTemplate(String name) {
    Truth.assertThat(actual().getDelTemplateSelector().hasDelTemplateNamed(name)).isFalse();
  }

  static class TemplateBasicNodeSubject
      extends Subject<TemplateBasicNodeSubject, TemplateMetadata> {
    TemplateBasicNodeSubject(FailureMetadata failureMetadata, TemplateMetadata templateBasicNode) {
      super(failureMetadata, templateBasicNode);
    }

    void definedAt(SourceLocation srcLocation) {
      Truth.assertThat(actual().getSourceLocation()).isEqualTo(srcLocation);
    }
  }

  static class TemplateDelegateNodesSubject
      extends Subject<TemplateDelegateNodesSubject, List<TemplateMetadata>> {
    TemplateDelegateNodesSubject(FailureMetadata failureMetadata, List<TemplateMetadata> nodes) {
      super(failureMetadata, nodes);
    }

    void definedAt(SourceLocation sourceLocation) {
      List<SourceLocation> locations = new ArrayList<>();
      for (TemplateMetadata delegateNode : actual()) {
        locations.add(delegateNode.getSourceLocation());
      }
      Truth.assertThat(locations).contains(sourceLocation);
    }
  }
}
