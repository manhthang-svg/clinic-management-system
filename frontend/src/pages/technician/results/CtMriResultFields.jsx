import { ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, ImageLinkField, SignerField, TextAreaField,
} from '../../../components/paraclinicalResult/ResultFields';

// truong dung chung phieu CT va MRI: ky thuat, mo ta, ket luan, link
export function CtMriResultFields({ order, form, set, errors, readOnly, templateButton }) {
  return (
    <>
      <ResultSection title="Kết quả" action={templateButton}>
        <TextAreaField
          id="result-technique"
          label="Kỹ thuật"
          rows={2}
          value={form.technique}
          onChange={(value) => set('technique', value)}
          readOnly={readOnly}
        />
        <TextAreaField
          id="result-description"
          label="Mô tả"
          required
          rows={9}
          mono
          value={form.description}
          onChange={(value) => set('description', value)}
          readOnly={readOnly}
          error={errors.description}
        />
      </ResultSection>
      <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ImageLinkField form={form} set={set} errors={errors} readOnly={readOnly} required />
          <SignerField order={order} form={form} set={set} readOnly={readOnly} />
        </div>
      </ConclusionSection>
    </>
  );
}

export default CtMriResultFields;
