package com.bank.poc.processing.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(ProcessController.class);

    private final RestTemplate restTemplate;
    private final Map<String, Map<String, Object>> processStore = new ConcurrentHashMap<>();

    public ProcessController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // POST /api/process — Process a file (called by Upload Service)
    @PostMapping
    public ResponseEntity<Map<String, Object>> processFile(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> request) {
        String fileId = (String) request.get("fileId");
        String accountId = (String) request.get("accountId");
        String fileName = (String) request.get("fileName");
        Object fileSizeObj = request.get("fileSize");
        long fileSize = fileSizeObj instanceof Number ? ((Number) fileSizeObj).longValue() : 0;

        if (fileId == null || fileName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "fileId and fileName are required"));
        }

        String processId = "PROC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String startedAt = Instant.now().toString();

        log.info("⚙️ Processing started for {} ({})", fileId, fileName);

        // Determine processing type based on file extension
        String ext = fileName.contains(".") ?
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "unknown";

        String processingType;
        switch (ext) {
            case "csv": case "xlsx": case "xls":
                processingType = "data_extraction"; break;
            case "pdf":
                processingType = "document_parsing"; break;
            case "jpg": case "jpeg": case "png": case "gif":
                processingType = "image_analysis"; break;
            case "xml": case "json":
                processingType = "structured_data"; break;
            default:
                processingType = "generic";
        }

        // Simulate processing delay (1-3 seconds)
        long processingDelay = 1000 + (long) (Math.random() * 2000);
        try {
            Thread.sleep(processingDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate results — 10% failure rate
        int recordsProcessed = (int) (Math.random() * 500) + 10;
        boolean isFailure = Math.random() < 0.1;
        String processingStatus = isFailure ? "failed" : "completed";
        String errorMessage = isFailure ? "Corrupted file format detected" : null;

        Map<String, Object> processRecord = new LinkedHashMap<>();
        processRecord.put("processId", processId);
        processRecord.put("fileId", fileId);
        processRecord.put("accountId", accountId);
        processRecord.put("fileName", fileName);
        processRecord.put("fileSize", fileSize);
        processRecord.put("processingType", processingType);
        processRecord.put("processingStatus", processingStatus);
        processRecord.put("recordsProcessed", isFailure ? 0 : recordsProcessed);
        processRecord.put("processingDuration", processingDelay + "ms");
        processRecord.put("errorMessage", errorMessage);
        processRecord.put("startedAt", startedAt);
        processRecord.put("completedAt", Instant.now().toString());

        processStore.put(processId, processRecord);

        log.info("{} Processing {} for {}: {}, {} records",
                isFailure ? "❌" : "✅", processingStatus, fileId, processingType, recordsProcessed);

        // If processing succeeded, trigger validation
        Map<String, Object> validationResult = null;
        if (!isFailure) {
            try {
                Map<String, Object> valPayload = new LinkedHashMap<>();
                valPayload.put("fileId", fileId);
                valPayload.put("processId", processId);
                valPayload.put("accountId", accountId);
                valPayload.put("fileName", fileName);
                valPayload.put("processingType", processingType);
                valPayload.put("recordsProcessed", recordsProcessed);
                valPayload.put("processingStatus", processingStatus);

                // Forward correlation ID via header
                HttpHeaders valHeaders = new HttpHeaders();
                valHeaders.set(CORRELATION_HEADER, correlationId != null ? correlationId : fileId);
                valHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> valRequest = new HttpEntity<>(valPayload, valHeaders);

                @SuppressWarnings("unchecked")
                Map<String, Object> valResponse = restTemplate.postForObject(
                        "http://localhost:3003/api/validate", valRequest, Map.class);
                validationResult = valResponse;
            } catch (Exception e) {
                log.error("❌ Validation call failed for {}: {}", fileId, e.getMessage());
                validationResult = Map.of("validationStatus", "error", "error", e.getMessage());
            }
        }

        // Build response
        Map<String, Object> response = new LinkedHashMap<>(processRecord);
        response.put("validationStatus", validationResult != null ?
                validationResult.getOrDefault("validationStatus", "pending") :
                (isFailure ? "skipped" : "pending"));
        response.put("validationId", validationResult != null ?
                validationResult.get("validationId") : null);

        String overallStatus;
        if (isFailure) {
            overallStatus = "failed";
        } else if (validationResult != null && "passed".equals(validationResult.get("validationStatus"))) {
            overallStatus = "completed";
        } else if (validationResult != null && "failed".equals(validationResult.get("validationStatus"))) {
            overallStatus = "completed_with_errors";
        } else {
            overallStatus = validationResult != null && "passed_with_warnings".equals(validationResult.get("validationStatus"))
                    ? "completed" : "in_progress";
        }
        response.put("overallStatus", overallStatus);

        // Return response with X-Correlation-ID header
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CORRELATION_HEADER, correlationId != null ? correlationId : fileId);
        return new ResponseEntity<>(response, responseHeaders, HttpStatus.OK);
    }

    // GET /api/process/{processId} — Get processing details
    @GetMapping("/{processId}")
    public ResponseEntity<Map<String, Object>> getProcessRecord(@PathVariable String processId) {
        Map<String, Object> record = processStore.get(processId);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Process record not found", "processId", processId));
        }
        return ResponseEntity.ok(record);
    }

    // GET /api/process — List all processing records
    @GetMapping
    public ResponseEntity<Map<String, Object>> listRecords() {
        List<Map<String, Object>> records = new ArrayList<>(processStore.values());
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }
}
