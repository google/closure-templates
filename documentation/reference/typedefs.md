# Type Aliases

[TOC]

## type {#type}

The `type` command creates a named type alias.

```soy
{type MY_UNION = string | number /}
```

The second form defines a type alias that can be used both within its file and
in other files.

```soy
{export type SHARED_TYPE = type_expr /}
```

Type aliases must be defined outside of templates, above any template
declarations in a file.

### Using a type alias

A type alias can be used anywhere that expects a type expression.

```soy
{template helloConst}
  {@param p: SHARED_TYPE}
  ...
{/template}
```

To use an exported (public) type alias from another file you must import either
the type symbol or the file namespace.

```soy
import {SHARED_TYPE} from 'path/to/file_1.soy';
import * as ns from 'path/to/file_2.soy';

{template concat}
  {@param p1: SHARED_TYPE}
  {@param p2: ns.SHARED_TYPE}
  ...
{/template}
```
