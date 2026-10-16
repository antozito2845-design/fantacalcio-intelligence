package it.fantacalciointelligence.backend.club;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubService {

    private final ClubRepository clubRepository;

    public ClubService(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> findAllActive() {
        return clubRepository
                .findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(ClubResponse::from)
                .toList();
    }
}