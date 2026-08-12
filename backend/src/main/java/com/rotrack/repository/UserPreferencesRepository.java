package com.rotrack.repository;

import com.rotrack.model.UserPreferences;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
}
