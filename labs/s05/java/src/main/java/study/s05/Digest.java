package study.s05;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * n8n 의 ④ Merge + ⑤ Code(Digest) 노드: 메일 · RSS 항목 수십 개를 문자열 하나로 합쳐 LLM 호출을 한 번으로 줄입니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s05.Digest   (이 단계만 실행해 결과 확인)
 */
public record Digest(String date, int mailCount, int feedCount, String digest) {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MAX_BODY = 500;    // 메일 한 통에서 쓸 최대 글자 수
    static final int MAX_MAILS = 30;    // 토큰 비용을 막는 상한
    static final Path DATA = Path.of(System.getProperty("data", "../python/data"));   // Python 트랙과 같은 샘플
    static final String FEED = System.getProperty("feed", DATA.resolve("feed.xml").toString());

    /** -Dnow=2026-11-13T07:30:00+09:00 이 있으면 그 시각, 없으면 지금(서울 시간). */
    static OffsetDateTime now() {
        String v = System.getProperty("now", System.getenv("BRIEF_NOW"));
        return v != null ? OffsetDateTime.parse(v) : OffsetDateTime.now(KST);
    }

    /** HTML 태그와 연속 공백을 지우고 limit 글자로 자릅니다. */
    static String squash(String text, int limit) {
        String s = (text == null ? "" : text).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip();
        return s.length() <= limit ? s : s.substring(0, limit);
    }

    public static Digest build(OffsetDateTime at) throws Exception {
        List<String> mails = new ArrayList<>();
        for (Inbox.Mail m : Inbox.mails(DATA.resolve("mails.json"), at)) {
            String subject = m.subject() == null || m.subject().isEmpty() ? "(제목 없음)" : m.subject();
            mails.add("- [" + m.from() + "] " + subject + "\n  " + squash(m.snippet(), MAX_BODY));
        }
        List<String> feeds = new ArrayList<>();
        for (Inbox.FeedItem f : Inbox.feed(FEED)) {
            feeds.add("- " + f.title() + " (" + f.link() + ")\n  " + squash(f.summary(), 300));
        }
        List<String> lines = new ArrayList<>();
        lines.add("## 받은 메일 " + mails.size() + "건");
        lines.addAll(mails.subList(0, Math.min(MAX_MAILS, mails.size())));
        lines.add("");
        lines.add("## RSS 새 글 " + feeds.size() + "건");
        lines.addAll(feeds);
        String date = at.atZoneSameInstant(KST).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return new Digest(date, mails.size(), feeds.size(), String.join("\n", lines));
    }

    public static void main(String[] args) throws Exception {
        Digest d = build(now());
        System.out.printf("%s 메일 %d건 · RSS %d건%n%n%s%n", d.date(), d.mailCount(), d.feedCount(), d.digest());
    }
}
