import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, SectionFields, SignerField,
} from '../../../components/paraclinicalResult/ResultFields';

/** UC-67 Phiếu điện tim (MS 12/BV-02): blood pressure and the ECG analysis items. */
export function EcgResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly, templateButton }) => (
        <>
          <ResultSection title="1. Kết quả" description="Huyết áp và phân tích điện tim." action={templateButton}>
            <SectionFields order={order} form={form} set={set} errors={errors} readOnly={readOnly} columns={2} />
          </ResultSection>
          <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly} label="2. Kết luận">
            <div className="max-w-md">
              <SignerField order={order} form={form} set={set} readOnly={readOnly} label="Bác sĩ ký phiếu" />
            </div>
          </ConclusionSection>
          <FilesSection form={form} set={set} readOnly={readOnly} title="Bản ghi điện tim" description="Ảnh hoặc PDF bản ghi điện tim (nếu có). Không in lên phiếu." />
        </>
      )}
    </ResultEditor>
  );
}

export default EcgResultPage;
