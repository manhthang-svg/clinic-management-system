import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, RecommendationField, SignerField, TextAreaField,
} from '../../../components/paraclinicalResult/ResultFields';

/** UC-67 Phiếu chiếu/chụp X-quang (MS 09/BV2): description, images like the ultrasound slip, conclusion. */
export function XRayResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly, templateButton }) => (
        <>
          <ResultSection title="Mô tả kết quả chiếu/chụp" description="Mỗi ý một dòng, bắt đầu bằng dấu “-”." action={templateButton}>
            <TextAreaField
              id="result-description"
              label="Mô tả"
              required
              rows={6}
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
            description="Ảnh phim X-quang. Hai ảnh đầu tiên được in bên phải phần mô tả."
          />
          <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <RecommendationField form={form} set={set} readOnly={readOnly} />
              <SignerField order={order} form={form} set={set} readOnly={readOnly} label="BS CN Hình ảnh ký phiếu" />
            </div>
          </ConclusionSection>
        </>
      )}
    </ResultEditor>
  );
}

export default XRayResultPage;
