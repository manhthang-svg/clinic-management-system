package clinic.backend.utils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Cv365XmlSerializerTest {

    @Test
    @DisplayName("Cv365XmlSerializer: serialize Map sang XML với root HoSoBenhAn và lồng thẻ chính xác")
    void serialize_success() {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("mabenhnhan", "BN00001");
        patient.put("hoten", "NGUYEN VAN A");
        patient.put("tuoi", 30);
        patient.put("buong", null);

        Map<String, Object> historyItem1 = new LinkedHashMap<>();
        historyItem1.put("thongtin_noidung", "Dị ứng Penicillin");
        Map<String, Object> historyItem2 = new LinkedHashMap<>();
        historyItem2.put("thongtin_noidung", "Tăng huyết áp");
        patient.put("tiensubenhtatcuabanthan", List.of(historyItem1, historyItem2));

        Map<String, Object> hoSo = new LinkedHashMap<>();
        hoSo.put("ThongTinBenhNhan", patient);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("HoSoBenhAn", hoSo);

        byte[] xmlBytes = Cv365XmlSerializer.serialize(root);
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);

        assertThat(xml).contains("<HoSoBenhAn>");
        assertThat(xml).contains("</HoSoBenhAn>");
        assertThat(xml).contains("<mabenhnhan>BN00001</mabenhnhan>");
        assertThat(xml).contains("<hoten>NGUYEN VAN A</hoten>");
        assertThat(xml).contains("<tuoi>30</tuoi>");
        assertThat(xml).contains("<thongtin_noidung>Dị ứng Penicillin</thongtin_noidung>");
        assertThat(xml).contains("<thongtin_noidung>Tăng huyết áp</thongtin_noidung>");
    }
}

