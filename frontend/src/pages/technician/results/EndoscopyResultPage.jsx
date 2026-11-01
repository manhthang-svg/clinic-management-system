import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, SectionFields, SignerField,
} from '../../../components/paraclinicalResult/ResultFields';

/** UC-67 Phiếu nội soi (MS 11/BV-02): four images, then one line per site. */
export function EndoscopyResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {({ form, set, errors, readOnly, templateButton }) => (
        <>
          <FilesSection
            form={form}
            set={set}
            readOnly={readOnly}
            title="Hình ảnh nội soi"
            description="Bốn ảnh đầu tiên được in thành một hàng trên phiếu. Ghi chú thích vị trí cho từng ảnh."
          />
          <ResultSection title="Mô tả nội soi" description="Mô tả từng vị trí." action={templateButton}>
            <SectionFields order={order} form={form} set={set} errors={errors} readOnly={readOnly} />
          </ResultSection>
          <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly}>
            <div className="max-w-md">
              <SignerField order={order} form={form} set={set} readOnly={readOnly} />
            </div>
          </ConclusionSection>
        </>
      )}
    </ResultEditor>
  );
}

export default EndoscopyResultPage;
