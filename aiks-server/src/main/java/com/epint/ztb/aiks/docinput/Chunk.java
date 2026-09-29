package com.epint.ztb.aiks.docinput;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 法规文本分块结果
 */
@Data
@AllArgsConstructor
public class Chunk {

    /** 分块文本 */
    private String content;
    /** 条款号（如"第二十二条"），非条款结构文本为 null */
    private String articleNo;
    /** 所属章名 */
    private String chapterName;

    /**
     * token 估算：中文按 1 字符 ≈ 1 token 粗略估算，仅用于分块决策与统计，
     * 不作为计费依据
     */
    public int estimateTokens() {
        return content == null ? 0 : content.length();
    }
}
