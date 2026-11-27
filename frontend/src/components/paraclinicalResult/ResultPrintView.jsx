import { Barcode } from './Barcode';
import { MOCK_CLINIC, PRINT_FORMS } from './resultMockData';
import {
  formatLongDate, formatReference, formatTime, interpret, qualitativeLabel,
} from './resultForm';

// UC-68: ban in phieu ket qua, moi phieu 1 trang A4 theo mau phong kham
// cac mau: xet nghiem, sieu am, noi soi, X-quang, CT/MRI, dien tim

function images(form) {
  return (form.files ?? []).filter((file) => file.type.startsWith('image/') && file.url);
}

function Header({ order, form: printForm, copy }) {
  // phieu that in thong tin co so (UC-77), phieu demo in tieu de mau
  const clinic = order.clinic === undefined ? MOCK_CLINIC : order.clinic;
  return (
    <header className="flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <div className="w-12 h-12 border-2 border-black flex items-center justify-center text-[10px] font-bold shrink-0">LOGO</div>
        <div className="leading-5">
          {clinic?.parentAgency && <div className="font-bold text-red-700">{clinic.parentAgency}</div>}
          <div className="font-extrabold">{clinic?.name ?? 'CHƯA CẤU HÌNH THÔNG TIN PHÒNG KHÁM'}</div>
          {clinic?.address && <div className="text-[12px]">Địa chỉ: {clinic.address}</div>}
          {clinic?.phone && <div className="text-[12px]">Điện thoại: {clinic.phone}</div>}
        </div>
      </div>
      <div className="text-right">
        <div className="font-bold">MS: {printForm.formCode}</div>
        {printForm.layout === 'LAB' && <div className="text-[12px]"><strong>Mã XN:</strong> {order.orderCode}</div>}
        <Barcode value={order.orderCode} />
        {order.registrationCode && <div className="text-[12px]"><strong>Số vào viện:</strong> {order.registrationCode}</div>}
        {copy && <div className="mt-1 font-extrabold text-red-700">BẢN SAO</div>}
      </div>
    </header>
  );
}

function Title({ printForm, order, showPriority = true }) {
  const emergency = order.priority === 'EMERGENCY';
  return (
    <div className="text-center my-3">
      <h2 className="text-[20px] font-extrabold tracking-wide">{printForm.title}</h2>
      {showPriority && (
        <div className="flex justify-center items-center gap-3 text-[12px] italic mt-1">
          <span>Thường</span>
          <span className="w-5 h-5 border border-black inline-flex items-center justify-center not-italic font-bold">{emergency ? '' : 'X'}</span>
          <span className="text-red-700">Cấp cứu</span>
          <span className="w-5 h-5 border border-red-700 inline-flex items-center justify-center not-italic font-bold">{emergency ? 'X' : ''}</span>
        </div>
      )}
    </div>
  );
}

function Row({ children }) {
  return <div className="flex flex-wrap gap-x-6">{children}</div>;
}

function Item({ label, children, className = '' }) {
  return (
    <span className={className}>
      <strong>{label}</strong> {children}
    </span>
  );
}

// cac dong thong tin benh nhan theo tung mau phieu
function PatientBlock({ order, printForm }) {
  const { patient } = order;
  const fields = printForm.fields ?? [];
  const has = (field) => fields.includes(field);
  return (
    <div className="space-y-0.5">
      <Row>
        <Item label={printForm.nameLabel} className="flex-1"><span className="font-bold uppercase">{patient.name}</span></Item>
        <Item label="Tuổi:">{patient.age} tuổi</Item>
        <Item label="Giới:">{patient.gender}</Item>
      </Row>
      {has('coverage') && (
        <Row>
          <Item label="Đối tượng:">{patient.coverage}</Item>
          {patient.insuranceNumber && <Item label="Số thẻ BHYT:" className="flex-1">{patient.insuranceNumber}</Item>}
          {printForm.ethnicity && <Item label="Dân tộc:">{patient.ethnicity}</Item>}
        </Row>
      )}
      {has('address') && <Row><Item label="Địa chỉ:">{patient.address}</Item></Row>}
      {has('departmentRoom') && (
        <Row>
          <Item label="Khoa:">{order.department}</Item>
          <Item label="Phòng:">{order.orderingRoom}</Item>
        </Row>
      )}
      {has('room') && (
        <Row>
          <Item label="Phòng:" className="flex-1">{order.orderingRoom}</Item>
          {printForm.layout === 'XRAY' && order.performingDepartment && <span>({order.performingDepartment})</span>}
        </Row>
      )}
      {has('symptoms') && <Row><Item label="Triệu chứng:">{order.symptoms}</Item></Row>}
      {has('diagnosis') && <Row><Item label="Chẩn đoán:">{order.diagnosis}</Item></Row>}
      {has('request') && <Row><Item label={printForm.layout === 'CT_MRI' ? 'Yêu cầu:' : 'Yêu cầu kiểm tra:'}>{order.serviceName}</Item></Row>}
      {has('screening') && order.screening && (
        <>
          <div>- Người bệnh có dị vật kim loại trong cơ thể: {order.screening.metalImplant}</div>
          <div>- Các kết quả xét nghiệm, chẩn đoán hình ảnh đã có: {order.screening.priorResults}</div>
        </>
      )}
    </div>
  );
}

