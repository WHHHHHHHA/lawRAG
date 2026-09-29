package com.epint.ztb.aiks.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.epint.ztb.aiks.docinput.DocumentIngestService;
import com.epint.ztb.aiks.dto.ChatDtos.ReferenceItem;

/**
 * 引用解析：将回答中的〔n〕标注映射为结构化 references，
 * 未被回答引用的片段不进 references（引用号超出范围也丢弃）。
 */
@Component
public class CitationResolver {

    /** 回答中的引用形式：〔1〕（全角方括号，与系统提示词约定一致） */
    private static final Pattern CITATION_PATTERN = Pattern.compile("〔(\\d+)〕");

    /** 片段截断长度 */
    private static final int SNIPPET_MAX = 200;

    public List<ReferenceItem> resolve(String answer, List<Document> fragments) {
        Set<Integer> citedNos = new LinkedHashSet<>();
        Matcher m = CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (m.find()) {
            citedNos.add(Integer.parseInt(m.group(1)));
        }
        List<ReferenceItem> references = new ArrayList<>();
        for (Integer no : citedNos) {
            if (no < 1 || no > fragments.size()) {
                continue;
            }
            references.add(toReference(no, fragments.get(no - 1)));
        }
        return references;
    }

    private ReferenceItem toReference(int refNo, Document d) {
        String snippet = d.getText() == null ? "" : d.getText().trim();
        if (snippet.length() > SNIPPET_MAX) {
            snippet = snippet.substring(0, SNIPPET_MAX) + "…";
        }
        return new ReferenceItem(refNo,
                str(d, DocumentIngestService.META_DOC_GUID),
                str(d, DocumentIngestService.META_LAW_NAME),
                str(d, DocumentIngestService.META_ARTICLE_NO),
                str(d, DocumentIngestService.META_CHAPTER_NAME),
                snippet,
                d.getScore());
    }

    private String str(Document d, String key) {
        Object v = d.getMetadata() != null ? d.getMetadata().get(key) : null;
        return v != null ? v.toString() : null;
    }
}
