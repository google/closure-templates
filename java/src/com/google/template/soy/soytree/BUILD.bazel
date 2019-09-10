load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "soytree",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy:shared/internal/DelTemplateSelector",
        "//java/src/com/google/template/soy:shared/restricted/SoyFunction",
        "//java/src/com/google/template/soy:shared/restricted/SoyPrintDirective",
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/basetree",
        "//java/src/com/google/template/soy/error",
        "//java/src/com/google/template/soy/exprtree",
        "//java/src/com/google/template/soy/types",
        "//src/main/protobuf:template_metadata_java_proto",
        "@com_google_auto_value",
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
        "@javax_annotation_jsr250_api",
    ],
)
