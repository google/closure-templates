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

package com.google.template.soy.jbcsrc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.OBJECT;

import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.jbcsrc.restricted.Flags;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.Names;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * A subclass of {@link ClassWriter} that allows us to specialize {@link
 * ClassWriter#getCommonSuperClass} for compiler generated types as well as set common defaults for
 * all classwriters used by {@code jbcsrc}.
 */
public final class SoyClassWriter extends ClassVisitor {
  /** Returns a new SoyClassWriter for writing a new class of the given type. */
  public static Builder builder(TypeInfo type) {
    return new Builder(type);
  }

  public static final class Builder {
    private final TypeInfo type;
    private int access = Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
    private TypeInfo baseClass = OBJECT;
    private List<String> interfaces = new ArrayList<>();
    private String fileName; // optional

    private Builder(TypeInfo type) {
      this.type = checkNotNull(type);
    }

    /**
     * Set the access permissions on the generated class. The default is package private and {@code
     * final}.
     *
     * @param access The access permissions, a bit mask composed from constants like {@link
     *     Opcodes#ACC_PUBLIC}
     */
    public Builder setAccess(int access) {
      this.access = access;
      return this;
    }

    /** Sets the base class for this type. The default is {@code Object}. */
    public Builder extending(TypeInfo baseClass) {
      this.baseClass = checkNotNull(baseClass);
      return this;
    }

    /** Adds an {@code interface} to the class. */
    public Builder implementing(TypeInfo typeInfo) {
      interfaces.add(typeInfo.internalName());
      return this;
    }

    public Builder sourceFileName(String fileName) {
      this.fileName = checkNotNull(fileName);
      return this;
    }

    public SoyClassWriter build() {
      return new SoyClassWriter(new Writer(), this);
    }
  }

  private final Writer writer;
  private final TypeInfo typeInfo;
  private int numFields;
  private int numDetachStates;

  private SoyClassWriter(Writer writer, Builder builder) {
    super(writer.api(), Flags.DEBUG ? new CheckClassAdapter(writer, false) : writer);
    this.writer = writer;
    this.typeInfo = builder.type;
    super.visit(
        Opcodes.V1_7,
        builder.access,
        builder.type.internalName(),
        null /* not generic */,
        builder.baseClass.internalName(),
        builder.interfaces.toArray(new String[builder.interfaces.size()]));
    if (builder.fileName != null) {
      super.visitSource(
          builder.fileName,
          // No JSR-45 style source maps, instead we write the line numbers in the normal locations.
          null);
    }
  }

  /** Sets the number of 'detach states' needed by the compiled class. */
  public void setNumDetachStates(int numDetachStates) {
    checkArgument(numDetachStates >= 0);
    this.numDetachStates = numDetachStates;
  }

  /** @deprecated Don't call visitSource(), SoyClassWriter calls it for you during construction. */
  @Deprecated
  @Override
  public void visitSource(String source, String debug) {
    throw new UnsupportedOperationException(
        "Don't call visitSource(), SoyClassWriter calls it for you");
  }

  /** @deprecated Don't call visit(), SoyClassWriter calls it for you during construction. */
  @Deprecated
  @Override
  public void visit(int v, int a, String n, String s, String b, String[] i) {
    throw new UnsupportedOperationException("Don't call visit(), SoyClassWriter calls it for you");
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String desc, String signature, Object value) {
    numFields++;
    return super.visitField(access, name, desc, signature, value);
  }

  /** Returns the bytecode of the class that was build with this class writer. */
  public ClassData toClassData() {
    return ClassData.create(typeInfo, writer.toByteArray(), numFields, numDetachStates);
  }

  private static final class Writer extends ClassWriter {
    private static final String PROTO_EXTENDABLE_BUILDER =
        Type.getInternalName(GeneratedMessage.ExtendableBuilder.class);

    Writer() {
      super(COMPUTE_FRAMES | COMPUTE_MAXS);
    }

    int api() {
      return api;
    }

    @Override
    protected String getCommonSuperClass(String left, String right) {
      // TODO(lukes): we know the names and superclasses of all the classes we generate prior to
      // this method being called, so we could build a smarter system just by building up that
      // graph as we generate classes.
      // similarly for soy.data classes we could consider adding special cases. The fact that this
      // class falls back to doing classpath lookups makes it hard to test in a unit test :(
      boolean leftIsGenerated = left.startsWith(Names.INTERNAL_CLASS_PREFIX);
      boolean rightIsGenerated = right.startsWith(Names.INTERNAL_CLASS_PREFIX);
      if (!leftIsGenerated && !rightIsGenerated) {
        // Test for proto extendable builders.  We need to calculate common baseclasses for these
        // since we generate calls to methods on ExtendableBuilder
        if ((left.equals(PROTO_EXTENDABLE_BUILDER) && right.endsWith("$Builder"))
            || (right.equals(PROTO_EXTENDABLE_BUILDER) && left.endsWith("$Builder"))) {
          return PROTO_EXTENDABLE_BUILDER;
        }
        // The only cases that should occur here should be about our runtime types (like StringData)
        try {
          return super.getCommonSuperClass(left, right);
        } catch (RuntimeException re) {
          throw new RuntimeException(
              "unable to calculate common base class of: " + left + " and " + right, re);
        }
      }
      // The only reason a generated type will get compared to a non-generated type is if they
      // happen to share a local variable slot.  This is because ASM doesn't know that the old
      // variable has gone 'out of scope' and a new one entered it.  The best advice from the asm
      // community so far has been 'just return object', so that is what we are doing
      // See http://mail.ow2.org/wws/arc/asm/2015-06/msg00008.html
      return OBJECT.internalName();
    }
  }
}
