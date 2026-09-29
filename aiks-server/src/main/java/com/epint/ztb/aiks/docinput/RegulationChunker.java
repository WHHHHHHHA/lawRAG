package com.epint.ztb.aiks.docinput;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import com.epint.ztb.aiks.config.AiksProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 法规文本分块器。
 *
 * 优先按法规条款结构分块："第X章"记录章节归属，"第X条"作为天然切分点，
 * 每个分块携带条款号（ArticleNo）与章名（ChapterName）元数据，供问答时引用出处。
 * 非法规结构文本（通知、解读等）退化为 TokenTextSplitter 兜底分块。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegulationChunker {

    /** 匹配"第X章 标题"行 */
    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile("(?m)^\\s{0,8}第[一二三四五六七八九十百千零〇0-9]+章[^\\n]{0,100}");

    /** 匹配"第X条"行首（捕获条款号） */
    private static final Pattern ARTICLE_PATTERN =
            Pattern.compile("(?m)^\\s{0,8}(第[一二三四五六七八九十百千零〇0-9]+条)");

    private final AiksProperties props;

    public List<Chunk> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        // 收集条款切分点：{条款起始位置, 条款号}
        List<int[]> articleStarts = new ArrayList<>();
        List<String> articleNos = new ArrayList<>();
        Matcher m = ARTICLE_PATTERN.matcher(normalized);
        while (m.find()) {
            articleStarts.add(new int[] {m.start(), m.end()});
            articleNos.add(m.group(1));
        }

        // 条款数不足视为非法规结构文本，走兜底分块
        if (articleStarts.size() < 2) {
            log.info("未识别出法规条款结构（条款数={}），使用 TokenTextSplitter 兜底分块", articleStarts.size());
            return fallbackChunk(normalized);
        }

        // 收集章标题位置：{章起始位置, 章结束位置}
        List<int[]> chapterPos = new ArrayList<>();
        Matcher cm = CHAPTER_PATTERN.matcher(normalized);
        while (cm.find()) {
            chapterPos.add(new int[] {cm.start(), cm.end()});
        }

        List<Chunk> result = new ArrayList<>();

        // 首条之前的前言（目录/总说明），足够长才独立成块
        int firstStart = articleStarts.get(0)[0];
        if (firstStart > 0) {
            String preamble = normalized.substring(0, firstStart).trim();
            if (preamble.length() >= minChunkTokens()) {
                result.add(new Chunk(preamble, null, currentChapterFor(chapterPos, normalized, 0)));
            }
        }

        for (int i = 0; i < articleStarts.size(); i++) {
            int start = articleStarts.get(i)[0];
            int end = i + 1 < articleStarts.size() ? articleStarts.get(i + 1)[0] : normalized.length();
            String content = normalized.substring(start, end).trim();
            if (content.isEmpty()) {
                continue;
            }
            String articleNo = articleNos.get(i);
            String chapterName = currentChapterFor(chapterPos, normalized, start);
            if (content.length() > articleMaxTokens()) {
                // 超长条款（附则/罚则长段）二次切分，保留条款号归属
                for (Document sub : splitLong(content)) {
                    result.add(new Chunk(sub.getText(), articleNo, chapterName));
                }
            } else {
                result.add(new Chunk(content, articleNo, chapterName));
            }
        }

        mergeSmallChunks(result);
        log.info("法规条款结构分块完成：条款数={}，分块数={}", articleStarts.size(), result.size());
        return result;
    }

    /** 非法规结构文本兜底：TokenTextSplitter 常规分块 */
    private List<Chunk> fallbackChunk(String text) {
        TokenTextSplitter splitter = new TokenTextSplitter(fallbackChunkSize(), 200, 5, 10000, true);
        List<Chunk> result = new ArrayList<>();
        for (Document d : splitter.apply(List.of(new Document(text)))) {
            result.add(new Chunk(d.getText(), null, null));
        }
        mergeSmallChunks(result);
        return result;
    }

    private List<Document> splitLong(String content) {
        TokenTextSplitter splitter = new TokenTextSplitter(articleMaxTokens(), 200, 5, 10000, true);
        return splitter.apply(List.of(new Document(content)));
    }

    /** 小于 minChunkTokens 的碎块并入前一块 */
    private void mergeSmallChunks(List<Chunk> chunks) {
        if (chunks.size() < 2) {
            return;
        }
        List<Chunk> merged = new ArrayList<>();
        for (Chunk c : chunks) {
            if (!merged.isEmpty() && c.estimateTokens() < minChunkTokens()) {
                Chunk prev = merged.get(merged.size() - 1);
                merged.set(merged.size() - 1,
                        new Chunk(prev.getContent() + "\n" + c.getContent(), prev.getArticleNo(), prev.getChapterName()));
            } else {
                merged.add(c);
            }
        }
        chunks.clear();
        chunks.addAll(merged);
    }

    /** 找到位置 pos 之前最近的章标题 */
    private String currentChapterFor(List<int[]> chapterPos, String text, int pos) {
        String latest = null;
        for (int[] cp : chapterPos) {
            if (cp[0] < pos) {
                latest = text.substring(cp[0], cp[1]).trim();
            } else {
                break;
            }
        }
        return latest;
    }

    private int minChunkTokens() {
        return props.getIngest().getMinChunkTokens() != null ? props.getIngest().getMinChunkTokens() : 50;
    }

    private int articleMaxTokens() {
        return props.getIngest().getArticleMaxTokens() != null ? props.getIngest().getArticleMaxTokens() : 800;
    }

    private int fallbackChunkSize() {
        return props.getIngest().getFallbackChunkSize() != null ? props.getIngest().getFallbackChunkSize() : 1000;
    }
}
