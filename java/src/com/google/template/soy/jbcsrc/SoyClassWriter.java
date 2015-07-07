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

package com.google.template.soy.jbcsrc;

import com.google.template.soy.jbcsrc.api.Names;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * A subclass of {@link ClassWriter} that allows us to specialize 
 * {@link ClassWriter#getCommonSuperClass} for compiler generated types as well as set common 
 * defaults for all classwriters used by {@code jbcsrc}.
 */
final class SoyClassWriter extends ClassVisitor {
  private static final String OBJECT_NAME = Type.getInternalName(Object.class);
  private final Writer writer;

  SoyClassWriter() {
    this(new Writer());
  }

  private SoyClassWriter(Writer writer) {
    super(writer.api(), new CheckClassAdapter(writer, false));
    this.writer = writer;
  }

  /** Returns the bytecode of the class that was build with this class writer. */
  byte[] toByteArray() {
    return writer.toByteArray();
  }

  private static final class Writer extends ClassWriter {
    Writer() {
      super(COMPUTE_FRAMES | COMPUTE_MAXS);
    }

    int api() {
      return api;
    }

    @Override protected String getCommonSuperClass(String left, String right) {
      boolean leftIsGenerated = left.startsWith(Names.INTERNAL_CLASS_PREFIX);
      boolean rightIsGenerated = right.startsWith(Names.INTERNAL_CLASS_PREFIX);
      if (!leftIsGenerated & !rightIsGenerated) {
        return super.getCommonSuperClass(left, right);
      }
      // The only reason a generated type will get compared to a non-generated type is if they
      // happen to share a local variable slot.  This is because ASM doesn't know that the old
      // variable has gone 'out of scope' and a new one entered it.  The best advice from the asm
      // community so far has been 'just return object', so that is what we are doing
      // See http://mail.ow2.org/wws/arc/asm/2015-06/msg00008.html
      return OBJECT_NAME;
    }
  }
}

