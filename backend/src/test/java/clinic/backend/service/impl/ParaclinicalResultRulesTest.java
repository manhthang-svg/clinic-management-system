package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.entity.LabParameter;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.entity.MedicalService;
import clinic.backend.enums.LabParameterValueType;
import clinic.backend.enums.LabReferenceType;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ResultForm;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UC-67: interpretation of lab values and the printed slip of a service. */
class ParaclinicalResultRulesTest {

    @ParameterizedTest
    @CsvSource({"3.8,LOW", "3.9,NORMAL", "'6,4',NORMAL", "6.5,HIGH", "abc,"})
    void interpretsANumericValueAgainstAnInclusiveRange(String value, String expected) {
        LabReferenceRange range = range(LabReferenceType.RANGE, "3.9", "6.4");

        assertThat(ParaclinicalResultServiceImpl.interpret(numeric(), range, value)).isEqualTo(expected);
    }

    @Test
    void interpretsAQualitativeValueAgainstItsNormalValues() {
        LabReferenceRange range = range(LabReferenceType.QUALITATIVE, null, null);
        when(range.getNormalValues()).thenReturn(new String[] {"NEGATIVE"});
        LabParameter parameter = mock(LabParameter.class);
        when(parameter.getValueType()).thenReturn(LabParameterValueType.QUALITATIVE);

        assertThat(ParaclinicalResultServiceImpl.interpret(parameter, range, "NEGATIVE")).isEqualTo("NORMAL");
        assertThat(ParaclinicalResultServiceImpl.interpret(parameter, range, "POSITIVE")).isEqualTo("ABNORMAL");
    }

    @Test
    void hasNoInterpretationWithoutAReferenceRange() {
        assertThat(ParaclinicalResultServiceImpl.interpret(numeric(), null, "5")).isNull();
    }

    @Test
    void acceptsCommaAndDotDecimals() {
        assertThat(ParaclinicalResultServiceImpl.parseNumber("6,2")).isEqualByComparingTo("6.2");
        assertThat(ParaclinicalResultServiceImpl.parseNumber(" 11.4 ")).isEqualByComparingTo("11.4");
        assertThat(ParaclinicalResultServiceImpl.parseNumber("1.2.3")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "LAB,Tổng phân tích tế bào máu ngoại vi,LAB",
            "IMAGING,Siêu âm ổ bụng tổng quát,ULTRASOUND",
            "IMAGING,Chụp X-quang ngực thẳng,XRAY",
            "IMAGING,Chụp cắt lớp vi tính sọ não,CT",
            "IMAGING,Chụp CT ổ bụng,CT",
            "IMAGING,Chụp cộng hưởng từ cột sống cổ,MRI",
            "FUNCTIONAL,Nội soi dạ dày tá tràng,ENDOSCOPY",
            "FUNCTIONAL,Điện tim thường,ECG",
            "FUNCTIONAL,Đo chức năng hô hấp,XRAY",
    })
    void picksThePrintedSlipFromTheServiceTypeAndName(MedicalServiceType type, String name, ResultForm expected) {
        MedicalService service = mock(MedicalService.class);
        when(service.getType()).thenReturn(type);
        when(service.getTenchidinh()).thenReturn(name);

        assertThat(ParaclinicalResultServiceImpl.resultFormOf(service)).isEqualTo(expected);
    }

    private static LabParameter numeric() {
        LabParameter parameter = mock(LabParameter.class);
        when(parameter.getValueType()).thenReturn(LabParameterValueType.NUMERIC);
        return parameter;
    }

    private static LabReferenceRange range(LabReferenceType type, String low, String high) {
        LabReferenceRange range = mock(LabReferenceRange.class);
        when(range.getReferenceType()).thenReturn(type);
        when(range.getLowerBound()).thenReturn(low == null ? null : new BigDecimal(low));
        when(range.getUpperBound()).thenReturn(high == null ? null : new BigDecimal(high));
        when(range.isLowerInclusive()).thenReturn(true);
        when(range.isUpperInclusive()).thenReturn(true);
        when(range.getEffectiveFrom()).thenReturn(LocalDate.of(2020, 1, 1));
        when(range.getId()).thenReturn(UUID.randomUUID());
        when(range.getCreatedAt()).thenReturn(Instant.now());
        return range;
    }
}
