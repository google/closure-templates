load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "xliffmsgplugin",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy",
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/error",
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
    ],
)
