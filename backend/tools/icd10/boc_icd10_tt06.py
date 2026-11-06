"""Bóc Phụ lục TT 06/2026/TT-BYT (bảng 29 cột, 1.271 trang) từ PDF chính thức thành CSV trung gian cho convert_to_seed.py.
  curl -O https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/4/06-byt-kem.pdf
  python boc_icd10_tt06.py 06-byt-kem.pdf icd_tt06.csv          # ~10-15 phút, có thể chạy lại để tiếp tục
  python convert_to_seed.py icd_tt06.csv ../../src/main/resources/db/reference/icd10.csv --code-cols "Mã bệnh" --name-col "Tên bệnh" --parent-col "Mã cha" --note-col "Ghi chú"
Cột dùng (theo Điều 4 TT 06): 18 mã bệnh, 22 tên bệnh tiếng Việt, 15 mã nhóm 3 ký tự (làm ma_cha), 24-29 cờ quy tắc.
Kết quả lần chạy dùng cho repo: 15.844 mã. Backend tự tính lại mã cha của mã 5 ký tự (B18.00 -> B18.0) khi import.
Cần: pip install pdfplumber
"""
import sys, os, json, re, csv, pdfplumber
src, dst = sys.argv[1], sys.argv[2]
os.makedirs('chunks', exist_ok=True)
with pdfplumber.open(src) as pdf:
    n = len(pdf.pages)
    for s in range(0, n, 20):
        f = f'chunks/{s:04d}.json'
        if os.path.exists(f): continue
        out = []
        for i in range(s, min(s + 20, n)):
            p = pdf.pages[i]
            for tb in p.extract_tables():
                out += [(i + 1, [(c or '') for c in r]) for r in tb]
            p.flush_cache()
        # encoding='utf-8': the Windows default (cp1252) cannot hold Vietnamese text.
        with open(f, 'w', encoding='utf-8') as fh:
            json.dump(out, fh, ensure_ascii=False)
        print('trang', s + 1, '-', min(s + 20, n), flush=True)
cl = lambda s: ' '.join(s.split())
recs, skipped = [], 0
for f in sorted(os.listdir('chunks')):
    with open('chunks/' + f, encoding='utf-8') as fh:
        chunk = json.load(fh)
    for pg, r in chunk:
        if len(r) != 29:
            skipped += any(x.strip() for x in r); continue                               # bảng không đủ 29 cột
        if r[1].strip() == '2' and r[2].strip() == '3': continue                         # bỏ dòng đánh số cột
        if r[0].strip().isdigit(): recs.append(r)
        elif not r[0].strip() and recs and any(x.strip() for x in r):              # ô bị ngắt sang trang sau
            recs[-1] = [(a + '\n' + b).strip() if b.strip() else a for a, b in zip(recs[-1], r)]
stt = [int(r[0]) for r in recs]; assert stt == list(range(1, len(stt) + 1)), 'STT không liên tục'
# STT liên tục không phát hiện được dòng mất ở cuối: in ra để đối chiếu với STT cuối của Phụ lục.
print('STT cuối:', stt[-1], '| dòng bị bỏ vì không đủ 29 cột:', skipped)
FL = [(23, 'c24_khong_dung_benh_chinh'), (24, 'c25_khong_khuyen_khich_benh_chinh'), (25, 'c26_co_ma_chi_tiet_hon'),
      (26, 'c27_chi_ma_tu_vong'), (27, 'c28_nu'), (28, 'c29_nam')]
rows = []
for c in recs:
    raw = cl(c[17]); code = raw.rstrip('†*'); notes = []
    if raw.endswith('†'): notes.append('dao_gam')
    if raw.endswith('*'): notes.append('dau_sao')
    if code == 'U13/9':  # lỗi gõ trong bản gốc (cột 19 = U139, tên bệnh ghi U13.9)
        code = 'U13.9'; notes.append('bản gốc in "U13/9" ở cột 18, sửa theo cột 19 (U139) và tên bệnh')
    assert re.fullmatch(r'[A-Z]\d\d(\.\d{1,2})?', code), code
    grp = cl(c[14]).rstrip('†*')
    notes += [k for i, k in FL if cl(c[i])]
    rows.append({'Mã bệnh': code, 'Tên bệnh': cl(c[21]), 'Mã cha': grp if '.' in code else '', 'Ghi chú': '|'.join(notes)})
with open(dst, 'w', newline='', encoding='utf-8-sig') as f:
    w = csv.DictWriter(f, list(rows[0])); w.writeheader(); w.writerows(rows)
print(len(rows), 'dòng ->', dst)
