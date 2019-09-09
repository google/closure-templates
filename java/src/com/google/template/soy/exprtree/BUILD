load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "exprtree",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy:shared/internal/BuiltinFunction",
        "//java/src/com/google/template/soy:shared/restricted/SoyFunction",
        "//java/src/com/google/template/soy:shared/restricted/SoyPureFunction",
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/basetree",
        "//java/src/com/google/template/soy/plugin/restricted",
        "//java/src/com/google/template/soy/types",
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
    ],
)
