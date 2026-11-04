// lab_parameters.value_type: label and tone of its badge.
const LAB_VALUE_TYPES = {
  NUMERIC: { label: 'Định lượng', tone: 'bg-[#e8f0fe] text-[#3b6fd8] border-[#cfdffb]' },
  QUALITATIVE: { label: 'Định tính', tone: 'bg-[#f1edfd] text-[#7c5cdb] border-[#e2d9fb]' },
  TEXT: { label: 'Văn bản', tone: 'bg-[#fdf3e1] text-[#c98a1b] border-[#f8e2b8]' },
};

/** The result kind of a lab parameter as a coloured badge. */
export function LabValueTypeBadge({ valueType }) {
  const type = LAB_VALUE_TYPES[valueType];
  if (!type) return <span className="text-slate-400">{valueType ?? '—'}</span>;
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-md text-xs font-semibold border whitespace-nowrap ${type.tone}`}>
      {type.label}
    </span>
  );
}

export default LabValueTypeBadge;
