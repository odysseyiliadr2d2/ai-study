package study.s05;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** n8n 의 ② Gmail · ③ RSS Read 노드 대신: 메일과 RSS 항목을 읽어 옵니다. */
public final class Inbox {

    /** Gmail 노드(Simplify 켬) 출력과 같은 필드 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mail(String from, String subject, String snippet, String received, String category) {}

    public record FeedItem(String title, String link, String summary) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private Inbox() {}

    /** 지난 24시간에 받은 메일, 프로모션 제외 (n8n 의 Received After · -category:promotions). */
    public static List<Mail> mails(Path file, OffsetDateTime at) throws IOException {
        List<Mail> all = JSON.readValue(file.toFile(), new TypeReference<List<Mail>>() {});
        OffsetDateTime since = at.minusHours(24);
        return all.stream()
                .filter(m -> !"promotions".equals(m.category()))
                .filter(m -> {
                    OffsetDateTime t = OffsetDateTime.parse(m.received());
                    return !t.isBefore(since) && !t.isAfter(at);
                })
                .toList();
    }

    /** RSS 2.0 을 JDK 내장 XML 파서로 읽습니다. 주소(http…)와 파일 경로를 모두 받습니다. Atom 까지 다뤄야 하면 Rome 라이브러리를 씁니다. */
    public static List<FeedItem> feed(String source) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // 외부 엔터티(XXE) 차단
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = source.startsWith("http") ? builder.parse(source) : builder.parse(new File(source));

        List<FeedItem> items = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            items.add(new FeedItem(child(e, "title"), child(e, "link"), child(e, "description")));
        }
        return items;
    }

    private static String child(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().strip();
    }
}
