package it.fantacalciointelligence.backend.availability;

import java.util.Locale;

import it.fantacalciointelligence.backend.common.BadRequestException;

public enum PlayerAvailabilityStatus {

    AVAILABLE,
    PROBABLE,
    DOUBTFUL,
    INJURED,
    SUSPENDED,
    UNAVAILABLE;

    public static PlayerAvailabilityStatus fromValue(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    "Lo stato di disponibilità è obbligatorio"
            );
        }

        try {
            return PlayerAvailabilityStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        }
        catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Stato di disponibilità non valido: "
                            + value
            );
        }
    }

    public boolean preventsSelection() {
        return this == INJURED
                || this == SUSPENDED
                || this == UNAVAILABLE;
    }
}