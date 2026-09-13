package com.wyx.travelagent.knowledge.repository;

import com.wyx.travelagent.knowledge.admin.KnowledgeAdministrationPersistence;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentStatus;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

/** MyBatis 管理查询行；只在 repository 包内转换为不可变端口模型。 */
final class KnowledgeAdministrationRow {
    private Long id;
    private String title;
    private String city;
    private String publisher;
    private String sourceUrl;
    private LocalDate publishedAt;
    private String documentType;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private long fileSize;
    private String sha256;
    private String status;
    private String failureReason;
    private Integer uploadedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private long chunkCount;

    KnowledgeAdministrationPersistence.DocumentRecord toRecord() {
        return new KnowledgeAdministrationPersistence.DocumentRecord(
                new KnowledgeDocument(
                        id, title, city, publisher, URI.create(sourceUrl), publishedAt,
                        KnowledgeDocumentType.valueOf(documentType), originalFilename,
                        storedFilename, contentType, fileSize, sha256,
                        KnowledgeDocumentStatus.valueOf(status), failureReason, uploadedBy,
                        createdAt, updatedAt),
                chunkCount);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDate getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long chunkCount) { this.chunkCount = chunkCount; }
}
