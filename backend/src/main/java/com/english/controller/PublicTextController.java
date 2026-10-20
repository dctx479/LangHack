package com.english.controller;

import com.english.dto.Result;
import com.english.dto.TextVersionsDTO;
import com.english.dto.AuthorDTO;
import com.english.entity.Text;
import com.english.entity.UserTextRead;
import com.english.entity.User;
import com.english.mapper.UserTextReadMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.entity.TextLearningRecord;
import com.english.service.ArticleVersionService;
import com.english.service.TextService;
import com.english.service.UserService;
import com.english.service.ContentTagService;
import com.english.entity.ContentTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import com.english.util.TextEncodingRepair;

/**
 * 用户阅读统计DTO
 */
class UserReadingStats {
    private Integer readArticlesCount;  // 阅读篇数
    private Integer totalReadCount;      // 阅读次数
    
    public UserReadingStats(Integer readArticlesCount, Integer totalReadCount) {
        this.readArticlesCount = readArticlesCount;
        this.totalReadCount = totalReadCount;
    }
    
    public Integer getReadArticlesCount() {
        return readArticlesCount;
    }
    
    public void setReadArticlesCount(Integer readArticlesCount) {
        this.readArticlesCount = readArticlesCount;
    }
    
    public Integer getTotalReadCount() {
        return totalReadCount;
    }
    
    public void setTotalReadCount(Integer totalReadCount) {
        this.totalReadCount = totalReadCount;
    }
}

/**
 * 公开课文控制器
 * 为普通用户提供随机课文访问
 */
@RestController
@RequestMapping("/api/public/texts")
public class PublicTextController {

    private static final Logger log = LoggerFactory.getLogger(PublicTextController.class);

    @Autowired
    private TextService textService;

    @Autowired
    private ArticleVersionService articleVersionService;

    @Autowired
    private UserTextReadMapper userTextReadMapper;

    @Autowired
    private TextLearningRecordMapper textLearningRecordMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ContentTagService contentTagService;

    @Autowired
    private com.english.service.RecommendationService recommendationService;

    @Autowired
    private com.english.service.AuthorEnricher authorEnricher;

    /**
     * 获取所有内容标签（公开接口，学生上传时使用）
     */
    @GetMapping("/content-tags")
    public Result<List<ContentTag>> getPublicContentTags() {
        try {
            List<ContentTag> tags = contentTagService.lambdaQuery()
                    .eq(ContentTag::getDeleted, 0)
                    // 内部标签（学院-专业）不参与学生端选标签
                    .and(w -> w.eq(ContentTag::getIsInternal, 0).or().isNull(ContentTag::getIsInternal))
                    .orderByAsc(ContentTag::getSortOrder)
                    .orderByAsc(ContentTag::getId)
                    .list();
            return Result.success(tags);
        } catch (Exception e) {
            log.error("获取内容标签列表失败", e);
            return Result.error("获取标签列表失败");
        }
    }

    /**
     * 学生端脱敏：内部标签（如「学院-专业」）只用于内容追溯，不展示给学生。
     * 直接改 tags 字段，前端拿到的仍是逗号串，无需任何改动。
     */
    private void hideInternalTags(List<Text> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        Set<String> internal = contentTagService.getInternalTagNames();
        if (internal.isEmpty()) {
            return;
        }
        for (Text text : texts) {
            if (text != null) {
                text.setTags(contentTagService.stripInternalTags(text.getTags(), internal));
            }
        }
    }

