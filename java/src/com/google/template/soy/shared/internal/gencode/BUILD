load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gencode",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy/base",
        "@com_google_auto_value",
        "@com_google_guava_guava",
    ],
)
