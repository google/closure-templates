#!/bin/bash

# This script runs on http://go/kokoro to run all of Soy's opensource Bazel
# tests.

# Fail on any error
set -e

# Display commands being run.
set -x

# Build all binaries.
time bazel build -c opt ...

# Run all tests.
time bazel test -c opt --test_output=errors ...
