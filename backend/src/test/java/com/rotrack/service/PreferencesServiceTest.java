package com.rotrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rotrack.dto.PreferencesDTO;
import com.rotrack.model.UserPreferences;
import com.rotrack.repository.UserPreferencesRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PreferencesServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private final UserPreferencesRepository repository = org.mockito.Mockito.mock(UserPreferencesRepository.class);
    private final PreferencesService service = new PreferencesService(repository);

    @Test
    void returnsPrivateDefaultsWhenPreferencesRowIsMissing() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        PreferencesDTO result = service.getPreferences(USER_ID);

        assertEquals(null, result.timezone());
        assertEquals(null, result.dailyWorkGoalMinutes());
        assertEquals(false, result.shareStudySummary());
        assertEquals(false, result.shareActiveStudyStatus());
    }

    @Test
    void updatesOnlyTheAuthenticatedUsersRow() {
        UserPreferences preferences = preferences(USER_ID);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(preferences));
        when(repository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreferencesDTO result = service.updatePreferences(
                USER_ID,
                "America/New_York",
                120,
                true,
                false
        );

        assertEquals("America/New_York", result.timezone());
        assertEquals(120, result.dailyWorkGoalMinutes());
        assertEquals(true, result.shareStudySummary());
        assertEquals(false, result.shareActiveStudyStatus());
        verify(repository).findById(USER_ID);
        verify(repository).save(preferences);
    }

    @Test
    void rejectsInvalidSavedTimezone() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(USER_ID, "Not/A_Zone", null, false, false)
        );
    }

    @Test
    void rejectsInvalidDailyGoal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(USER_ID, "UTC", 0, false, false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePreferences(USER_ID, "UTC", 1441, false, false)
        );
    }

    private UserPreferences preferences(UUID userId) {
        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(userId);
        return preferences;
    }
}
