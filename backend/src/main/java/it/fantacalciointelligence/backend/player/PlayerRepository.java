package it.fantacalciointelligence.backend.player;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findTop50ByActiveTrueOrderByDisplayNameAsc();
}