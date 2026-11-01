import { TriangleAlert } from 'lucide-react';
import { ResultEditor, ResultSection } from '../../../components/paraclinicalResult/ResultEditor';
import { CtMriResultFields } from './CtMriResultFields';

/** UC-67 Phiếu chụp cộng hưởng từ (MS 09/BV2), with the safety screening from the order. */
export function MriResultPage({ order, api }) {
  return (
    <ResultEditor order={order} api={api}>
      {(context) => (
        <>
          <ResultSection title="Sàng lọc trước chụp" description="Do bác sĩ chỉ định ghi khi chỉ định; in trên phiếu.">
            <div className="flex items-start gap-2.5 p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-sm">
              <TriangleAlert className="w-4 h-4 shrink-0 mt-0.5" />
              <dl className="space-y-1">
                <div><dt className="inline font-semibold">Dị vật kim loại trong cơ thể: </dt><dd className="inline">{order.screening?.metalImplant ?? 'Chưa ghi'}</dd></div>
                <div><dt className="inline font-semibold">Kết quả XN, CĐHA đã có: </dt><dd className="inline">{order.screening?.priorResults ?? 'Chưa ghi'}</dd></div>
              </dl>
            </div>
          </ResultSection>
          <CtMriResultFields {...context} />
        </>
      )}
    </ResultEditor>
  );
}

export default MriResultPage;
