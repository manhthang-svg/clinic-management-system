import { useContext, useRef, useState } from 'react';
import { FileText, ImagePlus, Trash2 } from 'lucide-react';
import { ACCEPTED_TYPES, MAX_FILES, checkFiles, formatFileSize, resultErrorMessage } from './resultForm';
import { ResultApiContext } from './resultContext';

let nextId = 0;

// link xem truoc anh
function previewUrl(file) {
  try {
    return URL.createObjectURL(file);
  } catch {
    return '';
  }
}

// giu chu thich dang go do chua luu khi danh sach anh moi tu server tra ve
function withLocalCaptions(serverFiles, localFiles) {
  const typed = new Map(localFiles.map((file) => [file.id, file.caption]));
  return serverFiles.map((file) => ({ ...file, caption: typed.get(file.id) ?? file.caption ?? '' }));
}

// UC-67: anh dinh kem (JPG / PNG, toi da 10 anh, moi anh 10 MB); chon la tai len server ngay
export function ResultFiles({ files, readOnly, onChange }) {
  const inputRef = useRef(null);
  const [errors, setErrors] = useState([]);
  const [busy, setBusy] = useState(false);
  const { uploadImage, deleteImage } = useContext(ResultApiContext);

  // tai tung anh len server; anh loi thi bao loi, anh khac van tiep tuc
  const upload = async (accepted) => {
    setBusy(true);
    let current = files;
    const failed = [];
    for (const file of accepted) {
      try {
        const order = await uploadImage(file, '');
        current = withLocalCaptions(order?.result?.files ?? current, current);
        onChange(current);
      } catch (error) {
        failed.push(`${file.name}: ${resultErrorMessage(error)}`);
      }
    }
    setErrors((previous) => [...previous, ...failed]);
    setBusy(false);
  };

  // chon anh: kiem tra roi tai len
  const add = (event) => {
    const { accepted, errors: rejected } = checkFiles(event.target.files ?? [], files.length);
    setErrors(rejected);
    if (accepted.length > 0 && uploadImage) {
      void upload(accepted);
    } else if (accepted.length > 0) {
      onChange([
        ...files,
        ...accepted.map((file) => ({
          id: `file-${Date.now()}-${(nextId += 1)}`,
          name: file.name,
          type: file.type,
          size: file.size,
          url: previewUrl(file),
          caption: '',
        })),
      ]);
    }
    event.target.value = '';
  };

  const update = (id, patch) => onChange(files.map((file) => (file.id === id ? { ...file, ...patch } : file)));
  const remove = async (id) => {
    if (!deleteImage) {
      onChange(files.filter((file) => file.id !== id));
      return;
    }
    // xoa tren server truoc, thanh cong moi bo khoi danh sach
    setBusy(true);
    try {
      const order = await deleteImage(id);
      onChange(withLocalCaptions(order?.result?.files ?? files.filter((file) => file.id !== id), files));
      setErrors([]);
    } catch (error) {
      setErrors([resultErrorMessage(error)]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      {!readOnly && (
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={busy || files.length >= MAX_FILES}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-[#127e7a] bg-[#e6f5f3] hover:bg-[#d8efec] rounded-xl cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ImagePlus className="w-4 h-4" />
            {busy ? 'Đang tải ảnh…' : 'Thêm ảnh'}
          </button>
          <span className="text-xs text-slate-500">
            {files.length}/{MAX_FILES} ảnh · JPG, PNG · tối đa 10 MB mỗi ảnh
          </span>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept={ACCEPTED_TYPES.join(',')}
            onChange={add}
            className="hidden"
            aria-label="Chọn ảnh đính kèm"
          />
        </div>
      )}

      {errors.length > 0 && (
        <ul role="alert" className="text-xs text-rose-600 space-y-0.5">
          {errors.map((message) => <li key={message}>{message}</li>)}
        </ul>
      )}

      {files.length === 0 ? (
        <p className="text-sm text-slate-400">Chưa có ảnh đính kèm.</p>
      ) : (
        <ul className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
          {files.map((file, index) => (
            <li key={file.id} className="rounded-xl border border-slate-200 overflow-hidden bg-white">
              <div className="aspect-[4/3] bg-slate-900 flex items-center justify-center">
                {file.type.startsWith('image/') && file.url ? (
                  <img src={file.url} alt={file.caption || `Ảnh ${index + 1}`} className="max-h-full max-w-full object-contain" />
                ) : (
                  <FileText className="w-10 h-10 text-slate-300" />
                )}
              </div>
              <div className="p-2.5 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-medium text-slate-700 truncate" title={file.name}>{file.name}</span>
                  {file.size ? <span className="text-[11px] text-slate-400 shrink-0">{formatFileSize(file.size)}</span> : null}
                </div>
                {readOnly ? (
                  file.caption && <p className="text-xs text-slate-600">{file.caption}</p>
                ) : (
                  <div className="flex items-center gap-2">
                    <input
                      value={file.caption}
                      onChange={(event) => update(file.id, { caption: event.target.value })}
                      placeholder="Chú thích (vd: Hang vị)"
                      aria-label={`Chú thích ${file.name}`}
                      maxLength={255}
                      className="flex-1 min-w-0 px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs focus:outline-none focus:border-[#127e7a]"
                    />
                    <button
                      type="button"
                      onClick={() => void remove(file.id)}
                      disabled={busy}
                      aria-label={`Xóa ${file.name}`}
                      className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default ResultFiles;
