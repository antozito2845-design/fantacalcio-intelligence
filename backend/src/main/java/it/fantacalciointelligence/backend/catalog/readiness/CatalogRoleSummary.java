package it.fantacalciointelligence.backend.catalog.readiness;

public record CatalogRoleSummary(
        int P,
        int D,
        int C,
        int A
) {
    public int total() {
        return P + D + C + A;
    }
}
