#!/bin/bash

# This script runs on http://go/kokoro to run all of Soy's opensource Bazel
# tests.

# Fail on any error
set -e

# Display commands being run.
set -x

# Install bazel.
use_bazel.sh 4.1.0
command -v bazel
bazel version

cd "${KOKORO_ARTIFACTS_DIR}/github/closure-templates"

# Build all binaries.
time bazel build -c opt ...

# Run all tests.
time bazel test -c opt --test_output=errors ...
