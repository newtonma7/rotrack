package com.rotrack.service;

import com.rotrack.dto.PreferencesDTO;
import com.rotrack.model.UserPreferences;
import com.rotrack.repository.UserPreferencesRepository;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService {

    private final UserPreferencesRepository repository;

    public PreferencesService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    public PreferencesDTO getPreferences(UUID userId) {
        return repository.findById(userId)
                .map(this::toDto)
                .orElseGet(this::defaultPreferences);
    }

    @Transactional
    public PreferencesDTO updatePreferences(
            UUID userId,
            String timezone,
            Integer dailyWorkGoalMinutes,
            boolean shareStudySummary,
            boolean shareActiveStudyStatus
    ) {
        // Share the dashboard's IANA allowlist; preserve the user's identifier instead of silently rewriting it.
        ZoneId validatedZone = timezone == null ? null : TimeZoneValidator.parse(timezone);
        if (dailyWorkGoalMinutes != null && (dailyWorkGoalMinutes < 1 || dailyWorkGoalMinutes > 1440)) {
            throw new IllegalArgumentException("dailyWorkGoalMinutes must be between 1 and 1440");
        }

        UserPreferences preferences = repository.findById(userId).orElseGet(() -> {
            UserPreferences created = new UserPreferences();
            created.setUserId(userId);
            return created;
        });
        preferences.setTimezone(validatedZone == null ? null : timezone);
        preferences.setDailyWorkGoalMinutes(dailyWorkGoalMinutes);
        preferences.setShareStudySummary(shareStudySummary);
        preferences.setShareActiveStudyStatus(shareActiveStudyStatus);
        return toDto(repository.save(preferences));
    }

    private PreferencesDTO defaultPreferences() {
        return new PreferencesDTO(null, null, false, false);
    }

    private PreferencesDTO toDto(UserPreferences preferences) {
        return new PreferencesDTO(
                preferences.getTimezone(),
                preferences.getDailyWorkGoalMinutes(),
                preferences.isShareStudySummary(),
                preferences.isShareActiveStudyStatus()
        );
    }
}
