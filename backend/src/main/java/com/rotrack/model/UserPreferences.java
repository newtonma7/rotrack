package com.rotrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String timezone;

    @Column(name = "daily_work_goal_minutes")
    private Integer dailyWorkGoalMinutes;

    @Column(name = "share_study_summary", nullable = false)
    private boolean shareStudySummary;

    @Column(name = "share_active_study_status", nullable = false)
    private boolean shareActiveStudyStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Integer getDailyWorkGoalMinutes() {
        return dailyWorkGoalMinutes;
    }

    public void setDailyWorkGoalMinutes(Integer dailyWorkGoalMinutes) {
        this.dailyWorkGoalMinutes = dailyWorkGoalMinutes;
    }

    public boolean isShareStudySummary() {
        return shareStudySummary;
    }

    public void setShareStudySummary(boolean shareStudySummary) {
        this.shareStudySummary = shareStudySummary;
    }

    public boolean isShareActiveStudyStatus() {
        return shareActiveStudyStatus;
    }

    public void setShareActiveStudyStatus(boolean shareActiveStudyStatus) {
        this.shareActiveStudyStatus = shareActiveStudyStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
