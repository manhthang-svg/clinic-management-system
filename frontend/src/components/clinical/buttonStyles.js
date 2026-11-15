/** Tailwind classes of the buttons on the prescription tab (UC-59, UC-90 to UC-92, UC-60), kept in one place. */
const BASE = 'inline-flex items-center gap-2 rounded-lg font-semibold disabled:cursor-not-allowed disabled:opacity-50';
const LARGE = `${BASE} min-h-10 py-2 text-sm`;
const SMALL = 'inline-flex items-center gap-1 rounded-lg border bg-white px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50';

export const BUTTON = {
    /** The main action: save. */
    primary: `${LARGE} bg-teal-700 px-5 text-white hover:bg-teal-800`,
    /** A second action of the same weight: sign and send, print. */
    outline: `${LARGE} border border-teal-700 bg-white px-5 text-teal-800 hover:bg-teal-50`,
    /** A neutral action: show the send result, close. */
    neutral: `${LARGE} border border-slate-300 bg-white px-4 text-slate-700 hover:bg-slate-50`,
    /** A destructive action: delete the prescription. */
    danger: `${LARGE} border border-rose-300 bg-white px-4 text-rose-700 hover:bg-rose-50`,
    /** Small actions inside the rejection (rose) and no-answer (amber) results. */
    smallRose: `${SMALL} border-rose-300 hover:bg-rose-100`,
    smallAmber: `${SMALL} border-amber-300 hover:bg-amber-100`,
};
