import { Field, INPUT_CLASS } from '../../../components/shared/FormFields';
import { LabResultTable } from '../../../components/paraclinicalResult';
import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import { ConclusionSection } from '../../../components/paraclinicalResult/ResultFields';

// UC-67: phieu ket qua xet nghiem
export function LabResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly }) => (
        <>
          <ResultSection
            title={order.section}
            description="Nhập giá trị đo được. Hệ thống tự đánh giá cao/thấp theo khoảng tham chiếu (dữ liệu mẫu)."
          >
            <div className="max-w-xs">
              <Field id="result-receivedAt" label="TG nhận mẫu">
                <input
                  id="result-receivedAt"
                  type="datetime-local"
                  value={form.receivedAt}
                  onChange={(event) => set('receivedAt', event.target.value)}
                  disabled={readOnly}
                  className={INPUT_CLASS}
                />
              </Field>
            </div>
            <LabResultTable
              parameters={order.parameters}
              values={form.values}
              errors={errors}
              readOnly={readOnly}
              onChange={(code, value) => set('values', { ...form.values, [code]: value })}
            />
          </ResultSection>
          <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly} label="Nhận xét" />
        </>
      )}
    </ResultEditor>
  );
}

export default LabResultPage;
