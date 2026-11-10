-- V56: Bổ sung các trường phục vụ Check-in (xác nhận có mặt) tại phòng cận lâm sàng (Case 1)
-- Cho phép lưu thời điểm bệnh nhân có mặt tại phòng và nhân sự thực hiện check-in trước khi cấp số thứ tự (STT).

ALTER TABLE paraclinical_order_details
    ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS checked_in_by_account_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_paraclinical_order_details_checked_in_by'
    ) THEN
        ALTER TABLE paraclinical_order_details
            ADD CONSTRAINT fk_paraclinical_order_details_checked_in_by
            FOREIGN KEY (checked_in_by_account_id) REFERENCES staff_accounts (id) ON DELETE RESTRICT;
    END IF;
END $$;

-- Đánh index tra cứu nhanh theo phòng và trạng thái check-in
CREATE INDEX IF NOT EXISTS idx_paraclinical_order_details_room_checkin
    ON paraclinical_order_details (room_id, checked_in_at);

