-- UC-56: "Kết quả điều trị" becomes optional free text, as CV 365 III.31.1 (ketquadieutri, a string) and form 15/BV1
-- ("Tình trạng người bệnh ra viện") describe it; a first visit often has no outcome to record yet.
-- The former codes are kept as their labels: 1 Khỏi, 2 Đỡ, 3 Không thay đổi, 4 Nặng hơn.
ALTER TABLE conclusions ALTER COLUMN ketquadieutri DROP NOT NULL;

ALTER TABLE conclusions ALTER COLUMN ketquadieutri TYPE TEXT USING (
    CASE ketquadieutri
        WHEN 1 THEN 'Khỏi'
        WHEN 2 THEN 'Đỡ'
        WHEN 3 THEN 'Không thay đổi'
        WHEN 4 THEN 'Nặng hơn'
        ELSE ketquadieutri::text
    END);
