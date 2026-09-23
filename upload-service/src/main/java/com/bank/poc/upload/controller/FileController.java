package com.bank.poc.upload.controller;

import com.bank.poc.upload.dto.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final RestTemplate restTemplate;
    private final Map<String, FileRecord> fileStore = new ConcurrentHashMap<>();

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public FileController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // POST /api/files/upload — Upload a file and trigger processing
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "description", required = false, defaultValue = "") String description) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        }

        String fileId = "FILE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (accountId == null || accountId.isBlank()) {
            accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        // Save file to disk
        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String storedName = System.currentTimeMillis() + "-" + file.getOriginalFilename();
            File dest = new File(dir, storedName);
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("Failed to save file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed", "details", e.getMessage()));
        }

        FileRecord record = new FileRecord(
                fileId, accountId, file.getOriginalFilename(),
                file.getSize(), file.getContentType(), description
        );
        fileStore.put(fileId, record);

        log.info("📤 File uploaded: {} ({})", record.getFileName(), fileId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("fileId", fileId);
        response.put("accountId", accountId);
        response.put("fileName", record.getFileName());
        response.put("fileSize", record.getFileSize());
        response.put("mimeType", record.getMimeType());
        response.put("uploadStatus", "uploaded");
        response.put("message", "File uploaded successfully. Processing initiated.");

        // Trigger processing asynchronously
        CompletableFuture.runAsync(() -> triggerProcessing(record));

        // Return response with X-Correlation-ID header
        HttpHeaders headers = new HttpHeaders();
        headers.set(CORRELATION_HEADER, fileId);
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    // GET /api/files/{fileId} — Get file info & current status
    @GetMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> getFile(@PathVariable String fileId) {
        FileRecord file = fileStore.get(fileId);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found", "fileId", fileId));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", file.getFileId());
        response.put("accountId", file.getAccountId());
        response.put("fileName", file.getFileName());
        response.put("fileSize", file.getFileSize());
        response.put("mimeType", file.getMimeType());
        response.put("description", file.getDescription());
        response.put("uploadStatus", file.getUploadStatus());
        response.put("processingStatus", file.getProcessingStatus());
        response.put("validationStatus", file.getValidationStatus());
        response.put("overallStatus", file.getOverallStatus());
        response.put("uploadedAt", file.getUploadedAt());
        response.put("updatedAt", file.getUpdatedAt());
        return ResponseEntity.ok(response);
    }

    // GET /api/files — List all uploaded files
    @GetMapping
    public ResponseEntity<Map<String, Object>> listFiles() {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileRecord f : fileStore.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", f.getFileId());
            item.put("accountId", f.getAccountId());
            item.put("fileName", f.getFileName());
            item.put("fileSize", f.getFileSize());
            item.put("overallStatus", f.getOverallStatus());
            item.put("uploadedAt", f.getUploadedAt());
            files.add(item);
        }
        return ResponseEntity.ok(Map.of("files", files, "total", files.size()));
    }

    // PATCH /api/files/{fileId}/status — Update file status (called internally)
    @PatchMapping("/{fileId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String fileId, @RequestBody Map<String, String> updates) {
        FileRecord file = fileStore.get(fileId);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found", "fileId", fileId));
        }

        if (updates.containsKey("processingStatus")) file.setProcessingStatus(updates.get("processingStatus"));
        if (updates.containsKey("validationStatus")) file.setValidationStatus(updates.get("validationStatus"));
        if (updates.containsKey("overallStatus")) file.setOverallStatus(updates.get("overallStatus"));
        file.setUpdatedAt(Instant.now().toString());

        return ResponseEntity.ok(Map.of("success", true, "fileId", fileId, "updatedFields", updates.keySet()));
    }

    private void triggerProcessing(FileRecord record) {
        try {
            log.info("⚙️ Triggering processing for {}...", record.getFileId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fileId", record.getFileId());
            payload.put("accountId", record.getAccountId());
            payload.put("fileName", record.getFileName());
            payload.put("fileSize", record.getFileSize());
            payload.put("mimeType", record.getMimeType());

            // Pass correlation ID via header
            HttpHeaders headers = new HttpHeaders();
            headers.set(CORRELATION_HEADER, record.getFileId());
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    "http://localhost:3002/api/process", request, Map.class);

            if (result != null) {
                record.setProcessingStatus((String) result.getOrDefault("processingStatus", "unknown"));
                String valStatus = (String) result.getOrDefault("validationStatus", record.getValidationStatus());
                record.setValidationStatus(valStatus);
                String overall = (String) result.getOrDefault("overallStatus", record.getOverallStatus());
                record.setOverallStatus(overall);
                record.setUpdatedAt(Instant.now().toString());
                log.info("✅ Processing complete for {}: {}", record.getFileId(), record.getProcessingStatus());
            }
        } catch (Exception e) {
            log.error("❌ Processing failed for {}: {}", record.getFileId(), e.getMessage());
            record.setProcessingStatus("failed");
            record.setOverallStatus("failed");
            record.setUpdatedAt(Instant.now().toString());
        }
    }
}
