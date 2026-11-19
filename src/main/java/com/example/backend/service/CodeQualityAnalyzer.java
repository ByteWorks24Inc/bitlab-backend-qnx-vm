package com.example.backend.service;

import com.example.backend.dto.CodeQualityReport;
import com.example.backend.dto.CodeQualityReport.Finding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic quality analysis for the C code typed in the QNX lab.
 *
 * The score starts at 100 and deductions are applied for correctness,
 * safety, memory and style findings. It is intentionally deterministic and
 * fast so the frontend can call it on every (debounced) keystroke without
 * needing the VM or the compiler.
 */
@Service
public class CodeQualityAnalyzer {

    private static final Pattern FUNCTION_DEF = Pattern.compile(
            "(?m)^[ \\t]*(?:static\\s+|inline\\s+|extern\\s+)*"
                    + "(?:(?:unsigned|signed|const|volatile|register)\\s+)*"
                    + "(?:(?:struct|enum|union)\\s+\\w+\\s*)?"
                    + "(?:int|void|char|float|double|long|short|size_t|ssize_t|bool|"
                    + "uint\\d+_t|int\\d+_t)\\s+\\**\\w+\\s*\\([^;{}]*\\)\\s*\\{");

    private static final Pattern MAIN = Pattern.compile("\\bint\\s+main\\s*\\(");
    private static final Pattern RETURNS = Pattern.compile("\\breturn\\b");
    private static final Pattern INCLUDE_STDIO = Pattern.compile("#\\s*include\\s*<stdio\\.h>");
    private static final Pattern INCLUDE_STDLIB = Pattern.compile("#\\s*include\\s*<stdlib\\.h>");
    private static final Pattern IO_USE = Pattern.compile(
            "\\b(printf|fprintf|sprintf|snprintf|puts|putchar|scanf|fscanf|sscanf|gets|fgets|perror)\\s*\\(");
    private static final Pattern GETS = Pattern.compile("\\bgets\\s*\\(");
    private static final Pattern UNSAFE_STR = Pattern.compile("\\b(strcpy|strcat|sprintf)\\s*\\(");
    private static final Pattern SCANF_STRING = Pattern.compile("\\bscanf\\s*\\([^;\\n]*%s");
    private static final Pattern SYSTEM_CALL = Pattern.compile("\\bsystem\\s*\\(");
    private static final Pattern GOTO = Pattern.compile("\\bgoto\\s+\\w+\\s*;");
    private static final Pattern FFLUSH_STDIN = Pattern.compile("\\bfflush\\s*\\(\\s*stdin\\s*\\)");
    private static final Pattern ALLOC = Pattern.compile("\\b(malloc|calloc|realloc)\\s*\\(");
    private static final Pattern FREE = Pattern.compile("\\bfree\\s*\\(");
    private static final Pattern TODO = Pattern.compile("\\b(TODO|FIXME|XXX)\\b");

    private static final int MAX_FINDINGS_PER_RULE = 5;

    public CodeQualityReport analyze(String source) {
        String code = source == null ? "" : source;
        String[] lines = code.split("\n", -1);

        if (code.isBlank()) {
            return CodeQualityReport.builder()
                    .score(0)
                    .grade("No code")
                    .lines(0)
                    .functions(0)
                    .commentRatio(0)
                    .maxNesting(0)
                    .build();
        }

        int codeLines = 0;
        int commentLines = 0;
        int longLines = 0;
        int trailingLines = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            boolean comment = inBlockComment;
            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
            } else if (trimmed.startsWith("//")) {
                comment = true;
            } else if (trimmed.startsWith("/*")) {
                comment = true;
                if (!trimmed.contains("*/")) inBlockComment = true;
            }

            if (comment) commentLines++;
            else codeLines++;

