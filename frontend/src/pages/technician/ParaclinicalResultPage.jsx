import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router';
import { FormAlert } from '../../components/shared/FormFields';
import { resultMockApi } from '../../components/paraclinicalResult';
import { resultApiFor } from '../../apis/paraclinicalResultApi';
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

// moi mau phieu co 1 trang nhap rieng
const PAGES = {
  LAB: LabResultPage,
  ULTRASOUND: UltrasoundResultPage,
  ENDOSCOPY: EndoscopyResultPage,
  XRAY: XRayResultPage,
  CT: CtResultPage,
  MRI: MriResultPage,
  ECG: EcgResultPage,
};

// UC-67: tai phieu va mo trang nhap dung loai dich vu
// luu cache cac tab da tai + tai truoc cac tab khac de chuyen tab khong phai cho
export function ParaclinicalResultPage({ api: injectedApi }) {
  const { orderId } = useParams();
  const baseApi = injectedApi ?? resultApiFor(orderId, resultMockApi);
  // cache phieu tung dich vu (promise): tab dang tai truoc thi dung lai request do
  const cacheRef = useRef(new Map());
  const [order, setOrder] = useState(null);
  const [siblings, setSiblings] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback((id) => {
    let request = cacheRef.current.get(id);
    if (!request) {
      request = baseApi.get(id);
      cacheRef.current.set(id, request);
      request.catch(() => cacheRef.current.delete(id)); // a failed load is retried on the next click
    }
    return request;
  }, [baseApi]);

  // luu xong thi cap nhat cache + trang thai tab
  const api = useMemo(() => {
    const remember = (saved) => {
      if (saved?.id) cacheRef.current.set(saved.id, Promise.resolve(saved));
      if (saved?.siblingServices?.length) setSiblings(saved.siblingServices);
      return saved;
    };
    return {
      ...baseApi,
      saveDraft: async (...args) => remember(await baseApi.saveDraft(...args)),
      submit: async (...args) => remember(await baseApi.submit(...args)),
      recordPrint: async (...args) => remember(await baseApi.recordPrint(...args)),
    };
  }, [baseApi]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await load(orderId);
        if (!cancelled) {
          setError(null);
          setOrder(data);
          if (data.siblingServices?.length) setSiblings((current) => current ?? data.siblingServices);
        }
      } catch (loadError) {
        if (!cancelled) setError({ orderId, message: resultErrorMessage(loadError) });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [load, orderId]);

  // tai truoc cac tab khac; loi thi bo qua, bam tab se tai lai
  useEffect(() => {
    (siblings ?? []).forEach((service) => {
      if (service.id) load(service.id).catch(() => {});
    });
  }, [load, siblings]);

  const loadingOther = order && order.id !== orderId && error?.orderId !== orderId;

  if (!order || error?.orderId === orderId) {
    return (
      <div className="space-y-6 pb-10">
        <ResultBreadcrumb title="Phiếu kết quả" />
        {error ? <FormAlert>{error.message}</FormAlert> : <p className="text-sm text-slate-500">Đang tải phiếu…</p>}
      </div>
    );
  }

  const Page = PAGES[order.printForm] ?? LabResultPage;
  const shown = siblings ? { ...order, siblingServices: siblings } : order;
  return (
    <div className="relative">
      {loadingOther && (
        <p role="status" className="absolute right-0 top-0 text-xs font-medium text-teal-700">Đang tải phiếu…</p>
      )}
      <div className={loadingOther ? 'opacity-60 pointer-events-none transition-opacity' : 'transition-opacity'}>
        <Page key={order.id} order={shown} api={api} />
      </div>
    </div>
  );
}

export default ParaclinicalResultPage;
