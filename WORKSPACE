##
 # Copyright 2013 Google Inc.
 #
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at
 #
 #     http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 #
 # NOTE: THIS IS A WORK IN PROGRESS AND IS NOT EXPECTED TO WORK AS WE HAVE
 # NO CONTINUOUS INTEGRATION.
##

workspace(name = "com_google_closure_templates")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

# TODO(yannic): Switch to Bazel-native dependencies when possible.

# public domain
java_import_external(
    name = "aopalliance",
    jar_sha256 = "0addec670fedcd3f113c5c8091d783280d23f75e3acb841b61a9cdb079376a08",
    jar_urls = [
        "https://repo1.maven.org/maven2/aopalliance/aopalliance/1.0/aopalliance-1.0.jar",
        "http://maven.ibiblio.org/maven2/aopalliance/aopalliance/1.0/aopalliance-1.0.jar",
    ],
)

# MIT License
java_import_external(
    name = "args4j",
    jar_sha256 = "457557186c22180be7a8ae577e05a8084a864f7fd1fb53a3dbcbecb25fda3ce5",
    jar_urls = [
        "https://repo1.maven.org/maven2/args4j/args4j/2.0.23/args4j-2.0.23.jar",
    ],
)

# Apache 2.0
#
# AutoValue 1.6+ shades Guava, Auto Common, and JavaPoet. That's OK
# because none of these jars become runtime dependencies.
java_import_external(
    name = "com_google_auto_value",
    extra_build_file_content = """
java_plugin(
    name = "AutoAnnotationProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    tags = ["annotation=com.google.auto.value.AutoAnnotation;genclass=${package}.AutoAnnotation_${outerclasses}${classname}_${methodname}"],
    deps = [":processor"],
)

java_plugin(
    name = "AutoOneOfProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    tags = ["annotation=com.google.auto.value.AutoValue;genclass=${package}.AutoOneOf_${outerclasses}${classname}"],
    deps = [":processor"],
)

java_plugin(
    name = "AutoValueProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    tags = ["annotation=com.google.auto.value.AutoValue;genclass=${package}.AutoValue_${outerclasses}${classname}"],
    deps = [":processor"],
)

java_library(
    name = "com_google_auto_value",
    exported_plugins = [
        ":AutoAnnotationProcessor",
        ":AutoOneOfProcessor",
        ":AutoValueProcessor",
    ],
    exports = ["@com_google_auto_value_annotations"],
)
""",
    generated_rule_name = "processor",
    jar_sha256 = "ed5f69ef035b5367f1f0264f843b988908e36e155845880b29d79b7c8855adf3",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/auto/value/auto-value/1.6.5/auto-value-1.6.5.jar",
    ],
    exports = ["@com_google_auto_value_annotations"],
)

# Apache 2.0
java_import_external(
    name = "com_google_auto_value_annotations",
    default_visibility = ["@com_google_auto_value//:__pkg__"],
    jar_sha256 = "3677f725f5b1b6cd6a4cc8aa8cf8f5fd2b76d170205cbdc3e9bfd9b58f934b3b",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/auto/value/auto-value-annotations/1.6.5/auto-value-annotations-1.6.5.jar",
    ],
    neverlink = True,
)

# Apache 2.0
java_import_external(
    name = "com_google_common_html_types",
    jar_sha256 = "78b6baa2ecc56435dc0ae88c57f442bd2d07127cb50424d400441ddccc45ea24",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/common/html/types/types/1.0.7/types-1.0.7.jar",
    ],
    deps = [
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
        "@com_google_jsinterop_annotations",
        "@com_google_protobuf//:protobuf_java",
        "@javax_annotation_jsr250_api",
    ],
)

