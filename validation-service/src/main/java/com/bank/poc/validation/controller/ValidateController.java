package com.bank.poc.validation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/validate")
public class ValidateController {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(ValidateController.class);

    private final Map<String, Map<String, Object>> validationStore = new ConcurrentHashMap<>();
    private final Map<String, String> fileValidationMap = new ConcurrentHashMap<>();

    // POST /api/validate — Validate processing results (called by Processing Service)
    @PostMapping
    public ResponseEntity<Map<String, Object>> validateFile(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> request) {
        String fileId = (String) request.get("fileId");
        String processId = (String) request.get("processId");
        String accountId = (String) request.get("accountId");
        String fileName = (String) request.get("fileName");
        String processingType = (String) request.get("processingType");
        Object recordsObj = request.get("recordsProcessed");
        int recordsProcessed = recordsObj instanceof Number ? ((Number) recordsObj).intValue() : 0;

        if (fileId == null || processId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "fileId and processId are required"));
        }

        String validationId = "VAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("🔍 Validation started for {} (process: {})", fileId, processId);

        // Run validation checks
        List<Map<String, String>> checks = new ArrayList<>();
        boolean hasErrors = false;
        boolean hasWarnings = false;

        // Check 1: File integrity
        checks.add(Map.of(
                "checkName", "file_integrity",
                "status", "passed",
                "message", "File integrity verified successfully"
        ));

        // Check 2: Record count
        if (recordsProcessed > 0) {
            checks.add(Map.of(
                    "checkName", "record_count",
                    "status", "passed",
                    "message", recordsProcessed + " records processed and validated"
            ));
        } else {
            checks.add(Map.of(
                    "checkName", "record_count",
                    "status", "failed",
                    "message", "No records were processed"
            ));
            hasErrors = true;
        }

        // Check 3: Format compliance (15% warning rate)
        boolean formatWarning = Math.random() < 0.15;
        checks.add(Map.of(
                "checkName", "format_compliance",
                "status", formatWarning ? "warning" : "passed",
                "message", formatWarning
                        ? "Minor format inconsistencies detected in some records"
                        : "All records comply with expected format"
        ));
        if (formatWarning) hasWarnings = true;

        // Check 4: Data quality (5% failure rate)
        boolean dataQualityFail = Math.random() < 0.05;
        checks.add(Map.of(
                "checkName", "data_quality",
                "status", dataQualityFail ? "failed" : "passed",
                "message", dataQualityFail
                        ? "Critical data quality issues found: duplicate entries detected"
                        : "Data quality checks passed"
        ));
        if (dataQualityFail) hasErrors = true;

        // Check 5: Compliance
        checks.add(Map.of(
                "checkName", "compliance_check",
                "status", "passed",
                "message", "Regulatory compliance requirements met"
        ));

        // Determine overall status
        String validationStatus;
        if (hasErrors) {
            validationStatus = "failed";
        } else if (hasWarnings) {
            validationStatus = "passed_with_warnings";
        } else {
            validationStatus = "passed";
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map<String, String> check : checks) {
            if ("failed".equals(check.get("status"))) errors.add(check.get("message"));
            if ("warning".equals(check.get("status"))) warnings.add(check.get("message"));
        }

        long checksPassed = checks.stream().filter(c -> "passed".equals(c.get("status"))).count();
        long checksFailed = checks.stream().filter(c -> "failed".equals(c.get("status"))).count();
        long checksWarning = checks.stream().filter(c -> "warning".equals(c.get("status"))).count();

        String result;
        if ("passed".equals(validationStatus)) {
            result = "File processed and validated successfully";
        } else if ("passed_with_warnings".equals(validationStatus)) {
            result = "File processed with minor warnings";
        } else {
            result = "File validation failed - review required";
        }

        Map<String, Object> validationRecord = new LinkedHashMap<>();
        validationRecord.put("validationId", validationId);
        validationRecord.put("fileId", fileId);
        validationRecord.put("processId", processId);
        validationRecord.put("accountId", accountId);
        validationRecord.put("fileName", fileName);
        validationRecord.put("processingType", processingType);
        validationRecord.put("validationStatus", validationStatus);
        validationRecord.put("checksPerformed", checks.size());
        validationRecord.put("checksPassed", checksPassed);
        validationRecord.put("checksFailed", checksFailed);
        validationRecord.put("checksWarning", checksWarning);
        validationRecord.put("checks", checks);
        validationRecord.put("errors", errors);
        validationRecord.put("warnings", warnings);
        validationRecord.put("result", result);
        validationRecord.put("validatedAt", Instant.now().toString());

        validationStore.put(validationId, validationRecord);
        fileValidationMap.put(fileId, validationId);

        String icon = "passed".equals(validationStatus) ? "✅" :
                "passed_with_warnings".equals(validationStatus) ? "⚠️" : "❌";
        log.info("{} Validation {} for {}", icon, validationStatus, fileId);

        // Return response with X-Correlation-ID header
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CORRELATION_HEADER, correlationId != null ? correlationId : fileId);
        return new ResponseEntity<>(validationRecord, responseHeaders, HttpStatus.OK);
    }

    // GET /api/validate/{validationId} — Get validation details
    @GetMapping("/{validationId}")
    public ResponseEntity<Map<String, Object>> getValidation(@PathVariable String validationId) {
        Map<String, Object> record = validationStore.get(validationId);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Validation record not found", "validationId", validationId));
        }
        return ResponseEntity.ok(record);
    }

    // GET /api/validate/status/{fileId} — End-to-end status for a file
    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileStatus(@PathVariable String fileId) {
        String validationId = fileValidationMap.get(fileId);
        if (validationId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "No validation record found for this file",
                            "fileId", fileId,
                            "suggestion", "The file may still be processing or has not been uploaded yet"
                    ));
        }

        Map<String, Object> record = validationStore.get(validationId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("validationId", record.get("validationId"));
        response.put("processId", record.get("processId"));
        response.put("accountId", record.get("accountId"));
        response.put("fileName", record.get("fileName"));
        response.put("validationStatus", record.get("validationStatus"));
        response.put("result", record.get("result"));
        response.put("checksPerformed", record.get("checksPerformed"));
        response.put("checksPassed", record.get("checksPassed"));
        response.put("checksFailed", record.get("checksFailed"));
        response.put("errors", record.get("errors"));
        response.put("warnings", record.get("warnings"));
        response.put("validatedAt", record.get("validatedAt"));
        return ResponseEntity.ok(response);
    }
}
