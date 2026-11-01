import { ResultEditor } from '../../../components/paraclinicalResult/ResultEditor';
import { CtMriResultFields } from './CtMriResultFields';

/** UC-67 Phiếu chụp cắt lớp vi tính (MS 09/BV2). */
export function CtResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {(context) => <CtMriResultFields {...context} />}
    </ResultEditor>
  );
}

export default CtResultPage;
