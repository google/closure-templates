/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.testing;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.ProtoTypeRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import com.google.template.soy.types.SoyTypeRegistryBuilder.ProtoFqnRegistryBuilder;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnknownType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Shared utilities for unit tests.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SharedTestUtils {

  public static final String NS = "{namespace brittle.test.ns}\n";

  private SharedTestUtils() {}

  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(String soyCode) {
    return buildTestSoyFileContent(false, soyCode);
  }

  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param strictHtml Whether to use strict html mode in this namespace.
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(boolean strictHtml, String soyCode) {
    return NS + buildTestTemplateContent(strictHtml, soyCode);
  }

  public static String buildTestTemplateContent(boolean strictHtml, String soyCode) {
    String templateName = ".brittleTestTemplate";

    return String.format(
        "/** Test template. */\n" + "{template %s%s}\n" + "%s\n" + "{/template}\n",
        templateName, strictHtml ? "" : " stricthtml=\"false\"", soyCode);
  }

  /**
   * Returns a template body for the given soy expression. e.g. for the soy expression {@code $foo +
   * 2} this will return
   *
   * <pre><code>
   *   {{@literal @}param foo : ?}
   *   {$foo + 2}
   * </code></pre>
   *
   * <p>To supply types, call {@link #createTemplateBodyForExpression} directly.
   */
  public static String untypedTemplateBodyForExpression(String soyExpr) {
    return createTemplateBodyForExpression(soyExpr, ImmutableMap.of());
  }

  /** Returns a template body for the given soy expression. With type specializations. */
  public static String createTemplateBodyForExpression(
      String soyExpr, final Map<String, SoyType> typeMap) {
    ExprNode expr = SoyFileParser.parseExpression(soyExpr, ErrorReporter.exploding());
    final Set<String> loopVarNames = new HashSet<>();
    final Set<String> names = new HashSet<>();
    new AbstractExprNodeVisitor<Void>() {

      @Override
      protected void visitVarRefNode(VarRefNode node) {
        if (node.getName().startsWith("$")) {
          names.add(node.getNameWithoutLeadingDollar());
        }
      }

      @Override
      protected void visitFunctionNode(FunctionNode node) {
        switch (node.getFunctionName()) {
          case "index":
          case "isFirst":
          case "isLast":
            loopVarNames.add(((VarRefNode) node.getChild(0)).getNameWithoutLeadingDollar());
            break;
          default: // fall out
        }
        visitChildren(node);
      }

      @Override
      protected void visitExprNode(ExprNode node) {
        if (node instanceof ParentExprNode) {
          visitChildren((ParentExprNode) node);
        }
      }
    }.exec(expr);
    final StringBuilder templateBody = new StringBuilder();
    for (String varName : Sets.difference(names, loopVarNames)) {
      SoyType type = typeMap.get(varName);
      if (type == null) {
        type = UnknownType.getInstance();
      }
      templateBody.append("{@param " + varName + ": " + type + "}\n");
    }
    String contents = "{" + soyExpr + "}\n";
    for (String loopVar : loopVarNames) {
      contents = "{for $" + loopVar + " in [null]}\n" + contents + "\n{/for}";
    }
    templateBody.append(contents);
    return templateBody.toString();
  }

  /**
   * Retrieves the node within the given Soy tree indicated by the given indices to reach the
   * desired node.
   *
   * @param soyTree The Soy tree.
   * @param indicesToNode The indices to reach the desired node to retrieve. E.g. To retrieve the
   *     first child of the template, simply pass a single 0.
   * @return The desired node in the Soy tree.
   */
  public static SoyNode getNode(SoyFileSetNode soyTree, int... indicesToNode) {

    SoyNode node = soyTree.getChild(0).getChild(0); // initially set to TemplateNode
    for (int index : indicesToNode) {
      node = ((ParentSoyNode<?>) node).getChild(index);
    }
    return node;
  }

  public static void testAllTestFilesAreCovered(String dir, Class<?> clazz) throws Exception {
    testAllTestFilesAreCovered(dir, clazz, ImmutableSet.of());
  }

  public static void testAllTestFilesAreCovered(
      String dir, Class<?> clazz, Set<String> filesWithoutTestMethods) throws Exception {
    Set<String> testFiles;
    try (Stream<Path> stream = Files.list(Paths.get(dir))) {
      testFiles =
          stream
              .filter(path -> path.getFileName().toString().endsWith(".soy"))
              .map(
                  path -> {
                    String filename = path.getFileName().toString();
                    filename = filename.substring(0, filename.indexOf('.'));
                    return filename;
                  })
              .collect(toCollection(HashSet::new));
    }
    Set<String> testMethods =
        Arrays.stream(clazz.getMethods())
            .filter(method -> method.isAnnotationPresent(Test.class))
            .map(Method::getName)
            .collect(toCollection(HashSet::new));

    assertWithMessage(
            "These files are missing tests methods. Delete the files or add test methods for them.")
        .that(Sets.difference(Sets.difference(testFiles, filesWithoutTestMethods), testMethods))
        .isEmpty();
  }

  public static SoyTypeRegistry importing(GenericDescriptor... descriptors) {
    return importing(Arrays.asList(descriptors));
  }

  /**
   * Creates a type registry that will resolve every message, enum, or extension in {@code
   * descriptors} as though a corresponding import statement were included in the template source.
   */
  public static SoyTypeRegistry importing(Iterable<GenericDescriptor> descriptors) {
    SoyTypeRegistry baseTypes = SoyTypeRegistryBuilder.create();
    ProtoTypeRegistry fqnRegistry = new ProtoFqnRegistryBuilder(descriptors).build(baseTypes);
    ImmutableMap<String, String> localToFqn =
        stream(descriptors)
            .filter(
                d ->
                    d instanceof Descriptor
                        || d instanceof EnumDescriptor
                        || (d instanceof FieldDescriptor && ((FieldDescriptor) d).isExtension()))
            .collect(toImmutableMap(SharedTestUtils::localName, SharedTestUtils::fqnName));
    ImmutableSet<FileDescriptor> files =
        stream(descriptors).map(GenericDescriptor::getFile).collect(toImmutableSet());
    return new TestSoyTypeRegistry(baseTypes, localToFqn, fqnRegistry, files);
  }

  private static String localName(GenericDescriptor d) {
    if (d instanceof FieldDescriptor) {
      return Field.computeSoyName((FieldDescriptor) d);
    }
    String pkg = d.getFile().getPackage();
    return pkg.isEmpty() ? d.getFullName() : d.getFullName().substring(pkg.length() + 1);
  }

  private static String fqnName(GenericDescriptor d) {
    if (d instanceof FieldDescriptor) {
      return Field.computeSoyFullyQualifiedName((FieldDescriptor) d);
    }
    return d.getFullName();
  }

  private static class TestSoyTypeRegistry extends DelegatingSoyTypeRegistry {
    private final ImmutableMap<String, String> localToFqn;
    private final ProtoTypeRegistry fqnRegistry;
    private final ImmutableSet<FileDescriptor> files;

    public TestSoyTypeRegistry(
        SoyTypeRegistry baseTypes,
        ImmutableMap<String, String> localToFqn,
        ProtoTypeRegistry fqnRegistry,
        ImmutableSet<FileDescriptor> files) {
      super(baseTypes);
      this.localToFqn = localToFqn;
      this.fqnRegistry = fqnRegistry;
      this.files = files;
    }

    @Override
    public SoyType getType(String typeName) {
      SoyType type = super.getType(typeName);
      if (type == null) {
        String fqn = SoyTypes.localToFqn(typeName, localToFqn);
        if (fqn != null) {
          type = fqnRegistry.getProtoType(fqn);
        }
      }
      return type;
    }

    @Override
    public Identifier resolve(Identifier id) {
      String resolved = SoyTypes.localToFqn(id.identifier(), localToFqn);
      if (resolved != null) {
        return Identifier.create(resolved, id.originalName(), id.location());
      }
      return super.resolve(id);
    }

    @Override
    public ImmutableSet<FileDescriptor> getProtoDescriptors() {
      return files;
    }

    @Override
    public ProtoTypeRegistry getProtoRegistry() {
      return fqnRegistry;
    }
  }
}
