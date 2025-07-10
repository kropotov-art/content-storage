package ru.kropotov.storage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import ru.kropotov.storage.domain.model.Tag;
import ru.kropotov.storage.domain.repository.TagRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {
    
    private final TagRepository tagRepository;

    public void ensureExists(List<String> tagNames) {
        if (isEmpty(tagNames)) {
            return;
        }

        List<Tag> existingTags = tagRepository.findByNameIn(tagNames);
        Set<String> existingTagNames = existingTags.stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());

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

    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    public boolean tagExists(String tagName) {
        if (tagName == null || tagName.trim().isEmpty()) {
            return false;
        }
        List<Tag> found = tagRepository.findByNameIn(List.of(tagName.trim().toLowerCase()));
        return !found.isEmpty();
    }
}