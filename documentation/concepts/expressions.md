# Expressions

[TOC]

## What are Soy expressions?

Expressions are another high level Soy concept. They are written within
templates to reference template data, variables, or compute intermediate values.

## What types of expressions are there?

TIP: For a complete list, see the [reference section](../reference/index).

### Literals

Literals are just that: literal expressions of values like integers (`100`,
`0x64`) or booleans (`true`, `false`). The Soy compiler evaluates them as they
are.

### Variables

Variables are things like parameters (`$myParam`), globals, and injected data.

### Operators

Mathematical operators like `+`, or `>`, and data access operators for protos,
among others.

NOTE: In Soy, operators follow a strict
[order of precedence](../reference/expressions.md#precedence).

### Function calls

More about this in the next concepts section.

## Where are expressions used?

Soy uses a language-neutral expression syntax, and expressions show up in lots
of different places in Soy. The most common place is in print commands, but they
are also used in [if commands](../reference/control-flow.md#if),
[let declarations](../reference/let.md), and many other places.

For example:

```soy
{template .myTemplate}
  {@param p: ?}
  {if $p > 2}
    {call .bar}{param p : $p == 3 ? 'a' : 'b' /}{/call}
  {/if}
{/template}
```

In the above example, we can see a parameter `p` being used in several
expressions for several different commands.

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="functions-plugins.md">CONTINUE >></a></section>

<br>
