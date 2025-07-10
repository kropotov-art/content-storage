package ru.kropotov.storage.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.kropotov.storage.service.TagService;
import ru.kropotov.storage.web.dto.TagDto;
import ru.kropotov.storage.web.mapper.TagMapper;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Tag management operations")
public class TagController {

    private final TagService tagService;
    private final TagMapper tagMapper;

    @GetMapping
    @Operation(summary = "Get all tags", description = "Retrieve complete tag vocabulary")
    public ResponseEntity<List<TagDto>> getAllTags() {
        List<ru.kropotov.storage.domain.model.Tag> tags = tagService.getAllTags();
        List<TagDto> tagDtos = tags.stream()
                .map(tagMapper::toDto)
                .toList();
        return ResponseEntity.ok(tagDtos);
    }
}