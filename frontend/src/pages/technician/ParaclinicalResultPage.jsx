import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { FormAlert } from '../../components/shared/FormFields';
import { resultMockApi } from '../../components/paraclinicalResult';
import { ResultBreadcrumb } from '../../components/paraclinicalResult/ResultEditor';
import { resultErrorMessage } from '../../components/paraclinicalResult/resultForm';
import {
  CtResultPage,
  EcgResultPage,
  EndoscopyResultPage,
  LabResultPage,
  MriResultPage,
  UltrasoundResultPage,
  XRayResultPage,
} from './results';

/** Each printed form has its own result page. */
const PAGES = {
  LAB: LabResultPage,
  ULTRASOUND: UltrasoundResultPage,
  ENDOSCOPY: EndoscopyResultPage,
  XRAY: XRayResultPage,
  CT: CtResultPage,
  MRI: MriResultPage,
  ECG: EcgResultPage,
};

/** UC-67 entry: loads the order and opens the result page of its service. Demo data until the API exists. */
export function ParaclinicalResultPage({ api = resultMockApi }) {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.get(orderId);
        if (!cancelled) setOrder(data);
      } catch (loadError) {
        if (!cancelled) setError(resultErrorMessage(loadError));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api, orderId]);

  if (!order) {
    return (
      <div className="space-y-6 pb-10">
        <ResultBreadcrumb title="Phiếu kết quả" />
        {error ? <FormAlert>{error}</FormAlert> : <p className="text-sm text-slate-500">Đang tải phiếu…</p>}
      </div>
    );
  }

  const Page = PAGES[order.printForm] ?? LabResultPage;
  return <Page key={order.id} order={order} api={api} />;
}

export default ParaclinicalResultPage;
