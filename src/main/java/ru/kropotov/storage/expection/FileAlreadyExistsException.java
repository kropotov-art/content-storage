package ru.kropotov.storage.expection;

public class FileAlreadyExistsException extends RuntimeException {
    
    private final String duplicateType;
    
    public FileAlreadyExistsException(String message, String duplicateType) {
        super(message);
        this.duplicateType = duplicateType;
    }
    
    public String getDuplicateType() {
        return duplicateType;
    }
}