// ngay + cho ky tay
function Signature({ date, time, title, name, className = '' }) {
  return (
    <div className={`text-center w-64 ${className}`}>
      <div className="italic">{time ? `${time}, ` : ''}{formatLongDate(date)}</div>
      <div className="font-bold">{title}</div>
      <div className="h-14" />
      <div className="font-bold">{name}</div>
    </div>
  );
}

// ai thuc hien, may nao, luc nao
function PerformedBy({ order }) {
  const instrument = order.result?.instrumentName;
  if (!instrument && !order.performedAt) return null;
  return (
    <Row>
      {order.performedAt && (
        <Item label="Thời gian thực hiện:">
          {new Date(order.performedAt).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })}
        </Item>
      )}
      {instrument && <Item label="Máy thực hiện:">{instrument}</Item>}
    </Row>
  );
}

function OrderingDoctor({ order }) {
  return (
    <div className="flex justify-end">
      <Signature date={order.orderedAt} title="Bác sĩ điều trị" name={order.orderingDoctor} />
    </div>
  );
}

function Conclusion({ form, label = 'KẾT LUẬN' }) {
  return (
    <div>
      <div className="font-bold text-red-700">{label}</div>
      <p className="whitespace-pre-line font-semibold">{form.conclusion}</p>
    </div>
  );
}

function LabTable({ order, form }) {
  const rows = [];
  let number = 0;
  let lastGroup = null;
  order.parameters.forEach((parameter) => {
    if (parameter.group && parameter.group !== lastGroup) {
      lastGroup = parameter.group;
      number += 1;
      rows.push(
        <tr key={`g-${parameter.group}`}>
          <td className="border border-black px-1.5 text-center">{number}</td>
          <td className="border border-black px-1.5 font-semibold" colSpan={4}>{parameter.group}</td>
        </tr>,
      );
    }
    const value = form.values[parameter.code];
    const flag = interpret(parameter, value);
    const color = flag === 'HIGH' || flag === 'ABNORMAL' ? 'text-red-600 font-bold' : flag === 'LOW' ? 'text-blue-700 font-bold' : '';
    if (!parameter.group) number += 1;
    rows.push(
      <tr key={parameter.code}>
        <td className="border border-black px-1.5 text-center">{parameter.group ? '' : number}</td>
        <td className={`border border-black px-1.5 ${parameter.group ? 'pl-4' : ''}`}>{parameter.name}</td>
        <td className={`border border-black px-1.5 text-center ${color}`}>
          {parameter.valueType === 'QUALITATIVE' ? qualitativeLabel(value) : value}
        </td>
        <td className="border border-black px-1.5 text-center">{formatReference(parameter.ref)}</td>
        <td className="border border-black px-1.5 text-center">{parameter.unit}</td>
      </tr>,
    );
  });

  return (
    <table className="w-full border-collapse border border-black text-[12.5px]">
      <thead>
        <tr>
          <th className="border border-black px-1.5 w-8">TT</th>
          <th className="border border-black px-1.5">Tên xét nghiệm</th>
          <th className="border border-black px-1.5 w-20">Kết quả</th>
          <th className="border border-black px-1.5 w-28">Tham chiếu</th>
          <th className="border border-black px-1.5 w-20">ĐVT</th>
        </tr>
      </thead>
      <tbody>
        <tr><td colSpan={5} className="border border-black text-center font-bold">{order.section}</td></tr>
        {rows}
      </tbody>
    </table>
  );
}

