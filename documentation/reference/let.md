# Let command


## let {#let}

Syntax for arbitrary values:

```soy
{let $<IDENTIFIER>: <EXPRESSION> /}
```

Syntax for rendering to string ("block form"):

```soy
{let $<IDENTIFIER> kind="IDENTIFIER_TYPE"}...{/let}
```

`let` defines a name for an intermediate value. The name is defined only within
the immediate code block containing the `let` command, and the value of the name
is not modifiable.

`kind` is an optional attribute, and the default is "html". See the security
guide for other [content kinds](../dev/security.md#content_kinds).

You might use `let` because you need to reuse the intermediate value multiple
times, or you need to print a rendered value using a directive, or you feel it
improves the readability of your code.

Example:

```soy
{let $isEnabled: $isAaa and not $isBbb and $ccc == $ddd + $eee /}
{if $isEnabled and $isXxx}
  ...
{elseif not $isEnabled and $isYyy}
  ...
{/if}
```

The second syntax form listed above renders the contents of the `let` block to a
string, including applying autoescaping. It is sometimes needed, but should be
used sparingly.
