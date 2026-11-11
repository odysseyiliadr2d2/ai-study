package study.s07;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 문의 메일 처리: 에이전트 → 사람 승인 → 답장 파일 → 기록.
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s07.InquiryApp
 */
public final class InquiryApp {

    static final Path INBOX = Path.of("inbox.jsonl"), OUTBOX = Path.of("outbox");
    static final Path LOG = Path.of("log.csv"), DONE = Path.of("processed.txt"), ERRORS = Path.of("errors.log");
    static final String HEADER = "processed_at,mail_id,from,subject,category,urgency,faq_used,result";

    private final BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    /** 초안을 보여 주고 y 를 받아야만 true. 입력이 없거나(EOF) 다른 값이면 '직접 처리'. */
    boolean askApproval(MailAgent.Mail mail, MailAgent.Result r) throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("[승인 요청] %s  (%s/%s)%n", mail.subject(), r.category(), r.urgency());
        System.out.printf("보낸 사람: %s%n요약: %s%n", mail.from(), r.summary());
        System.out.printf("사람 확인 필요: %s  · 참고 FAQ: %s%n", r.needsHuman(),
                r.faqUsed() == null || r.faqUsed().isBlank() ? "없음" : r.faqUsed());
        System.out.println("---- 답장 초안 ----\n" + r.replyDraft());
        System.out.print("발송 승인? [y = 발송 승인 / 그 밖 = 직접 처리] ");
        String line = console.readLine();          // ... < /dev/null 처럼 무응답이면 null → 발송하지 않음
        return line != null && line.trim().equalsIgnoreCase("y");
    }

    /** 실습에서는 SMTP 대신 파일로 '발송'합니다. 운영에서는 이 메서드만 Jakarta Mail 발송으로 바꿉니다. */
    Path writeReply(MailAgent.Mail mail, MailAgent.Result r) throws IOException {
        Files.createDirectories(OUTBOX);
        Path path = OUTBOX.resolve(mail.id() + ".txt");
        Files.writeString(path, "To: " + mail.from() + "\nSubject: Re: " + mail.subject()
                + "\nIn-Reply-To: " + mail.threadId() + "\n\n" + r.replyDraft() + "\n", StandardCharsets.UTF_8);
        return path;
    }

    static String csv(String v) {
        String s = v == null ? "" : v;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    void log(MailAgent.Mail mail, MailAgent.Result r, String result) throws IOException {
        if (Files.notExists(LOG)) Files.writeString(LOG, "﻿" + HEADER + "\n", StandardCharsets.UTF_8);  // 엑셀용 BOM
        List<String> row = List.of(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), mail.id(),
                mail.from(), mail.subject(), r == null ? "" : r.category(), r == null ? "" : r.urgency(),
                r == null ? "" : r.faqUsed(), result);
        Files.writeString(LOG, String.join(",", row.stream().map(InquiryApp::csv).toList()) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    void run() throws IOException {
        ObjectMapper json = new ObjectMapper();
        MailAgent agent = new MailAgent(new FaqTool(Path.of("faq.csv")));
        Set<String> done = Files.exists(DONE) ? new HashSet<>(Files.readAllLines(DONE)) : new HashSet<>();

        for (String line : Files.readAllLines(INBOX, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            MailAgent.Mail mail = json.readValue(line, MailAgent.Mail.class);
            if (done.contains(mail.id())) continue;
            try {
                MailAgent.Run run = agent.run(mail);
                System.out.printf("%n[%s] 도구 호출 %d회: %s%n", mail.id(), run.toolCalls().size(), run.toolCalls());
                if (askApproval(mail, run.result())) {
                    System.out.println("→ 발송: " + writeReply(mail, run.result()));
                    log(mail, run.result(), "자동 발송");
                } else {
                    log(mail, run.result(), "수동 처리");
                }
            } catch (Exception e) {                // 이 메일만 실패로 남기고 다음 메일은 계속 (n8n 의 Error Workflow)
                Files.writeString(ERRORS, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " " + mail.id()
                        + " " + MailAgent.MODEL + " " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                log(mail, null, "오류");
                System.out.printf("[%s] 오류 → errors.log (%s)%n", mail.id(), e.getClass().getSimpleName());
                continue;                          // processed.txt 에 넣지 않으므로 다음 실행에서 다시 처리
            }
            Files.writeString(DONE, mail.id() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    public static void main(String[] args) throws IOException {
        new InquiryApp().run();
    }
}
