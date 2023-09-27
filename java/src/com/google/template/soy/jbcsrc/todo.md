# jbcsrc TODO

This page lists a number of 'ideas' for how jbcsrc should work, could change and
also mistakes that were made. A lot of the orientation is from things learned
while working on b/172970101 (reduce jbcsrc gencode size)

[TOC]

## use lambdas for `let`/`param` subclasses

Currently we generate inner classes to support lazy evaluation of `let` and
`param` constructs. It would be better if we could model them as static
functions and use builtin lambda support to write the class

This would reduce class size and simplify the compiler since the JVM would now
be responsible for the 'capture' logic instead of the `LazyClosureCompiler`.

## Rethink logging implementation

The velogging in jbcsrc is highly dependent on content being streamed, this is
very useful because streaming is a core usecase, but leads to logs getting
dropped when streaming can't be maintained (e.g. due to a `+` operator).

We should instead create a way to track logs in the core SoyValue objects so
even if we need to render into a buffer we can still keep things around.

## Find more ways to not compile `let`/`param`/`msg` placeholders to inner classes

Each of these constructs by default generates an inner class to support lazy
evaluation, there are a number of special cases for each but there could be
more. For example, maybe for `param` values that are really desugared
`@attribute` params we should eagerly evaluate in the caller. This would
simplify code on both sides of the call with no loss in streaming since we are
guaranteed that it will be rendered very early in the callee.
