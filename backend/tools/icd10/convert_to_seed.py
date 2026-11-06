"""
Chuyển file Excel/CSV danh mục chính thức (tải từ nguồn nhà nước) sang định dạng seed:
    ma, ten, ma_cha, ghi_chu
Ví dụ:
  # Nghề nghiệp (QĐ 34/2020): mã nằm rải ở các cột Cấp 1..Cấp 5, chỉ lấy mã 2 chữ số (cấp 2)
  python convert_to_seed.py Danh-muc-nghe-nghiep-Viet-Nam.xlsx nghe_nghiep.csv \
      --code-cols "Cấp 1,Cấp 2,Cấp 3,Cấp 4,Cấp 5" --name-col "Tên gọi nghề nghiệp" \
      --keep-len 2 --parent-len 1 --append-existing

  # ĐVHC cấp xã: một cột mã xã, một cột tên xã, một cột mã tỉnh
  python convert_to_seed.py danh_sach_xa.xlsx dvhc_xa.csv \
      --code-cols "Mã" --name-col "Tên" --parent-col "Mã TP"

  # ICD-10 (TT 06/2026): lấy cột mã bệnh và tên bệnh
  python convert_to_seed.py icd10.xlsx icd10.csv --code-cols "Mã bệnh" --name-col "Tên bệnh"
Tên cột phải khớp đúng tiêu đề trong file gốc (mở file xem trước). Script tự dò dòng tiêu đề.
"""
import argparse, csv, sys
import openpyxl

def read_rows(path, sheet):
    if path.lower().endswith('.csv'):
        with open(path, encoding='utf-8-sig') as f:
            return [r for r in csv.reader(f)]
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb[sheet] if sheet else wb.worksheets[0]
    return [['' if c is None else str(c).strip() for c in r] for r in ws.iter_rows(values_only=True)]

def norm(s): return ' '.join(str(s).split()).lower()

def main():
    p = argparse.ArgumentParser()
    p.add_argument('src'); p.add_argument('dst')
    p.add_argument('--sheet')
    p.add_argument('--code-cols', required=True, help='1 hoặc nhiều cột mã, lấy giá trị khác rỗng đầu tiên từ phải sang (có --keep-len thì lấy mã đúng độ dài)')
    p.add_argument('--name-col', required=True)
    p.add_argument('--parent-col', help='cột mã cha (nếu có)')
    p.add_argument('--note-col', help='cột chép sang ghi_chu (nếu có)')
    p.add_argument('--keep-len', type=int, help='chỉ giữ mã có đúng độ dài này')
    p.add_argument('--parent-len', type=int, help='suy mã cha = N ký tự đầu của mã')
    p.add_argument('--zfill', type=int, help='bù số 0 bên trái cho mã số (vd 2 cho mã tỉnh)')
    p.add_argument('--append-existing', action='store_true', help='giữ các dòng đã có trong dst (vd mã 00)')
    a = p.parse_args()

    rows = read_rows(a.src, a.sheet)
    code_cols = [c.strip() for c in a.code_cols.split(',')]
    hdr_i = next((i for i, r in enumerate(rows[:50]) if norm(a.name_col) in [norm(x) for x in r]), None)
    if hdr_i is None: sys.exit(f'Không tìm thấy cột "{a.name_col}" trong 50 dòng đầu.')
    hdr = [norm(x) for x in rows[hdr_i]]
    try:
        ci = [hdr.index(norm(c)) for c in code_cols]; ni = hdr.index(norm(a.name_col))
        pi = hdr.index(norm(a.parent_col)) if a.parent_col else None
        oi = hdr.index(norm(a.note_col)) if a.note_col else None
    except ValueError as e: sys.exit(f'Thiếu cột: {e}. Tiêu đề tìm thấy: {rows[hdr_i]}')

    out, seen, errs = [], set(), []
    if a.append_existing:
        try:
            with open(a.dst, encoding='utf-8-sig') as f:
                for r in list(csv.reader(f))[1:]:
                    out.append(r); seen.add(r[0])
        except FileNotFoundError: pass
    for n, r in enumerate(rows[hdr_i + 1:], start=hdr_i + 2):
        r = r + [''] * (len(hdr) - len(r))
        name = ' '.join(r[ni].split())  # gộp khoảng trắng/xuống dòng thừa trong tên (vd "Phường \nHoàn Kiếm")
        cands = []
        for i in reversed(ci):
            c = r[i]
            if not c: continue
            if c.endswith('.0') and c[:-2].isdigit(): c = c[:-2]  # chỉ sửa số kiểu Excel (1.0 -> 1), không đụng mã ICD như A00.0
            if a.zfill and c.isdigit(): c = c.zfill(a.zfill)
            cands.append(c)
        # Một dòng có thể mang nhiều cấp mã (vd "44 | 440" khi nhóm chỉ có 1 nhóm con):
        # nếu có --keep-len thì chọn mã đúng độ dài đó, không thì lấy mã ngoài cùng bên phải.
        if a.keep_len: cands = [c for c in cands if len(c) == a.keep_len]
        code = cands[0] if cands else ''
        if not code or not name: continue
        parent = r[pi] if pi is not None else (code[:a.parent_len] if a.parent_len else '')
        if code in seen: errs.append(f'dòng {n}: mã trùng {code}'); continue
        note = r[oi] if oi is not None else ''
        seen.add(code); out.append([code, name, parent, note])
    with open(a.dst, 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f); w.writerow(['ma', 'ten', 'ma_cha', 'ghi_chu']); w.writerows(out)
    print(f'Ghi {len(out)} dòng vào {a.dst}')
    for e in errs[:20]: print('  CẢNH BÁO', e)

if __name__ == '__main__': main()
