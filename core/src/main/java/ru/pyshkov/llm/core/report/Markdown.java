package ru.pyshkov.llm.core.report;

import java.util.List;

/** Минимальные помощники для Markdown-отчётов. */
public final class Markdown {

    private Markdown() {
    }

    public static String table(List<String> headers, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        out.append("| ");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                out.append(" | ");
            }
            out.append(escape(headers.get(i)));
        }
        out.append(" |\n|");
        for (int i = 0; i < headers.size(); i++) {
            out.append("---|");
        }
        out.append('\n');
        for (List<String> row : rows) {
            out.append("| ");
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) {
                    out.append(" | ");
                }
                out.append(escape(i < row.size() ? row.get(i) : ""));
            }
            out.append(" |\n");
        }
        return out.toString();
    }

    public static String code(String language, String text) {
        return "```" + language + "\n" + text + "\n```\n";
    }

    private static String escape(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>");
    }
}
