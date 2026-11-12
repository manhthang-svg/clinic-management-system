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

/**
 * UC-67 entry: loads the order and opens the result page of its service.
 *
 * Switching between the services of the same order (the tabs on top of the form) must feel instant, so the page keeps
 * the forms it loaded in a cache, preloads the other tabs in the background once a form is shown, and refreshes the
 * cache with every save. The tabs always show the latest statuses known on this page.
 */
export function ParaclinicalResultPage({ api: injectedApi }) {
  const { orderId } = useParams();
  const baseApi = injectedApi ?? resultApiFor(orderId, resultMockApi);
  // Form of each service, as a promise: a tab clicked while it is still preloading reuses that request.
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

  // Saving a form updates the cache and the tab statuses, so going back to that tab shows what was saved.
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

  // Preload the other tabs once a form is on screen; a failed preload is ignored and the tab loads on click.
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