            if (line.length() > 100) longLines++;
            if (!line.equals(line.stripTrailing())) trailingLines++;
        }

        double commentRatio = lines.length == 0
                ? 0
                : Math.round(commentLines * 1000.0 / lines.length) / 10.0;

        String stripped = stripCommentsAndStrings(code);

        List<Finding> findings = new ArrayList<>();
        int deduction = 0;

        // ── Correctness ─────────────────────────────────────────────
        if (count(stripped, '{') != count(stripped, '}')) {
            findings.add(finding("error", "syntax-braces",
                    "Unbalanced braces — a block is not closed.", null));
            deduction += 20;
        }

        Matcher main = MAIN.matcher(stripped);
        boolean hasMain = main.find();
        if (!hasMain) {
            findings.add(finding("error", "missing-main",
                    "No int main() entry point found.", null));
            deduction += 15;
        } else if (!mainBodyHasReturn(stripped, main.start())) {
            findings.add(finding("warning", "missing-return",
                    "main() ends without a return statement.", null));
            deduction += 5;
        }

        if (IO_USE.matcher(stripped).find() && !INCLUDE_STDIO.matcher(code).find()) {
            findings.add(finding("error", "missing-stdio",
                    "printf/scanf are used but <stdio.h> is not included.", null));
            deduction += 12;
        }

        boolean usesAlloc = ALLOC.matcher(stripped).find() || FREE.matcher(stripped).find();
        if (usesAlloc && !INCLUDE_STDLIB.matcher(code).find()) {
            findings.add(finding("warning", "missing-stdlib",
                    "malloc/free are used but <stdlib.h> is not included.", null));
            deduction += 6;
        }

        // ── Safety ──────────────────────────────────────────────────
        int getsLines = addLineFindings(findings, lines, GETS, "error", "unsafe-gets",
                "gets() cannot bound its input — use fgets(buf, size, stdin).", MAX_FINDINGS_PER_RULE);
        if (getsLines > 0) deduction += 15;

        int unsafeLines = addLineFindings(findings, lines, UNSAFE_STR, "warning", "unsafe-string",
                "Unbounded string function — prefer the size-limited variant.", MAX_FINDINGS_PER_RULE);
        if (unsafeLines > 0) deduction += 6;

        int scanfLines = addLineFindings(findings, lines, SCANF_STRING, "warning", "unbounded-scanf",
                "scanf(\"%s\") has no field width — use %<width>s.", MAX_FINDINGS_PER_RULE);
        if (scanfLines > 0) deduction += 6;

        int systemLines = addLineFindings(findings, lines, SYSTEM_CALL, "warning", "system-call",
                "system() runs a shell command — validate or avoid it.", MAX_FINDINGS_PER_RULE);
        if (systemLines > 0) deduction += 5;

        // ── Memory ──────────────────────────────────────────────────
        int allocations = countMatches(stripped, ALLOC);
        int frees = countMatches(stripped, FREE);
        if (allocations > frees) {
            findings.add(finding("warning", "memory-leak",
                    allocations + " allocation(s) but only " + frees
                            + " free(s) — allocated memory may leak.", null));
            deduction += 8;
        }

        // ── Style / structure ───────────────────────────────────────
        int gotoLines = addLineFindings(findings, lines, GOTO, "warning", "goto",
                "goto makes control flow harder to follow.", MAX_FINDINGS_PER_RULE);
        if (gotoLines > 0) deduction += 4;

        int fflushLines = addLineFindings(findings, lines, FFLUSH_STDIN, "warning", "fflush-stdin",
                "fflush(stdin) is undefined behaviour — read and discard instead.", MAX_FINDINGS_PER_RULE);
        if (fflushLines > 0) deduction += 4;

        int maxNesting = maxNesting(stripped);
        if (maxNesting > 5) {
            findings.add(finding("warning", "deep-nesting",
                    "Nesting depth " + maxNesting + " — split this logic into helpers.", null));
            deduction += 8;
        } else if (maxNesting > 3) {
            findings.add(finding("warning", "deep-nesting",
                    "Nesting depth " + maxNesting + " (recommended maximum is 3).", null));
            deduction += 4;
        }

        int todoLines = addLineFindings(findings, lines, TODO, "info", "todo",
                "Unresolved TODO/FIXME marker.", MAX_FINDINGS_PER_RULE);
        if (todoLines > 0) deduction += Math.min(6, todoLines * 2);

        if (codeLines >= 8 && commentRatio < 10) {
            findings.add(finding("info", "low-comments",
                    "Only " + commentRatio + "% of lines are comments — explain the tricky parts.", null));
            deduction += 4;
        }

        int longLineCount = 0;
        int trailingCount = 0;
        for (int i = 0; i < lines.length && (longLineCount < MAX_FINDINGS_PER_RULE
                || trailingCount < MAX_FINDINGS_PER_RULE); i++) {
            String line = lines[i];
            if (line.length() > 100 && longLineCount < MAX_FINDINGS_PER_RULE) {
                longLineCount++;
                findings.add(finding("info", "long-line",
                        "Line is " + line.length() + " characters (limit 100).", i + 1));
            }
            if (!line.equals(line.stripTrailing()) && !line.isEmpty()
                    && trailingCount < MAX_FINDINGS_PER_RULE) {
                trailingCount++;
                findings.add(finding("info", "trailing-whitespace",
                        "Trailing whitespace.", i + 1));
            }
        }
        deduction += Math.min(5, longLineCount);
        deduction += Math.min(3, trailingCount);

        // ── Score ───────────────────────────────────────────────────
        int score = Math.max(0, Math.min(100, 100 - deduction));
        String grade = score >= 90 ? "Excellent"
                : score >= 75 ? "Good"
                : score >= 60 ? "Fair"
                : score >= 40 ? "Needs work"
                : "Poor";

        findings.sort(Comparator
                .comparingInt((Finding f) -> severityRank(f.getSeverity()))
                .thenComparing(f -> f.getLine() == null ? Integer.MAX_VALUE : f.getLine()));

        int errors = (int) findings.stream().filter(f -> "error".equals(f.getSeverity())).count();
        int warnings = (int) findings.stream().filter(f -> "warning".equals(f.getSeverity())).count();
        int infos = (int) findings.stream().filter(f -> "info".equals(f.getSeverity())).count();

        return CodeQualityReport.builder()
                .score(score)
                .grade(grade)
                .lines(lines.length)
                .functions(countFunctions(code))
                .commentRatio(commentRatio)
                .maxNesting(maxNesting)
                .errorCount(errors)
                .warningCount(warnings)
                .infoCount(infos)
                .findings(findings)
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Finding finding(String severity, String rule, String message, Integer line) {
        return Finding.builder().severity(severity).rule(rule).message(message).line(line).build();
    }

    private int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private int addLineFindings(List<Finding> findings, String[] lines, Pattern pattern,
                                String severity, String rule, String message, int cap) {
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                count++;
                if (count <= cap) {
                    findings.add(finding(severity, rule, message, i + 1));
                }
            }
        }
        return count;
    }

    private int countFunctions(String code) {
        Matcher matcher = FUNCTION_DEF.matcher(code);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /** True when the braces of the function starting at {@code mainStart} contain a return. */
    private boolean mainBodyHasReturn(String stripped, int mainStart) {
        int open = stripped.indexOf('{', mainStart);
        if (open < 0) return false;
        int depth = 0;
        for (int i = open; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return RETURNS.matcher(stripped.substring(open, i)).find();
                }
            }
        }
        return false;
    }

    private int maxNesting(String stripped) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') {
                depth++;
                if (depth > max) max = depth;
            } else if (c == '}') {
                if (depth > 0) depth--;
            }
        }
        return max;
    }

    private int count(String text, char needle) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == needle) count++;
        }
        return count;
    }

    private int countMatches(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /**
     * Blanks out comments, string literals and character literals while
     * preserving newlines, so structural checks are not fooled by braces or
     * keywords inside strings.
     */
    private String stripCommentsAndStrings(String code) {
        StringBuilder out = new StringBuilder(code.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append('\n');
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    out.append("  ");
                    i++;
                    inBlockComment = false;
                } else {
                    out.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (inString || inChar) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if ((inString && c == '"') || (inChar && c == '\'')) {
                    inString = false;
                    inChar = false;
                }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }
            if (c == '/' && next == '/') {
                inLineComment = true;
                out.append("  ");
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append("  ");
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(' ');
            } else if (c == '\'') {
                inChar = true;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
