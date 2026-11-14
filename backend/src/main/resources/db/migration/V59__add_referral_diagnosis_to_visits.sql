-- Chẩn đoán của nơi giới thiệu: TT 32/2023/TT-BYT mẫu 15/BV1 mục I.13; CV 365 II.7, III.17; BR-129.
-- Lưu cùng dòng với noigioithieu_loai (cùng mục I.13) để chỉ có một nguồn dữ liệu.
ALTER TABLE visits
    ADD COLUMN chandoancuanoigioithieu TEXT;

-- BR-129: bắt buộc 2–1.000 ký tự khi nơi giới thiệu là Y tế, không có giá trị khi Tự đến.
-- NOT VALID: lượt khám cũ đã chọn CO_SO_Y_TE chưa có chẩn đoán; chỉ kiểm tra dòng thêm mới hoặc sửa.
ALTER TABLE visits
    ADD CONSTRAINT ck_visits_chandoancuanoigioithieu CHECK (
        (noigioithieu_loai = 'CO_SO_Y_TE'
            AND char_length(btrim(chandoancuanoigioithieu)) BETWEEN 2 AND 1000)
        OR (noigioithieu_loai IS DISTINCT FROM 'CO_SO_Y_TE'
            AND chandoancuanoigioithieu IS NULL)
    ) NOT VALID;

-- Chỉ lưu một chỗ: cột cũ trên phiếu khám chưa từng được đọc hoặc ghi.
ALTER TABLE clinical_examinations
    DROP COLUMN chandoancuanoigioithieu;
