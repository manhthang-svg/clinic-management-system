import { ResultEditor } from '../../../components/paraclinicalResult/ResultEditor';
import { CtMriResultFields } from './CtMriResultFields';

// UC-67: phieu chup CT
export function CtResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {(context) => <CtMriResultFields {...context} />}
    </ResultEditor>
  );
}

export default CtResultPage;