function LabSlip({ order, form, printForm }) {
  const { patient } = order;
  const done = order.result?.submittedAt;
  return (
    <>
      <div className="grid grid-cols-[1fr_auto] gap-x-6">
        <div className="space-y-0.5">
          <Item label="Họ tên:"><span className="font-bold uppercase">{patient.name}</span></Item>
          <div><Item label="Địa chỉ:">{patient.address}</Item></div>
          <div><Item label="Đối tượng:">{patient.coverage}</Item></div>
          {patient.insuranceNumber && <div><Item label="Số thẻ BHYT:">{patient.insuranceNumber}</Item></div>}
          <div><Item label="Số điện thoại:">{patient.phone}</Item></div>
          <div><Item label="Phòng:"><span className="font-bold">{order.orderingRoom}</span></Item></div>
        </div>
        <div className="space-y-0.5">
          <div><Item label="Tuổi:">{patient.age} tuổi</Item> <Item label="Giới tính:" className="ml-4">{patient.gender}</Item></div>
          <div className="h-5" />
          <div><Item label="TG nhận mẫu:">{form.receivedAt ? new Date(form.receivedAt).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '……'}</Item></div>
          <div><Item label="TG hoàn thành:">{done ? new Date(done).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '……'}</Item></div>
        </div>
      </div>
      <div><Item label="Chẩn đoán:">{order.diagnosis}</Item></div>
      <LabTable order={order} form={form} />
      {form.conclusion && <div><strong>Nhận xét:</strong> <span className="whitespace-pre-line">{form.conclusion}</span></div>}
      <PerformedBy order={order} />
      <div className="flex justify-between">
        <Signature date={order.orderedAt} title="Bác sĩ điều trị" name={order.orderingDoctor} />
        <Signature date={done} time={formatTime(done)} title={printForm.signer} name={order.performer?.name} />
      </div>
    </>
  );
}