# BSD 3-clause
java_import_external(
    name = "com_google_code_findbugs_jsr305",
    jar_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
    jar_urls = [
        "http://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
        "http://maven.ibiblio.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_code_gson",
    jar_sha256 = "2d43eb5ea9e133d2ee2405cc14f5ee08951b8361302fdd93494a3a997b508d32",
    jar_urls = [
        "http://repo1.maven.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar",
        "http://maven.ibiblio.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_errorprone_error_prone_annotations",
    jar_sha256 = "03d0329547c13da9e17c634d1049ea2ead093925e290567e1a364fd6b1fc7ff8",
    jar_urls = [
        "http://maven.ibiblio.org/maven2/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar",
        "http://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_guava_guava",
    jar_sha256 = "6db0c3a244c397429c2e362ea2837c3622d5b68bb95105d37c21c36e5bc70abf",
    jar_urls = [
        "http://repo1.maven.org/maven2/com/google/guava/guava/25.1-jre/guava-25.1-jre.jar",
        "http://maven.ibiblio.org/maven2/com/google/guava/guava/25.1-jre/guava-25.1-jre.jar",
    ],
    deps = [
        "@com_google_code_findbugs_jsr305",
        "@com_google_errorprone_error_prone_annotations",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_code_gson",
    jar_sha256 = "2d43eb5ea9e133d2ee2405cc14f5ee08951b8361302fdd93494a3a997b508d32",
    jar_urls = [
        "http://repo1.maven.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar",
        "http://maven.ibiblio.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_inject_extensions_guice_multibindings",
    jar_sha256 = "592773a4c745cc87ba37fa0647fed8126c7e474349c603c9f229aa25d3ef5448",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/inject/extensions/guice-multibindings/4.1.0/guice-multibindings-4.1.0.jar",
        "http://maven.ibiblio.org/maven2/com/google/inject/extensions/guice-multibindings/4.1.0/guice-multibindings-4.1.0.jar",
    ],
    deps = [
        "@com_google_guava_guava",
        "@com_google_inject_guice",
        "@javax_inject",
    ],
)

# Apache 2.0
java_import_external(
    name = "com_google_inject_guice",
    jar_sha256 = "9b9df27a5b8c7864112b4137fd92b36c3f1395bfe57be42fedf2f520ead1a93e",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/google/inject/guice/4.1.0/guice-4.1.0.jar",
        "http://maven.ibiblio.org/maven2/com/google/inject/guice/4.1.0/guice-4.1.0.jar",
    ],
    deps = [
        "@aopalliance",
        "@com_google_code_findbugs_jsr305",
        "@com_google_guava_guava",
        "@javax_inject",
        "@org_ow2_asm",
    ],
)

# GWT Terms
java_import_external(
    name = "com_google_jsinterop_annotations",
    jar_sha256 = "b2cc45519d62a1144f8cd932fa0c2c30a944c3ae9f060934587a337d81b391c8",
    jar_urls = [
        "http://maven.ibiblio.org/maven2/com/google/jsinterop/jsinterop-annotations/1.0.1/jsinterop-annotations-1.0.1.jar",
        "https://repo1.maven.org/maven2/com/google/jsinterop/jsinterop-annotations/1.0.1/jsinterop-annotations-1.0.1.jar",
    ],
)

# ICU License
java_import_external(
    name = "com_ibm_icu_icu4j",
    jar_sha256 = "759d89ed2f8c6a6b627ab954be5913fbdc464f62254a513294e52260f28591ee",
    jar_urls = [
        "https://repo1.maven.org/maven2/com/ibm/icu/icu4j/57.1/icu4j-57.1.jar",
        "http://maven.ibiblio.org/maven2/com/ibm/icu/icu4j/57.1/icu4j-57.1.jar",
    ],
)

# CDDL 1.0
java_import_external(
    name = "javax_annotation_jsr250_api",
    jar_sha256 = "a1a922d0d9b6d183ed3800dfac01d1e1eb159f0e8c6f94736931c1def54a941f",
    jar_urls = [
        "https://repo1.maven.org/maven2/javax/annotation/jsr250-api/1.0/jsr250-api-1.0.jar",
        "http://maven.ibiblio.org/maven2/javax/annotation/jsr250-api/1.0/jsr250-api-1.0.jar",
    ],
)

# Apache 2.0
java_import_external(
    name = "javax_inject",
    jar_sha256 = "91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff",
    jar_urls = [
        "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
        "http://maven.ibiblio.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
    ],
)

# BSD 3-clause
java_import_external(
    name = "org_ow2_asm",
    jar_sha256 = "b88ef66468b3c978ad0c97fd6e90979e56155b4ac69089ba7a44e9aa7ffe9acf",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/ow2/asm/asm/7.0/asm-7.0.jar",
        "https://repo1.maven.org/maven2/org/ow2/asm/asm/7.0/asm-7.0.jar",
    ],
)

# BSD 3-clause
java_import_external(
    name = "org_ow2_asm_analysis",
    jar_sha256 = "e981f8f650c4d900bb033650b18e122fa6b161eadd5f88978d08751f72ee8474",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/ow2/asm/asm-analysis/7.0/asm-analysis-7.0.jar",
        "https://repo1.maven.org/maven2/org/ow2/asm/asm-analysis/7.0/asm-analysis-7.0.jar",
    ],
    exports = [
        "@org_ow2_asm",
        "@org_ow2_asm_tree",
    ],
)

# BSD 3-clause
java_import_external(
    name = "org_ow2_asm_commons",
    jar_sha256 = "fed348ef05958e3e846a3ac074a12af5f7936ef3d21ce44a62c4fa08a771927d",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/ow2/asm/asm-commons/7.0/asm-commons-7.0.jar",
        "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/7.0/asm-commons-7.0.jar",
    ],
    exports = ["@org_ow2_asm_tree"],
)

