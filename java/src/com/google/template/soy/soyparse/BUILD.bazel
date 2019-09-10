load("@xyz_yannic_rules_javacc//javacc:defs.bzl", "javacc_java_library")

javacc_java_library(
    name = "soyparse",
    srcs = glob([
        "*.java",
        "*.jj",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/basetree",
        "//java/src/com/google/template/soy/error",
        "//java/src/com/google/template/soy/exprtree",
        "//java/src/com/google/template/soy/soytree",
        "//java/src/com/google/template/soy/types",
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
        "@com_ibm_icu_icu4j",
    ],
)
