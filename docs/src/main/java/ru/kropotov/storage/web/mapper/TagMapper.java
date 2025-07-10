package ru.kropotov.storage.web.mapper;

import org.mapstruct.Mapper;
import ru.kropotov.storage.domain.model.Tag;
import ru.kropotov.storage.web.dto.TagDto;

@Mapper(componentModel = "spring")
public interface TagMapper {
    
    TagDto toDto(Tag tag);
    
    Tag toEntity(TagDto tagDto);
}