    /**
     * 获取首页推荐文章
     * 已登录用户：排除已读文章，未读不足6篇时重置已读记录
     * 未登录用户：随机展示所有推荐文章
     */
    @GetMapping("/featured")
    public Result<List<Text>> getFeaturedTexts(
            @RequestParam(required = false) Integer count,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(required = false) String exclude) {
        try {

            List<Text> featuredTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getIsFeatured, 1)
                    .and(w -> w
                        .isNull(Text::getUploadedBy)
                        .or(sub -> sub
                            .isNotNull(Text::getUploadedBy)
                            .eq(Text::getIsPublic, 1)
                            .eq(Text::getStatus, "APPROVED")
                        )
                    )
                    .orderByDesc(Text::getFeaturedPriority)
                    .orderByDesc(Text::getCreatedAt)
                    .list();

            if (featuredTexts.isEmpty()) {
                featuredTexts = textService.lambdaQuery()
                        .eq(Text::getDeleted, 0)
                        .and(w -> w
                            .isNull(Text::getUploadedBy)
                            .or(sub -> sub
                                .isNotNull(Text::getUploadedBy)
                                .eq(Text::getIsPublic, 1)
                                .eq(Text::getStatus, "APPROVED")
                            )
                        )
                        .orderByDesc(Text::getCreatedAt)
                        .list();
            }

            if (difficulty != null && !difficulty.trim().isEmpty()) {
                featuredTexts = featuredTexts.stream()
                        .filter(text -> difficulty.equals(text.getDifficulty()))
                        .collect(Collectors.toList());
            }

            Set<Long> excludedIds = parseTextIds(exclude);

            // No longer filter out read articles – keep all featured texts visible
            // so that returning from an article doesn't remove it from the list.

            // 手动刷新需要真正更换首屏文章，不能只打乱无优先级文章。
            // 普通请求继续保持运营配置的优先级顺序；refresh=true 时对整个候选池洗牌。
            if (refresh) {
                List<Text> refreshed = featuredTexts.stream()
                        .filter(text -> text.getId() == null || !excludedIds.contains(text.getId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                // 精选候选池耗尽时扩展到全部公开文章，避免刷新永远停留在少数精选文章。
                if (refreshed.isEmpty()) {
                    refreshed = textService.lambdaQuery()
                            .eq(Text::getDeleted, 0)
                            .and(w -> w
                                .isNull(Text::getUploadedBy)
                                .or(sub -> sub
                                    .isNotNull(Text::getUploadedBy)
                                    .eq(Text::getIsPublic, 1)
                                    .eq(Text::getStatus, "APPROVED")
                                )
                            )
                            .list()
                            .stream()
                            .filter(text -> (difficulty == null || difficulty.trim().isEmpty()
                                    || difficulty.equals(text.getDifficulty()))
                                    && (text.getId() == null || !excludedIds.contains(text.getId())))
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                // 全部候选都已展示时允许重新展示，避免返回空列表。
                if (refreshed.isEmpty()) {
                    refreshed = new ArrayList<>(featuredTexts);
                }
                Collections.shuffle(refreshed, new java.util.Random(System.nanoTime()));
                if (count != null && count > 0 && refreshed.size() > count) {
                    refreshed = new ArrayList<>(refreshed.subList(0, count));
                }
                TextEncodingRepair.repairAll(refreshed);
                authorEnricher.enrichTexts(refreshed);
                hideInternalTags(refreshed);
                return Result.success(refreshed);
            }

            // 有优先级(>0)的文章按优先级降序排在前面，priority=0 的文章随机填充剩余位置
            List<Text> prioritized = featuredTexts.stream()
                    .filter(t -> t.getFeaturedPriority() != null && t.getFeaturedPriority() > 0)
                    .sorted((a, b) -> Integer.compare(
                            b.getFeaturedPriority() != null ? b.getFeaturedPriority() : 0,
                            a.getFeaturedPriority() != null ? a.getFeaturedPriority() : 0))
                    .collect(Collectors.toList());
            List<Text> randomPool = new ArrayList<>(featuredTexts.stream()
                    .filter(t -> t.getFeaturedPriority() == null || t.getFeaturedPriority() == 0)
                    .collect(Collectors.toList()));
            // 按 id 先稳定排序，再用 3 小时时间窗口的种子洗牌：
            // 同一 3 小时窗口内顺序固定，跨窗口自动变化一次。
            randomPool.sort(Comparator.comparing(Text::getId));
            long shuffleSeed = System.currentTimeMillis() / (3L * 60 * 60 * 1000);
            Collections.shuffle(randomPool, new java.util.Random(shuffleSeed));

            List<Text> result = new ArrayList<>();
            if (count == null || count <= 0) {
                // 不限制数量，返回所有推荐文章
                result.addAll(prioritized);
                result.addAll(randomPool);
            } else if (prioritized.size() >= count) {
                result = prioritized.subList(0, count);
            } else {
                result.addAll(prioritized);
                int remaining = count - prioritized.size();
                result.addAll(randomPool.subList(0, Math.min(remaining, randomPool.size())));
            }

            TextEncodingRepair.repairAll(result);
            authorEnricher.enrichTexts(result);
            hideInternalTags(result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取推荐文章失败", e);
            return Result.error("获取推荐文章失败，请稍后重试");
        }
    }

    private Set<Long> parseTextIds(String rawIds) {
        Set<Long> ids = new HashSet<>();
        if (rawIds == null || rawIds.trim().isEmpty()) return ids;
        for (String value : rawIds.split(",")) {
            try {
                long id = Long.parseLong(value.trim());
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // Ignore malformed IDs supplied by the client.
            }
        }
        return ids;
    }

    /**
     * 搜索所有已审核公开文章（不限精选状态）
     * 支持按标题、内容、标签关键词搜索，带分页
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> searchArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String difficulty) {
        try {
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100;

            List<Text> texts = textService.getPublicTexts(page, size, difficulty, keyword);
            TextEncodingRepair.repairAll(texts);
            authorEnricher.enrichTexts(texts);
            hideInternalTags(texts);

            // 获取总数（不分页）
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Text> countWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            countWrapper.eq("deleted", 0);
            countWrapper.eq("is_public", 1);
            countWrapper.eq("status", "APPROVED");
            if (keyword != null && !keyword.trim().isEmpty()) {
                countWrapper.and(w -> w
                    .like("title", keyword)
                    .or().like("content", keyword)
                    .or().like("tags", keyword)
                );
            }
            if (difficulty != null && !difficulty.trim().isEmpty()) {
                countWrapper.eq("difficulty", difficulty);
            }
            long total = textService.count(countWrapper);

            Map<String, Object> result = new HashMap<>();
            result.put("articles", texts);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("搜索文章失败", e);
            return Result.error("搜索失败，请稍后重试");
        }
    }

    /**
     * 个性化推荐文章
     */
    @GetMapping("/recommended")
    public Result<List<Text>> getRecommendedTexts(
            @RequestParam(defaultValue = "12") int count,
            @RequestParam(required = false) String exclude) {
        try {
            count = Math.min(Math.max(count, 1), 50);

            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.success(Collections.emptyList());
            }

            List<Long> excludeIds = new ArrayList<>();
            if (exclude != null && !exclude.trim().isEmpty()) {
                for (String s : exclude.split(",")) {
                    try {
                        excludeIds.add(Long.parseLong(s.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            List<Text> result = recommendationService.recommend(userId, count, excludeIds);
            if (result == null) {
                result = new ArrayList<>();
            }
            TextEncodingRepair.repairAll(result);
            authorEnricher.enrichTexts(result);
            hideInternalTags(result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取推荐文章失败", e);
            return Result.error("获取推荐文章失败，请稍后重试");
        }
    }

    /**
     * 记录用户阅读行为
     * 阅读次数：每次调用都+1
     * 阅读篇数：同一用户同一篇文章仅第一次+1
     */
    @PostMapping("/{textId}/read")
    public Result<Void> recordReading(@PathVariable Long textId) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.success(null);
            }

            UserTextRead existing = userTextReadMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserTextRead>()
                            .eq(UserTextRead::getUserId, userId)
                            .eq(UserTextRead::getTextId, textId));
                            
            if (existing == null) {
                // 首次阅读：创建新记录，阅读篇数+1，阅读次数=1
                UserTextRead record = new UserTextRead();
                record.setUserId(userId);
                record.setTextId(textId);
                record.setReadAt(LocalDateTime.now());
                record.setLastReadAt(LocalDateTime.now());
                record.setReadCount(1);
                userTextReadMapper.insert(record);
            } else {
                // 重复阅读：仅增加阅读次数，不增加阅读篇数
                userTextReadMapper.incrementReadCount(userId, textId);
            }

            return Result.success(null);
        } catch (Exception e) {
            log.error("记录阅读行为失败, textId={}", textId, e);
            return Result.error("记录阅读行为失败");
        }
    }

    @PostMapping("/{textId}/version-read")
    public Result<Void> recordVersionRead(@PathVariable Long textId,
                                          @RequestParam String version) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.success(null);
        }
        try {
            TextLearningRecord record = new TextLearningRecord();
            record.setStudentId(userId);
            record.setTextId(textId);
            record.setContentVersion(version);
            textLearningRecordMapper.insert(record);
            return Result.success(null);
        } catch (Exception e) {
            log.warn("记录版本阅读失败, textId={}, version={}", textId, version, e);
            return Result.success(null);
        }
    }

    /**
     * 获取用户阅读统计
     */
    @GetMapping("/reading-stats")
    public Result<UserReadingStats> getReadingStats() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.success(new UserReadingStats(0, 0));
            }

            int readArticlesCount = userTextReadMapper.countReadArticles(userId);
            int totalReadCount = userTextReadMapper.sumReadCounts(userId);
            
            return Result.success(new UserReadingStats(readArticlesCount, totalReadCount));
        } catch (Exception e) {
            log.error("获取阅读统计失败", e);
            return Result.error("获取阅读统计失败");
        }
    }

    /**
     * 尝试从 SecurityContext 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            User user = userService.getUserByUsername(auth.getName());
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取随机课文列表
     */
    @GetMapping("/random")
    public Result<List<Text>> getRandomTexts(
            @RequestParam(defaultValue = "8") int count,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String includeUserContent) {
        try {
            // 限制最大获取数量
            count = Math.min(Math.max(count, 1), 50);

            // 先统计符合条件的总数，再随机选取，避免加载全部数据到内存
            // 构建查询条件
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Text> countWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            countWrapper.eq("deleted", 0);

            if (difficulty != null && !difficulty.trim().isEmpty()) {
                countWrapper.eq("difficulty", difficulty);
            }

            if ("false".equals(includeUserContent)) {
                countWrapper.isNull("uploaded_by");
            } else {
                // 系统内容 OR (用户内容 + 公开 + 审核通过)
                countWrapper.and(w -> w
                    .isNull("uploaded_by")
                    .or(sub -> sub
                        .isNotNull("uploaded_by")
                        .eq("is_public", 1)
                        .eq("status", "APPROVED")
                    )
                );
            }

            long total = textService.count(countWrapper);

            if (total == 0) {
                return Result.success(new ArrayList<>());
            }

            // 使用分页 + 随机偏移代替加载全部数据
            List<Text> randomTexts;
            if (total <= count) {
                // 总数不超过请求数量，直接返回全部
                randomTexts = textService.list(countWrapper);
                if (randomTexts == null) {
                    randomTexts = new ArrayList<>();
                }
            } else {
                // 随机选取：计算随机页码，避免分页边界问题
                // 使用随机偏移计算页码，而非直接计算页码，防止边界溢出
                long maxOffset = total - count;
                long randomOffset = (long) (Math.random() * (maxOffset + 1)); // +1 保证范围内
                int randomPage = (int) (randomOffset / count) + 1;

                com.baomidou.mybatisplus.extension.plugins.pagination.Page<Text> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(randomPage, count);
                List<Text> pageResult = textService.page(page, countWrapper).getRecords();
                randomTexts = new ArrayList<>(pageResult != null ? pageResult : new ArrayList<>());
                // 打乱顺序增加随机性
                Collections.shuffle(randomTexts);
            }

            TextEncodingRepair.repairAll(randomTexts);
            authorEnricher.enrichTexts(randomTexts);
            hideInternalTags(randomTexts);
            return Result.success(randomTexts);
        } catch (Exception e) {
            log.error("获取随机课文失败", e);
            return Result.error("获取随机课文失败，请稍后重试");
        }
    }

    /**
     * 获取课文统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getTextStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 统计系统课文数量
            long systemTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .isNull(Text::getUploadedBy)
                    .count();

            // 统计用户上传课文数量
            long userTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .isNotNull(Text::getUploadedBy)
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            // 统计总公开内容数量
            long totalPublicTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            // 按难度统计
            long easyTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "EASY")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            long mediumTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "MEDIUM")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            long hardTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "HARD")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            stats.put("totalSystemTexts", systemTexts);
            stats.put("totalUserTexts", userTexts);
            stats.put("totalPublicTexts", totalPublicTexts);
            stats.put("totalEasyTexts", easyTexts);
            stats.put("totalMediumTexts", mediumTexts);
            stats.put("totalHardTexts", hardTexts);
            stats.put("totalTexts", totalPublicTexts);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return Result.error("获取统计数据失败，请稍后重试");
        }
    }

    /**
     * 获取课文详情（包含所有版本）
     */
    @GetMapping("/{textId}")
    public Result<TextVersionsDTO> getTextDetail(@PathVariable Long textId) {
        try {
            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 验证课文是否可访问：系统内容 或 公开+已审核的用户内容
            boolean isAccessible = false;
            if (text.getUploadedBy() == null) {
                // 系统内容，总是可访问
                isAccessible = true;
            } else {
                // 用户上传的内容，需要公开且审核通过
                if ((text.getIsPublic() != null && text.getIsPublic() == 1) && "APPROVED".equals(text.getStatus())) {
                    isAccessible = true;
                }
            }

            if (!isAccessible) {
                return Result.error("课文不存在或未公开");
            }

            // 记录用户阅读行为
            recordReading(textId);
            
            // 原子递增查看次数
            textService.incrementViewCount(textId);

            // 触发异步版本生成（如果缺失）
            articleVersionService.checkAndGenerateVersions(textId);

            // 刷新查看次数（原子操作已执行）
            text = textService.getTextById(textId);
            TextEncodingRepair.repair(text);

            // 构建版本DTO
            TextVersionsDTO dto = new TextVersionsDTO();
            dto.setId(text.getId());
            dto.setTitle(text.getTitle());
            dto.setDifficulty(text.getDifficulty());
            dto.setAudioUrl(text.getAudioUrl());
            dto.setCoverUrl(text.getCoverUrl());
            dto.setViewCount(text.getViewCount() != null ? text.getViewCount() : 0);
            dto.setLikeCount(text.getLikeCount());
            dto.setContentType(text.getContentType());
            dto.setMarkdownContent(text.getMarkdownContent());

            // 填充上传者（作者）信息：系统内容无上传者，标记为官方内容
            authorEnricher.enrichText(text);
            dto.setAuthor(text.getAuthor());

            // 根据内容类型返回相应版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // Markdown内容，返回Markdown版本
                if (text.getMarkdownContent() != null && !text.getMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("original", text.getMarkdownContent());
                }
                if (text.getCet4MarkdownContent() != null && !text.getCet4MarkdownContent().trim().isEmpty()) {
                    dto.addVersion("cet4", text.getCet4MarkdownContent());
                }
                if (text.getCet6MarkdownContent() != null && !text.getCet6MarkdownContent().trim().isEmpty()) {
                    dto.addVersion("cet6", text.getCet6MarkdownContent());
                }
                if (text.getAcademicMarkdownContent() != null && !text.getAcademicMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("academic", text.getAcademicMarkdownContent());
                }
                if (text.getChineseParallelMarkdownContent() != null && !text.getChineseParallelMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("chinese_parallel", text.getChineseParallelMarkdownContent());
                }
            } else {
                // 纯文本内容
                dto.addVersion("original", text.getContent());
                dto.addVersion("cet4", text.getCet4Content());
                dto.addVersion("cet6", text.getCet6Content());
                dto.addVersion("academic", text.getAcademicContent());
                dto.addVersion("chinese_parallel", text.getChineseParallelContent());
            }

            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取课文详情失败, textId={}", textId, e);
            return Result.error("获取课文详情失败，请稍后重试");
        }
    }
}
