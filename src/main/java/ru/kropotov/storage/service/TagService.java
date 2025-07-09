package ru.kropotov.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import ru.kropotov.storage.config.properties.StorageProperties;
import ru.kropotov.storage.domain.model.Tag;
import ru.kropotov.storage.domain.repository.TagRepository;

import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {
    
    private final TagRepository tagRepository;
    private final StorageProperties storageProperties;

    public void validateAndNormalizeTags(List<String> tags) {
        if (isEmpty(tags)) {
            return;
        }
        
        if (tags.size() > storageProperties.getMaxTags()) {
            throw new IllegalArgumentException("Maximum " + storageProperties.getMaxTags() + " tags allowed");
        }

        for (int i = 0; i < tags.size(); i++) {
            String normalized = tags.get(i).trim().toLowerCase();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Tag cannot be empty after trimming");
            }
            tags.set(i, normalized);
        }

        List<String> distinctTags = tags.stream().distinct().toList();
        tags.clear();
        tags.addAll(distinctTags);
    }

    public void createMissingTags(List<String> tagNames) {
        if (isEmpty(tagNames)) {
            return;
        }

        List<Tag> existingTags = tagRepository.findByNameIn(tagNames);
        List<String> existingTagNames = existingTags.stream()
                .map(Tag::getName)
                .toList();

        List<Tag> newTags = tagNames.stream()
                .filter(tagName -> !existingTagNames.contains(tagName))
                .map(Tag::new)
                .toList();
        
        if (!newTags.isEmpty()) {
            try {
                tagRepository.saveAll(newTags);
                log.debug("Created {} new tags", newTags.size());
            } catch (DuplicateKeyException e) {
                log.debug("Some tags already existed during parallel insertion");
            }
        }
    }
}