import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, RecommendationField, SignerField, TextAreaField,
} from '../../../components/paraclinicalResult/ResultFields';

// UC-67: phieu sieu am (1 o mo ta + 2 anh)
export function UltrasoundResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly, templateButton }) => (
        <>
          <ResultSection title="Kết quả siêu âm" description="Mỗi ý một dòng, bắt đầu bằng dấu “-”." action={templateButton}>
            <TextAreaField
              id="result-description"
              label="Mô tả"
              required
              rows={8}
              mono
              value={form.description}
              onChange={(value) => set('description', value)}
              readOnly={readOnly}
              error={errors.description}
            />
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
