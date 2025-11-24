/*
 * Copyright 2025 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.error;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.compilermetrics.Metric;
import com.google.template.soy.compilermetrics.Metrics;
import com.google.template.soy.diagnosticcategory.Category;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reports forge metrics for Soy compilation errors. */
public final class MetricReporter {
  List<Metric> metrics = new ArrayList<>();
  public static final String METRICS_FILE_NAME = "SoyCompiler.pb";
  public static final String CUSTOM_METRICS_DIR_ENV_NAME = "CUSTOM_METRICS_DIR";

  public MetricReporter() {}

  /**
   * Reports an unexpected diagnostic error to the custom metrics directory specified by the
   * CUSTOM_metricsDir environment variable.
   *
   * @param message The message of the error.
   */
  private void reportUnexpectedDiagnosticError(String message) {
    metrics.add(createUnexpectedDiagnosticMetric(message));
  }

  /**
   * Reports metrics for a list of Soy errors.
   *
   * @param errors The soy errors to convert to metrics and report.
   */
  public void reportMetrics(List<SoyError> errors) {
    if (errors.isEmpty()) {
      return;
    }
    this.metrics.addAll(errors.stream().map(this::createMetric).collect(toImmutableList()));
  }

  /**
   * Reports metrics for a Soy compilation exception.
   *
   * @param sce The Soy compilation exception to convert to metrics.
   */
  public void reportMetrics(SoyCompilationException sce) {
    reportMetrics(sce.getErrors());
    reportUnexpectedDiagnosticError(sce.getMessage());
  }

  /**
   * Reports metrics for a Soy internal compiler exception.
   *
   * @param sce The Soy internal compiler exception to convert to metrics.
   */
  public void reportMetrics(SoyInternalCompilerException sce) {
    reportMetrics(sce.getErrors());
    reportUnexpectedDiagnosticError(sce.getMessage());
  }

  /**
   * Creates a metric for an unexpected diagnostic error.
   *
   * @param message The message of the error.
   */
  private static Metric createUnexpectedDiagnosticMetric(String message) {
    return Metric.newBuilder()
        .setImpression(Impression.MAIN_UNEXPECTED_DIAGNOSTIC)
        .setCategory(Category.ERROR)
        .setMessageText(message)
        .build();
  }

  /**
   * Creates a metric from a Soy error.
   *
   * @param soyError The Soy error to convert to a metric.
   */
  private Metric createMetric(SoyError soyError) {
    Impression impression = soyError.errorKind().getImpression();
    Preconditions.checkArgument(
        impression != null,
        "SoyErrorKind does not have a impression. Please add an impression to the Impressions enum"
            + " in soy_compiler_metrics.proto, and set the impression in the SoyErrorKind.");

    SourceLocation location = soyError.location();
    boolean locationIsKnown = location.isKnown();
    ByteSpan byteSpan = location.getByteSpan();
    int start = byteSpan.getStart();
    int end = byteSpan.getEnd();
    int length;
    if (byteSpan.isKnown()) {
      length = end - start + 1;
    } else if (locationIsKnown && location.isSingleLine()) {
      length = location.getLength();
    } else {
      length = 0;
    }
    String fileName = location.getFileName();
    if (fileName == null) {
      fileName = "";
    }

    return Metric.newBuilder()
        .setFileName(fileName)
        .setStart(start)
        .setLength(length)
        .setImpression(impression)
        .setCategory(soyError.isWarning() ? Category.WARNING : Category.ERROR)
        .setMessageText(soyError.message())
        .setCode(0)
        .build();
  }

  /**
   * Emits the metrics file to the forge metrics directory specified by the CUSTOM_METRICS_DIR
   * environment variable.
   */
  public void emit() {
    if (metrics.isEmpty()) {
      return;
    }

    String metricsDir = System.getenv(CUSTOM_METRICS_DIR_ENV_NAME);

    if (Strings.isNullOrEmpty(metricsDir)) {
      return;
    }
    File customMetricsDir = new File(metricsDir);
    if (!customMetricsDir.isDirectory() || !customMetricsDir.canWrite()) {
      return;
    }
    Path path = Path.of(metricsDir, METRICS_FILE_NAME);
    try (FileOutputStream fos = new FileOutputStream(path.toString(), true)) {
      Metrics.newBuilder().addAllMetric(metrics).build().writeTo(fos);
    } catch (IOException e) {
      throw new AssertionError("Failed to write metrics file", e);
    }
  }
}
