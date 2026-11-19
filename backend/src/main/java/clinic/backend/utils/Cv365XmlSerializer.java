// chuyen sang xml
package clinic.backend.utils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Tiện ích tuần tự hóa (serializer) dữ liệu trao đổi hồ sơ bệnh án sang định dạng XML chuẩn.
 * Tuân thủ theo Công văn 365/TTYQG-GPQLCL - Phụ lục "Mô tả dữ liệu trao đổi hồ sơ bệnh án điện tử".
 * Chuyển đổi cấu trúc cây Map/List sang tài liệu DOM XML chuẩn UTF-8, thụt lề 2 khoảng trắng.
 */
public final class Cv365XmlSerializer {

    /**
     * Private constructor để ngăn việc khởi tạo đối tượng từ bên ngoài (lớp tiện ích tĩnh - utility class).
     */
    private Cv365XmlSerializer() {
    }

    /**
     * Chuyển đổi cấu trúc dữ liệu Map (gồm DuLieuChu, HoSoBenhAn, extensions) thành mảng byte XML chuẩn CV 365.
     * Cấu trúc thẻ gốc là <HoSoBenhAn>, bên trong lồng thẻ <DuLieuChu> và các nhóm phần mục lâm sàng.
     *
     * @param root Map chứa toàn bộ dữ liệu gói lưu trữ hồ sơ bệnh án
     * @return Mảng byte chứa nội dung XML đã được format (indent 2 khoảng trắng, mã hóa UTF-8)
     * @throws IllegalStateException Nếu xảy ra lỗi phân tích hoặc xuất XML
     */
    public static byte[] serialize(Map<String, Object> root) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element rootElement = doc.createElement("HoSoBenhAn");
            doc.appendChild(rootElement);

            Object hoSoBenhAn = root.get("HoSoBenhAn");
            if (hoSoBenhAn instanceof Map<?, ?> hoSoMap) {
                if (root.get("DuLieuChu") instanceof Map<?, ?> duLieuChu) {
                    Element duLieuChuEl = doc.createElement("DuLieuChu");
                    rootElement.appendChild(duLieuChuEl);
                    appendMap(doc, duLieuChuEl, duLieuChu);
                }
                appendMap(doc, rootElement, hoSoMap);
                if (root.get("extensions") instanceof Map<?, ?> ext) {
                    Element extEl = doc.createElement("extensions");
                    rootElement.appendChild(extEl);
                    appendMap(doc, extEl, ext);
                }
            } else {
                appendMap(doc, rootElement, root);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize medical record to XML", ex);
        }
    }

    /**
     * Đệ quy chuyển đổi từng phần tử trong Map thành các phần tử (Element) XML tương ứng và gắn vào thẻ cha.
     * Xử lý trường hợp giá trị con là List (tạo nhiều thẻ cùng tên), Map (tạo thẻ lồng nhau), hoặc kiểu nguyên thủy.
     *
     * @param doc Tài liệu XML đang xây dựng (W3C Document)
     * @param parent Thẻ XML cha để gắn các thẻ con vào
     * @param map Dữ liệu dạng Map cần chuyển đổi
     */
    @SuppressWarnings("unchecked")
    private static void appendMap(Document doc, Element parent, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = sanitizeXmlTagName(String.valueOf(entry.getKey()));
            Object value = entry.getValue();

            if (value instanceof List<?> list) {
                for (Object item : list) {
                    Element itemEl = doc.createElement(key);
                    parent.appendChild(itemEl);
                    if (item instanceof Map<?, ?> subMap) {
                        appendMap(doc, itemEl, subMap);
                    } else if (item != null) {
                        itemEl.setTextContent(String.valueOf(item));
                    }
                }
            } else if (value instanceof Map<?, ?> subMap) {
                Element subEl = doc.createElement(key);
                parent.appendChild(subEl);
                appendMap(doc, subEl, subMap);
            } else {
                Element el = doc.createElement(key);
                parent.appendChild(el);
                if (value != null) {
                    el.setTextContent(String.valueOf(value));
                }
            }
        }
    }

    /**
     * Chuẩn hóa tên khóa thành tên thẻ XML hợp lệ theo quy chuẩn W3C:
     * - Thay thế các ký tự không hợp lệ bằng dấu gạch dưới '_'.
     * - Thêm tiền tố '_' nếu tên bắt đầu bằng số, dấu gạch nối '-' hoặc dấu chấm '.'.
     * - Trả về "item" nếu chuỗi null hoặc rỗng.
     *
     * @param name Tên khóa gốc từ Map
     * @return Tên thẻ XML hợp lệ
     */
    private static String sanitizeXmlTagName(String name) {
        if (name == null || name.isBlank()) {
            return "item";
        }
        String clean = name.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (Character.isDigit(clean.charAt(0)) || clean.charAt(0) == '-' || clean.charAt(0) == '.') {
            clean = "_" + clean;
        }
        return clean;
    }
}

