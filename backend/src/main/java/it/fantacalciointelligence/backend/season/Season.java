package it.fantacalciointelligence.backend.season;

import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 9)
    private String code;

    @Column(name = "start_year", nullable = false)
    private Short startYear;

    @Column(name = "end_year", nullable = false)
    private Short endYear;

    @Column(name = "current_season", nullable = false)
    private boolean currentSeason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Season() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Short getStartYear() {
        return startYear;
    }

    public Short getEndYear() {
        return endYear;
    }

    public boolean isCurrentSeason() {
        return currentSeason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}