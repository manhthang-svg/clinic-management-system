-- UC-48: optional notes entered with a room transfer, kept with the transfer history.
ALTER TABLE visit_transfers ADD COLUMN notes VARCHAR(500);
