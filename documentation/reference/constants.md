# Constants

[TOC]

## const {#const}

The `const` command allows you to define immutable data that can be used across
multiple templates. The first form of the command defines a constant that is
private to the file in which it is defined.

```soy
{const CONST_NAME = const_value_expr /}
```

The second form defines a constant that can be used both within its file and in
other files.

```soy
{export const CONST_NAME = const_value_expr /}
```

Constants must be defined outside of templates, above any template declarations
in a file.

### Using a constant

To use a constant inside the Soy file in which it is defined, simply reference
it by its name.

```soy
{template helloConst}
  <p>
    My constant: {CONST_NAME}
  </p>
{/template}
```

Constant definitions may reference other constants, as long as they are defined
earlier in the Soy file.

```soy
{const HELLO = 'hello' /}
{const HELLO_WORLD = HELLO + '_world' /}
```

To use an exported (public) constant from another file you must import either
the constant symbol or the file namespace.

```soy
import {CONST_NAME} from 'path/to/file_1.soy';
import * as ns from 'path/to/file_2.soy';

{template concat}
  <p>
    {CONST_NAME} {ns.CONST2}
  </p>
{/template}
```
