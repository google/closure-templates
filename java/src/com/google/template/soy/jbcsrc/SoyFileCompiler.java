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

package com.google.template.soy.jbcsrc;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.internal.InnerClasses;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;

/**
 * For each Soy file we generate a class hosting a number of static fields, methods and inner
 * classes.
 */
final class SoyFileCompiler {

  private final SoyFileNode fileNode;
  private final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  private final List<TypeWriter> writers = new ArrayList<>();

  SoyFileCompiler(
      SoyFileNode fileNode,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler) {
    this.fileNode = fileNode;
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
  }

  ImmutableList<ClassData> compile() {
    for (TemplateNode templateNode : fileNode.getTemplates()) {
      TypeWriter typeWriter = forTemplate(templateNode);
      new TemplateCompiler(
              templateNode,
              typeWriter.writer(),
              typeWriter.fields(),
              typeWriter.innerClasses(),
              javaSourceFunctionCompiler)
          .compile();
    }
    return writers.stream().flatMap(TypeWriter::close).collect(toImmutableList());
  }

  TypeWriter forTemplate(TemplateNode node) {
    // If the template is in a file whose namespace is not known to be unique, generate it into its
    // own class to avoid ODR violations.
    if (NamespaceExemptions.isKnownDuplicateNamespace(node.getParent().getNamespace())) {
      TypeWriter writer = TypeWriter.create(node);
      writers.add(writer);
      return writer;
    } else {
      if (writers.isEmpty()) {
        writers.add(TypeWriter.create(node.getParent()));
      }
      return writers.get(0);
    }
  }

  @AutoValue
  abstract static class TypeWriter {
    static TypeWriter create(SoyFileNode node) {
      TypeInfo fileType =
          TypeInfo.createClass(Names.javaClassNameFromSoyNamespace(node.getNamespace()));
      return create(fileType, node);
    }

    static TypeWriter create(TemplateNode node) {
      TypeInfo fileType =
          TypeInfo.createClass(Names.javaClassNameFromSoyTemplateName(node.getTemplateName()));
      return create(fileType, node.getParent());
    }

    static TypeWriter create(TypeInfo type, SoyFileNode node) {
      FieldManager fields = new FieldManager(type);
      InnerClasses innerClasses = new InnerClasses(type);
      SoyClassWriter writer =
          SoyClassWriter.builder(type)
              .setAccess(Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL)
              .sourceFileName(node.getFileName())
              .build();
      return new AutoValue_SoyFileCompiler_TypeWriter(writer, fields, innerClasses);
    }

    abstract SoyClassWriter writer();

    abstract FieldManager fields();

    abstract InnerClasses innerClasses();

    Stream<ClassData> close() {
      innerClasses().registerAllInnerClasses(writer());
      fields().defineFields(writer());
      fields().defineStaticInitializer(writer());
      writer().visitEnd();

      return Stream.concat(
          Stream.of(writer().toClassData()), innerClasses().getInnerClassData().stream());
    }
  }
}
