// 의존성 없이 Java 소스의 '문법'만 검사합니다 (import 대상 라이브러리가 없어도 됨).
//   java tools/JavaSyntax.java 파일1.java 파일2.java ...
// 문법 오류가 있으면 위치와 함께 출력하고 종료 코드 1.
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.util.*;

public class JavaSyntax {
    public static void main(String[] args) throws Exception {
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> d = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = c.getStandardFileManager(d, null, null)) {
            var units = fm.getJavaFileObjects(args);
            JavacTask t = (JavacTask) c.getTask(null, fm, d, List.of("-proc:none", "--release", "21"), null, units);
            t.parse();
        }
        int n = 0;
        for (var x : d.getDiagnostics()) if (x.getKind() == Diagnostic.Kind.ERROR) {
            n++; System.out.println(x.getSource().getName() + ":" + x.getLineNumber() + ": " + x.getMessage(Locale.ENGLISH));
        }
        System.out.println(n == 0 ? "syntax OK (" + args.length + " files)" : "syntax errors: " + n);
        System.exit(n == 0 ? 0 : 1);
    }
}
