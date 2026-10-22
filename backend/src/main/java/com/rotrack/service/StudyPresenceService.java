package com.rotrack.service;

import com.rotrack.dto.StudyPresenceDTO;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.repository.StudyPresenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPresenceService {
    private final StudyPresenceRepository repository;

    public StudyPresenceService(StudyPresenceRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true, timeout = 5)
    public StudyPresenceDTO get(UUID viewer, String username) {
        var result = repository.project(viewer, UsernameValidator.canonicalize(username));
        if (result.isEmpty()) throw new ResourceNotFoundException("Study presence not found");
        return new StudyPresenceDTO(result.getFirst());
    }
}
