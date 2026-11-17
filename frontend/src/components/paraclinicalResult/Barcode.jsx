import { code128Widths } from './code128';

// ve ma vach dang SVG, chu ben duoi
export function Barcode({ value, height = 34, module = 1.2 }) {
  const widths = code128Widths(value);
  const bars = [];
  let x = 10 * module; // quiet zone
  widths.forEach((width, index) => {
    if (index % 2 === 0) bars.push(<rect key={index} x={x} y={0} width={width * module} height={height} />);
    x += width * module;
  });
  const total = x + 10 * module;
  return (
    <figure className="inline-flex flex-col items-center m-0">
      <svg role="img" aria-label={`Mã vạch ${value}`} width={total} height={height} viewBox={`0 0 ${total} ${height}`} fill="#000">
        {bars}
      </svg>
      <figcaption className="text-[11px] font-mono tracking-wider">{value}</figcaption>
    </figure>
  );
}

export default Barcode;
