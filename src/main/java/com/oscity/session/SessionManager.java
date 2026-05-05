package com.oscity.session;

import java.time.LocalDateTime;
import java.util.UUID;

public class SessionManager {
    private String sessionId;
    private String mode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean isActive;
    private int hintsUsed;
    private int wrongAnswers;
    private SessionStats stats;

    public SessionManager() {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.startTime = LocalDateTime.now();
        this.isActive = false;
        this.hintsUsed = 0;
        this.wrongAnswers = 0;
        this.stats = new SessionStats();
    }

    public void startSession(String mode) {
        this.mode = mode;
        this.startTime = LocalDateTime.now();
        this.isActive = true;
    }

    public void endSession() {
        this.endTime = LocalDateTime.now();
        this.isActive = false;
    }

    public void recordHintUsed() {
        this.hintsUsed++;
    }

    public void recordWrongAnswer() {
        this.wrongAnswers++;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMode() {
        return mode;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public int getWrongAnswers() {
        return wrongAnswers;
    }

    public SessionStats getStats() {
        return stats;
    }

    public void startNewSession() {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.startTime = LocalDateTime.now();
        this.isActive = false;
        this.hintsUsed = 0;
        this.wrongAnswers = 0;
        this.stats = new SessionStats();
    }

    public long getSessionDurationSeconds() {
        if (endTime == null) return -1;
        return java.time.temporal.ChronoUnit.SECONDS.between(startTime, endTime);
    }
}
