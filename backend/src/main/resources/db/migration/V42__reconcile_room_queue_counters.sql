-- Existing visits may have been seeded/imported without advancing the matching counter.
-- Reconcile every room/day before issuing more queue numbers; never move a counter backwards.
INSERT INTO room_queue_counters (room_id, queue_date, last_queue_number)
SELECT room_id, queue_date, MAX(queue_number)
FROM visits
GROUP BY room_id, queue_date
ON CONFLICT (room_id, queue_date)
DO UPDATE SET last_queue_number = GREATEST(
    room_queue_counters.last_queue_number,
    EXCLUDED.last_queue_number
);
