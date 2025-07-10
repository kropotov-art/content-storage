package ru.kropotov.storage.infra.dto;

public record UploadResult(String key, String sha256, long size) {
}