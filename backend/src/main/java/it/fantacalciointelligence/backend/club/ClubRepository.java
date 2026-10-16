package it.fantacalciointelligence.backend.club;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findAllByActiveTrueOrderByNameAsc();
}