import { ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import {
  ConclusionSection, FilesSection, SignerField, TextAreaField,
} from '../../../components/paraclinicalResult/ResultFields';

// truong dung chung phieu CT va MRI: ky thuat, mo ta, anh (tai len Cloudinary), ket luan
export function CtMriResultFields({ order, form, set, errors, readOnly, templateButton }) {
  return (
    <>
      <ResultSection title="Kết quả" action={templateButton}>
        <TextAreaField
          id="result-technique"
          label="Kỹ thuật"
          required
          rows={2}
          value={form.technique}
          onChange={(value) => set('technique', value)}
          readOnly={readOnly}
          error={errors.technique}
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
      <FilesSection form={form} set={set} readOnly={readOnly} required error={errors.files} />
      <ConclusionSection form={form} set={set} errors={errors} readOnly={readOnly}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SignerField order={order} form={form} set={set} readOnly={readOnly} />
        </div>
      </ConclusionSection>
    </>
  );
}

export default CtMriResultFields;
