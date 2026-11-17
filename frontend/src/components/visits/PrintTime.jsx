import { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';

function formatPrintTime(date) {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
  }).format(date).replace(',', '');
}

/**
 * UC-41 step 5 / AF-02: the print time, small, on the top edge of a printed slip. It is set again just before each
 * print (synchronously, so the printed page carries it) and is shown on paper only, not on the screen preview.
 */
export function PrintTime({ className = '' }) {
  const [printedAt, setPrintedAt] = useState(() => new Date());

  useEffect(() => {
    const update = () => flushSync(() => setPrintedAt(new Date()));
    window.addEventListener('beforeprint', update);
    return () => window.removeEventListener('beforeprint', update);
  }, []);

  return (
    <p className={`hidden text-[10px] leading-none text-slate-600 print:block ${className}`}>
      In lúc: {formatPrintTime(printedAt)}
    </p>
  );
}

export default PrintTime;
