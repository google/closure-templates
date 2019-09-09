load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "passes",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/internal/proto",
        "//java/src/com/google/template/soy/shared/internal/gencode",
        "//java/src/com/google/template/soy/soytree",
        "//java/src/com/google/template/soy/types",
        "@com_google_guava_guava",
    ],
)