# BSD 3-clause
java_import_external(
    name = "org_ow2_asm_tree",
    jar_sha256 = "cfd7a0874f9de36a999c127feeadfbfe6e04d4a71ee954d7af3d853f0be48a6c",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/ow2/asm/asm-tree/7.0/asm-tree-7.0.jar",
        "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/7.0/asm-tree-7.0.jar",
    ],
    exports = ["@org_ow2_asm"],
)

# BSD 3-clause
java_import_external(
    name = "org_ow2_asm_util",
    jar_sha256 = "75fbbca440ef463f41c2b0ab1a80abe67e910ac486da60a7863cbcb5bae7e145",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/ow2/asm/asm-util/7.0/asm-util-7.0.jar",
        "https://repo1.maven.org/maven2/org/ow2/asm/asm-util/7.0/asm-util-7.0.jar",
    ],
    exports = [
        "@org_ow2_asm_analysis",
        "@org_ow2_asm_tree",
    ],
)

# MIT-style license
java_import_external(
    name = "org_json",
    jar_sha256 = "0aaf0e7e286ece88fb60b9ba14dd45c05a48e55618876efb7d1b6f19c25e7a29",
    jar_urls = [
        "https://mirror.bazel.build/repo1.maven.org/maven2/org/json/json/20160212/json-20160212.jar",
        "https://repo1.maven.org/maven2/org/json/json/20160212/json-20160212.jar",
        "http://maven.ibiblio.org/maven2/org/json/json/20160212/json-20160212.jar",
    ],
)

# Apache 2.0
http_archive(
    name = "rules_java",
    sha256 = "52423cb07384572ab60ef1132b0c7ded3a25c421036176c0273873ec82f5d2b2",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/0.1.0/rules_java-0.1.0.tar.gz",
    ],
)
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
rules_java_dependencies()
rules_java_toolchains()

# Apache 2.0
http_archive(
    name = "rules_proto",
    sha256 = "57001a3b33ec690a175cdf0698243431ef27233017b9bed23f96d44b9c98242f",
    strip_prefix = "rules_proto-9cd4f8f1ede19d81c6d48910429fe96776e567b1",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/9cd4f8f1ede19d81c6d48910429fe96776e567b1.tar.gz",
    ],
)
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")
rules_proto_dependencies()
rules_proto_toolchains()

# Apache 2.0
http_archive(
    name = "xyz_yannic_rules_javacc",
    sha256 = "737471214ad1e95398dab51659c75e0016ec6a2e9b93317b1a20753ce9d3e91f",
    strip_prefix = "xyz_yannic_rules_javacc-2c5682e28ce9ff92b672f6bfcea244d627289ca0",
    urls = [
        "https://github.com/bazel-packages/xyz_yannic_rules_javacc/archive/2c5682e28ce9ff92b672f6bfcea244d627289ca0.tar.gz",
    ],
)
load("@xyz_yannic_rules_javacc//javacc:repositories.bzl", "rules_javacc_dependencies", "rules_javacc_toolchains")
rules_javacc_dependencies()
rules_javacc_toolchains()
