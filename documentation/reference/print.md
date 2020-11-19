# Print


[TOC]

## print {#print}

Syntax (without directives):

```soy
{<expression>}
{print <expression>}
```

With directives:

```soy
{<expression>|<directive1>|<directive2>|...}
{print <expression>|<directive1>|<directive2>|...}
```

The `print` command is the most common command in Soy, so it merits a short
syntax in which you can omit the command name `print`. When Soy encounters a
`print` tag, it simply inserts the result of the expression,
[coerced to a string](coercions.md#string) if necessary, into the rendered
output.

<p class="note">NOTE: The command name `print` is optional.</p>

### Print directives

Print directives are post-processing on the output of a `print` command. For a
list of print directives, see the [Print Directives](print-directives.md)
chapter.

For example,

```soy
{$someUserContent|cleanHtml}
```

This applies the `cleanHtml` print directive. `|cleanHtml` removes unsafe HTML
tags which can be useful for preserving safe tags in user content while not
providing an XSS attack surface.

#### Passing parameters to print directives

Some print directives allow for parameters to be passed, for example
`|cleanHtml` can accept a number of extra tags that it can allow. For example:

```soy
{$someUserContent|cleanHtml:'ul','li'}
```

Here we apply the same directive as above but we are passing 'ul' and 'li' as
addition tag names that are considered safe.
