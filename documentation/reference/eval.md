# Eval

The `{eval}` command takes a single expression, and evaluates it for side
effects only. It does not print anything to the output.

Useful in conjunction with the `checkNotNull` or `throw` functions.

```soy
{eval checkNotNull($foo)}
```
