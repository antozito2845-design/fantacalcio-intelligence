package it.fantacalciointelligence.backend.player;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.*;

@Entity
@Table(
    name = "players",
    indexes = {
        @Index(
            name = "idx_players_normalized_name",
            columnList = "normalized_name"
        )
    }
)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "nationality_code", length = 2)
    private String nationalityCode;

    @Column(name = "preferred_foot", length = 10)
    private String preferredFoot;

    @Column(name = "height_cm")
    private Short heightCm;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Player() {
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getNationalityCode() {
        return nationalityCode;
    }

    public String getPreferredFoot() {
        return preferredFoot;
    }

    public Short getHeightCm() {
        return heightCm;
    }

    public boolean isActive() {
        return active;
    }
}