package com.english.service;

import com.english.entity.ContentTag;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Set;

public interface ContentTagService extends IService<ContentTag> {

    /** 返回当前所有未删除标签的名称集合（case-insensitive 比较，存原始大小写）。 */
    Set<String> getAllTagNames();

    /**
     * 过滤标签字符串，只保留白名单内的标签。
     * @param rawTags 逗号分隔的标签串（可为 null / 空）
     * @return 合法标签逗号串；若全部非法则返回空字符串
     */
    String filterValidTags(String rawTags);

    /**
     * 内部标签名称集合（小写）。内部标签用于内容追溯，不在学生端展示。
     */
    Set<String> getInternalTagNames();

    /**
     * 从标签串中剔除内部标签，用于公开接口返回前脱敏。不传 internalLowerNames 时现查一次。
     * @param rawTags 逗号分隔的标签串（可为 null / 空）
     * @param internalLowerNames 内部标签小写名称集合；为 null 时内部现查
     * @return 去掉内部标签后的逗号串（可能为空字符串）
     */
    String stripInternalTags(String rawTags, Set<String> internalLowerNames);
}
