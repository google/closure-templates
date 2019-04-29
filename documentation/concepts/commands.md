# Commands

[TOC]

## What are Soy commands?

Commands are a very high level concept that take many forms. They are
instructions that you give the template compiler to a) create templates, and b)
add custom logic to templates. Soy commands are anything placed within tags,
which are delimited by curly brackets: `{}`.

## What types of commands are there?

Commands represent everything from control flow:

```soy
{if <expression>}
  ...
{elseif <expression>}
  ...
{else}
  ...
{/if}
```

To the templates themselves:

```soy
{template <TEMPLATE_NAME>}
  ...
{/template}
```

The syntax for commands depends on the specific command. You can find
explanations and example usages of all commands in the Soy
[reference guide](../reference/index.md).

## Special case: `Print` command

The most common command, other than perhaps `template`, is `print`, which prints
a simple value. Because `print` is so common, it is the implied command for all
tags that don't explicitly begin with a command.

For example, given the parameter `$userName` you can write either `{print
$userName}` or the short form `{$userName}`.

### Print directives

Because `print` is so common, there are also a whole category of sub-commands
dedicated to it called print directives. Print directives are post-processing on
the output of a print command. For a full list and explanation, see the [print
directive reference page](../reference/print-directives.md).

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="expressions.md">CONTINUE >></a></section>

<br>
