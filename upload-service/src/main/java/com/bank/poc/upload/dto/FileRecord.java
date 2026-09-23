package com.bank.poc.upload.dto;

import java.time.Instant;

public class FileRecord {
    private String fileId;
    private String accountId;
    private String fileName;
    private long fileSize;
    private String mimeType;
    private String description;
    private String uploadStatus;
    private String processingStatus;
    private String validationStatus;
    private String overallStatus;
    private String uploadedAt;
    private String updatedAt;

    public FileRecord() {}

    public FileRecord(String fileId, String accountId, String fileName, long fileSize, String mimeType, String description) {
        this.fileId = fileId;
        this.accountId = accountId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.description = description;
        this.uploadStatus = "uploaded";
        this.processingStatus = "pending";
        this.validationStatus = "pending";
        this.overallStatus = "in_progress";
        this.uploadedAt = Instant.now().toString();
        this.updatedAt = this.uploadedAt;
    }

    // Getters and Setters
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(String uploadStatus) { this.uploadStatus = uploadStatus; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
