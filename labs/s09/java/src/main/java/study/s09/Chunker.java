package study.s09;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 1단계: 문서를 청크로 자르기 — ... -Dexec.mainClass=study.s09.Chunker -Dexec.args=docs > chunks.jsonl */
public final class Chunker {

    static final int MAX_CHARS = 600;   // 청크 최대 길이
    static final int OVERLAP = 100;     // 앞 청크와 겹치는 글자 수
    static final Pattern HEADING = Pattern.compile("^#{1,3} (.+)$");

    record Section(String title, String body) {}

    /** 마크다운 제목(#) 기준으로 큰 덩어리를 나눕니다. */
    static List<Section> splitSections(String text) {
        List<Section> out = new ArrayList<>();
        String title = "";
        StringBuilder buf = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                if (!buf.toString().isBlank()) out.add(new Section(title, buf.toString().strip()));
                title = m.group(1).strip();
                buf.setLength(0);
            } else {
                buf.append(line).append('\n');
            }
        }
        if (!buf.toString().isBlank()) out.add(new Section(title, buf.toString().strip()));
        return out;
    }

    /** 긴 덩어리는 MAX_CHARS 단위로 자르되 OVERLAP 만큼 겹칩니다. */
    static List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS, text.length());
            out.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - OVERLAP;
        }
        return out;
    }

    public static void main(String[] args) throws IOException {
        Path folder = Path.of(args.length > 0 ? args[0] : "docs");
        ObjectMapper json = new ObjectMapper();
        List<Path> files;
        try (Stream<Path> s = Files.walk(folder)) {
            files = s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
        for (Path path : files) {
            String name = path.getFileName().toString();
            String stem = name.substring(0, name.length() - 3);
            for (Section sec : splitSections(Files.readString(path, StandardCharsets.UTF_8))) {
                List<String> pieces = chunk(sec.body());
                for (int i = 0; i < pieces.size(); i++) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("doc", name);
                    rec.put("section", sec.title());
                    rec.put("part", i);
                    // 제목을 본문 앞에 붙이면 검색이 훨씬 잘 됩니다
                    rec.put("text", "[" + stem + " > " + sec.title() + "] " + pieces.get(i));
                    System.out.println(json.writeValueAsString(rec));
                }
            }
        }
    }
}
