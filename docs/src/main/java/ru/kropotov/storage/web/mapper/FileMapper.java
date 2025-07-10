package ru.kropotov.storage.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.kropotov.storage.domain.model.File;
import ru.kropotov.storage.web.dto.FileDto;

@Mapper
public interface FileMapper {
    
    @Mapping(target = "downloadUrl", expression = "java(\"/d/\" + file.getId() + \"/\" + file.getDownloadSecret())")
    FileDto toDto(File file);
}