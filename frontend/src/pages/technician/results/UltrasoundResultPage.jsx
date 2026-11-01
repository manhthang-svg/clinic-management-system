import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, RecommendationField, SectionFields, SignerField,
} from '../../../components/paraclinicalResult/ResultFields';

/** UC-67 Phiếu siêu âm (MS 11/BV-02): one line per organ, two images beside the text. */
export function UltrasoundResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly, templateButton }) => (
        <>
          <ResultSection title="Kết quả siêu âm" description="Mô tả từng cơ quan." action={templateButton}>
            <SectionFields order={order} form={form} set={set} errors={errors} readOnly={readOnly} />
          </ResultSection>
          <FilesSection
            form={form}
            set={set}
            readOnly={readOnly}
            description="Hai ảnh đầu tiên được in bên phải phần kết quả."
          />
          <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <RecommendationField form={form} set={set} readOnly={readOnly} />
              <SignerField order={order} form={form} set={set} readOnly={readOnly} />
            </div>
          </ConclusionSection>
        </>
      )}
    </ResultEditor>
  );
}

export default UltrasoundResultPage;
