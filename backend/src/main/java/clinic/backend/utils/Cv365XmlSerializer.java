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
 * Serializes CV 365 medical record exchange structures to standard XML bytes.
 * Conforms to Công văn 365/TTYQG-GPQLCL Phụ lục "Mô tả dữ liệu trao đổi hồ sơ bệnh án điện tử".
 */
public final class Cv365XmlSerializer {

    private Cv365XmlSerializer() {
    }

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

