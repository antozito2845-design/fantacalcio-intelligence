package it.fantacalciointelligence.backend.season;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonService {

    private final SeasonRepository seasonRepository;

    public SeasonService(SeasonRepository seasonRepository) {
        this.seasonRepository = seasonRepository;
    }

    @Transactional(readOnly = true)
    public List<SeasonResponse> findAll() {
        return seasonRepository
                .findAllByOrderByStartYearDesc()
                .stream()
                .map(SeasonResponse::from)
                .toList();
    }
}