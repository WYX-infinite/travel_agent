package com.wyx.travelagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Writes deterministic, sanitized offline evaluation reports. */
public final class EvaluationReportWriter {
    public static final Path OUTPUT_DIRECTORY = Path.of("target", "evaluation");
    public static final Path JSON_REPORT = OUTPUT_DIRECTORY.resolve("travel-agent-offline-evaluation.json");
    public static final Path MARKDOWN_REPORT = OUTPUT_DIRECTORY.resolve("travel-agent-offline-evaluation.md");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void write(Report report) throws IOException {
        Objects.requireNonNull(report, "report");
        Files.createDirectories(OUTPUT_DIRECTORY);
        Files.writeString(JSON_REPORT,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toJson(report)) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.writeString(MARKDOWN_REPORT, toMarkdown(report), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> toJson(Report report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evaluationMode", "DETERMINISTIC_OFFLINE");
        root.put("datasetVersion", report.datasetVersion());
        root.put("caseCount", report.cases().size());
        root.put("passedCaseCount", report.cases().stream().filter(CaseResult::passed).count());
        root.put("metrics", report.metrics().stream().map(EvaluationReportWriter::metricJson).toList());
        root.put("cases", report.cases());
        root.put("limitations", report.limitations());
        return root;
    }

    private static Map<String, Object> metricJson(MetricResult metric) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", metric.name());
        result.put("numerator", metric.numerator());
        result.put("denominator", metric.denominator());
        result.put("formula", metric.formula());
        result.put("displayValue", metric.displayValue());
        return result;
    }

    private static String toMarkdown(Report report) {
        StringBuilder text = new StringBuilder();
        text.append("# Travel Agent deterministic offline evaluation\n\n")
                .append("- Mode: `DETERMINISTIC_OFFLINE`\n")
                .append("- Dataset: `").append(report.datasetVersion()).append("`\n")
                .append("- Cases: ").append(report.cases().size()).append("\n")
                .append("- Passed: ").append(report.cases().stream().filter(CaseResult::passed).count()).append("\n\n")
                .append("## Metrics\n\n")
                .append("| Metric | Numerator | Denominator | Formula | Value |\n")
                .append("|---|---:|---:|---|---:|\n");
        report.metrics().forEach(metric -> text.append("| ").append(metric.name()).append(" | ")
                .append(metric.numerator()).append(" | ").append(metric.denominator()).append(" | ")
                .append(metric.formula()).append(" | ").append(metric.displayValue()).append(" |\n"));
        text.append("\n## Case results\n\n")
                .append("| ID | Scenario | Expected | Actual | Error | Passed | Failure reason |\n")
                .append("|---|---|---|---|---|---|---|\n");
        report.cases().forEach(result -> text.append("| ").append(result.id()).append(" | ")
                .append(result.scenarioType()).append(" | ").append(result.expectedTaskStatus()).append(" | ")
                .append(result.actualTaskStatus()).append(" | ")
                .append(result.actualErrorCode() == null ? "-" : result.actualErrorCode()).append(" | ")
                .append(result.passed()).append(" | ")
                .append(result.failureReason() == null ? "-" : result.failureReason()).append(" |\n"));
        text.append("\n## Limitations\n\n");
        report.limitations().forEach(item -> text.append("- ").append(item).append('\n'));
        return text.toString();
    }

    public record Report(String datasetVersion, List<MetricResult> metrics,
                         List<CaseResult> cases, List<String> limitations) {
        public Report {
            Objects.requireNonNull(datasetVersion, "datasetVersion");
            metrics = List.copyOf(metrics);
            cases = List.copyOf(cases);
            limitations = List.copyOf(limitations);
        }
    }

    public record MetricResult(String name, int numerator, int denominator, String formula) {
        public MetricResult {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(formula, "formula");
            if (numerator < 0 || denominator < 0) {
                throw new IllegalArgumentException("invalid metric counts");
            }
        }

        public String displayValue() {
            if (denominator == 0) return "N/A";
            if ("averageRepairAttempts".equals(name)) {
                return String.format(java.util.Locale.ROOT, "%.2f", numerator * 1.0 / denominator);
            }
            return String.format(java.util.Locale.ROOT, "%.2f%%", numerator * 100.0 / denominator);
        }
    }

    public record CaseResult(String id, String scenarioType, String expectedTaskStatus,
                             String actualTaskStatus, String expectedErrorCode, String actualErrorCode,
                             boolean passed, String failureReason, int repairAttempts) {
        public CaseResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(scenarioType, "scenarioType");
            Objects.requireNonNull(expectedTaskStatus, "expectedTaskStatus");
            Objects.requireNonNull(actualTaskStatus, "actualTaskStatus");
            if (repairAttempts < 0) throw new IllegalArgumentException("repairAttempts");
        }
    }
}
