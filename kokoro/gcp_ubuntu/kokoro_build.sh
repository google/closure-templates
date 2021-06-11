#!/bin/bash

# Fail on any error.
set -e

${KOKORO_ARTIFACTS_DIR}/git/closure-templates/opensource/kokoro/build.sh