function DescriptiveSlip({ order, form, printForm }) {
  const pictures = images(form);
  const done = order.result?.submittedAt;
  // nguoi ky la KTV thuc hien
  const signerName = order.performer?.name;
  const { layout } = printForm;

  return (
    <>
      <PatientBlock order={order} printForm={printForm} />

      {layout === 'XRAY' && (
        <table className="w-full border-collapse border border-black">
          <tbody>
            <tr><td className="border border-black px-2 font-bold">Yêu cầu chiếu/chụp</td></tr>
            <tr><td className="border border-black px-2">{order.serviceName}</td></tr>
          </tbody>
        </table>
      )}

      <OrderingDoctor order={order} />

      {layout === 'ENDOSCOPY' && pictures.length > 0 && (
        <div className="grid grid-cols-4 gap-2">
          {pictures.slice(0, 4).map((file) => (
            <figure key={file.id} className="m-0">
              <img src={file.url} alt={file.caption || file.name} className="w-full aspect-[4/3] object-cover bg-black" />
              {file.caption && <figcaption className="text-[11px] text-center">{file.caption}</figcaption>}
            </figure>
          ))}
        </div>
      )}

      {layout === 'XRAY' && (
        <div className="grid grid-cols-[1fr_auto] gap-4">
          <table className="w-full border-collapse border border-black">
            <tbody>
              <tr><td className="border border-black px-2 font-bold">Mô tả kết quả chiếu/chụp</td></tr>
              <tr><td className="border border-black px-2 whitespace-pre-line">{form.description}</td></tr>
              <tr><td className="border border-black px-2 font-bold text-red-700">Kết luận</td></tr>
              <tr><td className="border border-black px-2 whitespace-pre-line font-semibold">{form.conclusion}</td></tr>
            </tbody>
          </table>
          {/* 2 anh dau ben canh mo ta */}
          {pictures.length > 0 && (
            <div className="w-56 space-y-2">
              {pictures.slice(0, 2).map((file) => (
                <figure key={file.id} className="m-0">
                  <img src={file.url} alt={file.caption || file.name} className="w-full aspect-[4/3] object-cover bg-black" />
                  {file.caption && <figcaption className="text-[11px] text-center">{file.caption}</figcaption>}
                </figure>
              ))}
            </div>
          )}
        </div>
      )}

      {layout === 'CT_MRI' && (
        <div className="space-y-1">
          <div className="text-center font-extrabold">KẾT QUẢ</div>
          {form.technique && <p><strong>* Kỹ thuật:</strong> <strong>{form.technique}</strong></p>}
          <div><strong>* Mô tả:</strong></div>
          <p className="whitespace-pre-line">{form.description}</p>
          {pictures.length > 0 && (
            <div className="grid grid-cols-2 gap-2 py-1">
              {pictures.slice(0, 2).map((file) => (
                <figure key={file.id} className="m-0">
                  <img src={file.url} alt={file.caption || file.name} className="w-full aspect-[4/3] object-cover bg-black" />
                  {file.caption && <figcaption className="text-[11px] text-center">{file.caption}</figcaption>}
                </figure>
              ))}
            </div>
          )}
          <Conclusion form={form} label="Kết luận" />
        </div>
      )}

      {layout === 'ECG' && (
        <div className="space-y-1">
          <div className="font-bold">1. Kết quả:</div>
          <p className="whitespace-pre-line">{form.description}</p>
          {/* bang dien tim dai, in toi da 2 anh dau, moi anh het chieu ngang phieu */}
          {pictures.length > 0 && (
            <div className="space-y-2 py-1">
              {pictures.slice(0, 2).map((file) => (
                <figure key={file.id} className="m-0">
                  <img src={file.url} alt={file.caption || file.name} className="w-full max-h-64 object-contain bg-white border border-black" />
                  {file.caption && <figcaption className="text-[11px] text-center">{file.caption}</figcaption>}
                </figure>
              ))}
            </div>
          )}
          <div className="font-bold">2. Kết luận:</div>
          <p className="whitespace-pre-line font-semibold">{form.conclusion}</p>
        </div>
      )}

      {layout === 'ULTRASOUND' && (
        <div className="grid grid-cols-[1fr_auto] gap-4">
          <div>
            <div className="font-bold">KẾT QUẢ SIÊU ÂM</div>
            <p className="whitespace-pre-line">{form.description}</p>
            <Conclusion form={form} />
          </div>
          {pictures.length > 0 && (
            <div className="w-56 space-y-2">
              {pictures.slice(0, 2).map((file) => (
                <img key={file.id} src={file.url} alt={file.caption || file.name} className="w-full aspect-[4/3] object-cover bg-black" />
              ))}
            </div>
          )}
        </div>
      )}

      {layout === 'ENDOSCOPY' && (
        <div>
          <p className="whitespace-pre-line">{form.description}</p>
          <Conclusion form={form} />
        </div>
      )}

      {/* link xem phim (PACS), neu co, in cho moi loai phieu hinh anh */}
      {form.imageLink && (
        <p className="text-[12px] break-all"><strong>Link xem phim (PACS):</strong> {form.imageLink}</p>
      )}

      <PerformedBy order={order} />
      <div className="flex justify-between items-end pt-2">
        <div className="w-56 text-[12px]">
          {(layout === 'ULTRASOUND' || layout === 'XRAY') && (
            <div>
              <div className="italic">Lời dặn của BS chuyên khoa</div>
              <div className="whitespace-pre-line">{form.recommendation}</div>
            </div>
          )}
          {layout !== 'CT_MRI' && layout !== 'ULTRASOUND' && layout !== 'XRAY' && form.recommendation && (
            <div><strong>Lời dặn:</strong> {form.recommendation}</div>
          )}
        </div>
        <Signature date={done} title={printForm.signer} name={signerName} />
      </div>
    </>
  );
}

// UC-68: chon mau phieu va in (ban in lai co chu Ban sao)
export function ResultPrintView({ order, form }) {
  const printForm = PRINT_FORMS[order.printForm] ?? PRINT_FORMS.LAB;
  const copy = (order.printCount ?? 0) > 0;
  const lab = printForm.layout === 'LAB';
  return (
    <article
      aria-label="Phiếu kết quả in"
      className="paraclinical-result-print break-inside-avoid bg-white text-black text-[13px] leading-[1.45] font-['Times_New_Roman',serif] w-[210mm] min-h-[297mm] mx-auto p-[12mm] space-y-2"
    >
      <Header order={order} form={printForm} copy={copy} />
      <Title printForm={printForm} order={order} showPriority={printForm.priorityBox} />
      {lab
        ? <LabSlip order={order} form={form} printForm={printForm} />
        : <DescriptiveSlip order={order} form={form} printForm={printForm} />}
    </article>
  );
}

export default ResultPrintView;
