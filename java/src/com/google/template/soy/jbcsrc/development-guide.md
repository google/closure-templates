[TOC]

# Development Guide

This is intended to help new developers dive into the codebase and contribute
features.

For high level strategy, see [the design doc](README.md).

## Package design

The jbcsrc implementation is split across several packages.

*   `com.google.template.soy.jbcsrc`

    The base package contains the core compiler implementation and the public
    compiler entry point: `BytecodeCompiler`

*   `com.google.template.soy.jbcsrc.runtime`

    This package contains helper classes and utility routines that are only
    accessed by the generated code. A lot of the `jbcsrc` runtime is actually
    defined in other soy packages (such as
    `com.google.template.soy.shared.internal.SharedRuntime` or
    `com.google.template.soy.shared.data`), when it is possible to share with
    Tofu. So this package is really intended for jbcsrc specific functionality.

*   `com.google.template.soy.jbcsrc.api`

    This package contains the public api for rendering jbcsrc compiled templates
    via the `SoySauce` class.

*   `com.google.template.soy.jbcsrc.shared`

    This package contains functionality that is shared by the compiler and
    runtime, but is meant to be private to soy.

## A Bytecode Primer

### Definitions

*   Runtime/operand stack - The implicit runtime stack of the virtual machine
*   Basic Block - a sequence of instructions with no branches
*   Frame - the set of values on the runtime stack and in the local variable
    table

### Stack Machine

Java bytecode is a 'stack machine', this means that all operators perform some
kind of operations on an implicit runtime stack. For example, the opcode `IADD`
will pop 2 `int` values off the runtime stack and put their sum back onto the
stack. Bytecode also has a local variable table that can be used to store named
(well, indexed) values. However, there are no opcodes that can operate on local
variables (other that pushing them onto the stack).

### Types

The Java bytecode type system mostly maps to the normal Java type system with a
notable exception that `boolean` is not a type, `boolean` is just any integral
type (think `C`), `0 == false` and `non zero == true`. However, types impose
some important constraints on how bytecode can be written. Every value on the
runtime stack has a type associated, as well as every local variable. At any
instruction there is a notion of an active 'frame'. For a basic block, frames
are trivial to maintain and update. However, for branch target instructions
(jump locations), the frames at each branching instruction must be identical.
The jvm has dedicated opcodes to manipulate frame state for branch targets. For
the most part ASM will calculate these, but errors due to inconsistent frames
are easy to introduce (and do not have pretty failures).

### Opcodes

A good resource for figuring out what each jvm opcode does is from the [java
spec](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html)

### ASM Tips

To actually generate bytecode we use [`asm`](http://asm.ow2.org/), a bytecode
generation/manipulation library.

The asm library has a lot of benefits. It is small, blazing fast, and well
supported on the asm@ow2.org mailing list. However, documentation and error
checking are pretty light, so when you make a mistake the errors produced can be
inscrutable. To cope with this there is a global switch in `Flags.DEBUG`
controlled by a JVM system property. If this is enabled a number of additional
invariant checking operations are enabled both in `asm` and on the jbcsrc side.

NOTE: this isn't enabled all the time because it slows the compiler down by a
factor of 2.

For other asm errors here is what I know:

*   `NegativeArraySizeException` is thrown from `MethodWriter.visitMaxes`. The
    most likely explanation is that you have accidentally popped too many items
    off the runtime stack. Look for stray POP instructions, or using the wrong
    branch instruction (`IF_IEQ` pops two ints, `IFEQ` pops one).

## Core compiler types

The compiler is built out of a few core types that are combined together in many
different ways. Each of these types has a 2 phase lifecycle.

### `Expression` and `SoyExpression`

These two types are the core abstractions. An `Expression` represents a
'strategy' for producing a value on the runtime stack. Each expression has a
type and some additional metadata.

`SoyExpression` is a subtype of `Expression` that produces a 'soy value'. A soy
value is any value that relates directly to a user expression. For example,
`$foo` would be represented by a `SoyExpression` that accesses the parameter
`foo`. In addition to all the normal things you can do with `Expression`s,
`SoyExpression`s have helpers for boxing and unboxing values into `SoyValue`
objects as well as the standard set of soy type coercions (coerce to bool,
coerce to string).

### `Statement`

A Statement is strategy for generating a sequence of code that maintains the
invariant, that the runtime stack is the same at the end of the statement as the
beginning. This is useful for representing operations like `{print ...}`
commands.

### `BytecodeProducer`

`BytecodeProducer` is the common supertype of `Expression` and `Statement`, and
provides some useful debugging features. For example, calling `.toString` on any
bytecode producer will print out a human readable `javap` style trace of the
code.

`BytecodeProducer` also enforces an invariant that disallows creating new
instances of `BytecodeProducer` while generating code. This forces a model where
all `BytecodeProducer` objects are created and wired together and then we do a
single pass to generate code.

### `MethodRef`, `ConstructorRef`, `FieldRef`

These are analgous to the object `java.lang.reflect.Method`,
`java.lang.reflect.Constructor`, and `java.lang.reflect.Field` (and in fact
there are adapters for converting from the reflect objects to these). They are
optimized for generating expressions that invoke the methods or access the
fields, and it is also possible to construct these object for methods that do
not yet exist (because we haven't generated them yet).

## `CodeBuilder`

`CodeBuilder` is a wrapper around the asm `GeneratorAdapter` object that is
largely similar but disables some undesirable features (like renumbering local
variables).

## Compiler parts

### BytecodeCompiler

The main entry point.

### TemplateCompiler

This builds a new class for every soy template and then invokes
`SoyNodeCompiler` to generate the code for the `CompiledTemplate.render` method.

### SoyNodeCompiler

This handles transforming every `SoyNode` into a `Statement`. See
`visitRawTextNode` for a simple example.

### ExpressionCompiler

This handles transforming every `ExprNode` into a `SoyExpression`. See
`visitLessThanOpNode` for a relatively simple example.
