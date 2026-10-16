-- B63.3D
-- A Fantacalcio out_of_game signal proves that the player must no longer
-- be operational in Serie A, but it does not prove the destination.
--
-- Automatic Serie A exit claims are identified by their controlled
-- FANTACALCIO AUTO:*:EXIT:* detection key and may therefore keep both
-- destination fields NULL throughout their lifecycle.
--
-- Manual/non-automatic claims continue to require a destination.

ALTER TABLE player_transfer_claims
DROP CONSTRAINT ck_transfer_destination;

-- Repair only the old automatically-generated sentinel claims.
-- Preserve claim IDs/history instead of deleting and recreating them.
UPDATE player_transfer_claims claim
SET destination_club_name = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE claim.source = 'FANTACALCIO'
  AND claim.destination_club_name = 'OUTSIDE_SERIE_A'
  AND claim.to_club_id IS NULL
  AND claim.verification_status IN (
      'DETECTED',
      'PENDING_VERIFICATION'
  )
  AND claim.detection_key LIKE 'AUTO:%:EXIT:%'
  AND NOT EXISTS (
      SELECT 1
      FROM player_registration_events event
      WHERE event.transfer_claim_id = claim.id
  );

ALTER TABLE player_transfer_claims
ADD CONSTRAINT ck_transfer_destination
CHECK (
    to_club_id IS NOT NULL
    OR destination_club_name IS NOT NULL
    OR (
        source = 'FANTACALCIO'
        AND detection_key LIKE 'AUTO:%:EXIT:%'
    )
);