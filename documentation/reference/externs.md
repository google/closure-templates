# Externs

[TOC]

## extern {#extern}

The `extern` command allows you to define custom functions for use in Soy
templates. Implementing an external function is an advanced topic covered in
[Creating an External Function](../dev/externs.md).

Syntax:

```soy
{[export] extern <function_name>: <function_signature>}
  <implementation_info>
{/extern}
```

where:

*   `export` is optional. If present, the function can be called from any Soy
    file. If omitted, the function can be called only from the file it is
    defined in.

*   `<function_signature>` is of the form:

    ```soy
    (<param_name>: <param_type>[, <param_name>: <param_type>...]) => <return_type>
    ```

*   `<implementation_info>` is [`{javaimpl}`](#javaimpl), [`{jsimpl}`](#jsimpl),
    or both.

For example:

```soy
{export extern trim: (s: string) => string}...{/extern}
```

### Java implementation {#javaimpl}

Syntax:

```soy
{javaimpl class="<java_class>" method="<method_name>"
    params="<param_types>" return="<return_type>"
    type="<method_type>" /}
```

where:

*   `<java_class>` is the fully qualified name of the Java class containing the
    implementation.

*   `<method_name>` is the name of the method to invoke on the class.

*   `<param_types>` is a comma separated list of Java types. Each type must be a
    fully qualified Java class name or the name of a Java primitive.

*   `<return_type>` is the return type of the method.

*   `<method_type>` is one of `static`, `instance`, `interface`, or
    `static_interface`. Optional. `static` is the default. See
    [Creating an External Function](../dev/externs.md) for more information.

### JavaScript implementation {#jsimpl}

Syntax:

```soy
{jsimpl namespace="<js_namespace>" function="<function_name>" /}
```

where:

*   `<js_namespace>` is the JavaScript namespace containing the implementation.

*   `<function_name>` is the name of the function in `<js_namespace>` containing
    the implementation.

### Python implementation

The Python host language is not supported by external functions at this time. If
you require a Python implementation for your custom function you must use a
[plugin](../dev/plugins).

### Calling an external function

To call an external function inside the Soy file in which it is defined, simply
reference it by its name.

```soy
{template externExample}
  {trim('hello ')}
{/template}
```

To use an exported external function from another Soy file you must import
either its symbol or the file namespace.

```soy
import {aFunct} from 'path/to/file_1.soy';
import * as ns from 'path/to/file_2.soy';

{template concat}
  <p>
    {aFunct(1)} {ns.anotherFunct(2)}
  </p>
{/template}
```
