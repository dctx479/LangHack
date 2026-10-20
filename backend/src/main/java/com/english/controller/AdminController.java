package com.english.controller;

import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.Unit;
import com.english.entity.User;
import com.english.entity.Word;
import com.english.entity.Text;
import com.english.entity.ArticleType;
import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;
import com.english.entity.Video;
import com.english.entity.ContentTag;
import com.english.entity.ArticleType;
import com.english.enums.TaskType;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.CourseService;
import com.english.service.WordService;
import com.english.service.ClassService;
import com.english.service.UnitService;
import com.english.service.TextService;
import com.english.service.VideoService;
import com.english.service.UnitWordService;
import com.english.service.TextExerciseService;
import com.english.service.ArticleVersionService;
import com.english.service.CampaignStatsService;
import com.english.service.ContentTagService;
import com.english.service.SystemConfigService;
import com.english.util.SizeParseUtil;
import com.english.mapper.StudentExerciseAnswerMapper;
import com.english.mapper.TextExerciseMapper;
import com.english.mapper.TextMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private TextUnitMapper textUnitMapper;
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Value("${sso.enabled:false}")
    private boolean ssoEnabled;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private WordService wordService;

    @Autowired
    private ClassService classService;

    @Autowired
    private com.english.service.StudentClassService studentClassService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private TextService textService;

    @Autowired
    private UnitWordService unitWordService;

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private ArticleVersionService articleVersionService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private ContentTagService contentTagService;

    @Autowired
    private StudentExerciseAnswerMapper studentExerciseAnswerMapper;

    @Autowired
    private TextExerciseMapper textExerciseMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("role", role);
            params.put("keyword", keyword);

            Map<String, Object> result = userService.getUsersByPage(params);

            // records 已被 service 层 enrich 为 List<Map>（含学院/行政班），不再包含 password 字段
            // 此处不再遍历 setPassword，避免对 Map 强转 User 导致 ClassCastException
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            return Result.error("获取用户列表失败，请稍后重试");
        }
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<String> createUser(@RequestBody User user) {
        try {
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            if (user.getPassword() == null || user.getPassword().length() < 6) {
                return Result.error("密码长度不能少于 6 位");
            }
            if (user.getPassword().length() > 100) {
                return Result.error("密码长度不能超过 100 位");
            }
            if (userService.getUserByUsername(user.getUsername()) != null) {
                return Result.error("用户名已存在");
            }

            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("STUDENT");
            }

            userService.createUser(user);
            return Result.success("用户创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建用户失败", e);
            return Result.error("创建用户失败，请稍后重试");
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);

            // 如果有新密码，需要加密；否则不更新密码
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                throw new BusinessException("用户不存在");
            }

            // 如果没有提供新密码，置为null让Service层保留原密码
            // 不要把已编码的密码拷贝进来，否则Service层会二次编码导致用户无法登录
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(null);
            } else if (ssoEnabled) {
                return Result.error("已启用统一身份认证，默认禁用密码修改");
            } else if (user.getPassword().length() < 6) {
                return Result.error("密码长度不能少于 6 位");
            } else if (user.getPassword().length() > 100) {
                return Result.error("密码长度不能超过 100 位");
            }

            userService.updateUser(user);
            return Result.success("用户更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新用户失败", e);
            return Result.error("更新用户失败，请稍后重试");
        }
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            User targetUser = userService.getUserById(id);
            if (targetUser == null) {
                return Result.error("用户不存在");
            }
            if ("admin".equals(targetUser.getUsername())) {
                return Result.error("不能删除默认管理员账户");
            }

            userService.deleteUser(id);
            return Result.success("用户删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除用户失败 id={}", id, e);
            return Result.error("删除用户失败，请稍后重试");
        }
    }

    @GetMapping("/users/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<String>> getUserRoles() {
        List<String> roles = List.of("STUDENT", "TEACHER", "ADMIN", "USER");
        return Result.success(roles);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 获取用户统计
            Map<String, Object> userStats = userService.getUserStats();
            if (userStats != null) {
                stats.putAll(userStats);
            }

            // 获取课程统计
            Map<String, Object> courseStats = courseService.getCourseStats();
            if (courseStats != null) {
                stats.putAll(courseStats);
            }

            // 获取单词统计
            Map<String, Object> wordStats = wordService.getWordStats();
            if (wordStats != null) {
                stats.putAll(wordStats);
            }

            // 获取班级统计
            Map<String, Object> classStats = classService.getClassStats();
            if (classStats != null) {
                stats.putAll(classStats);
            }

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            return Result.error("获取统计信息失败，请稍后重试");
        }
    }

    // ========== 数据清理功能已迁移至 OpsController (/api/ops/cleanup-data, /api/ops/data-stats) ==========

    // ========== 课程管理相关 ==========

    /**
     * 获取课程列表（管理员专用）
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100)); // 防止超大分页查询
            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (keyword != null) params.put("keyword", keyword);

            Map<String, Object> result = courseService.getCoursesByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取课程列表失败", e);

            return Result.error("获取课程列表失败，请稍后重试");
        }
    }

    // ========== 单元管理相关 ==========

    /**
     * 获取单元列表
     */
    @GetMapping("/units")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getUnits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (courseId != null) {
                params.put("courseId", courseId);
            }

            Map<String, Object> result = unitService.getUnitsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取单元列表失败", e);

            return Result.error("获取单元列表失败，请稍后重试");
        }
    }

    /**
     * 根据课程ID获取单元列表
     */
    @GetMapping("/courses/{courseId}/units")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<Unit>> getUnitsByCourseId(@PathVariable Long courseId) {
        try {
            List<Unit> units = unitService.getUnitsByCourseId(courseId);
            return Result.success(units);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课程单元失败", e);
            return Result.error("获取课程单元失败，请稍后重试");
        }
    }

    /**
     * 创建单元
     */
    @PostMapping("/units")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createUnit(@RequestBody Map<String, Object> unitData) {
        try {
            if (unitData.get("courseId") == null || unitData.get("name") == null) {
                return Result.error("courseId 和 name 为必填字段");
            }

            // 验证 courseId 存在性
            Object courseIdObj = unitData.get("courseId");
            if (courseIdObj == null) {
                return Result.error("courseId 为必填字段");
            }
            Long courseId;
            try {
                courseId = Long.valueOf(courseIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("courseId 必须为有效的数字");
            }
            com.english.entity.Course course = courseService.getCourseById(courseId);
            if (course == null || course.getDeleted() == 1) {
                throw new BusinessException("课程不存在或已被删除");
            }
            Unit unit = new Unit();
            unit.setCourseId(courseId);
            Object nameObj = unitData.get("name");
            if (nameObj == null) {
                return Result.error("name 为必填字段");
            }
            unit.setName(nameObj.toString());
            unit.setDescription(unitData.get("description") != null ? unitData.get("description").toString() : "");
            Object orderNumObj = unitData.get("orderNum");
            try {
                unit.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);
            } catch (NumberFormatException e) {
                logger.debug("orderNum 格式错误，使用默认值 0");
                unit.setOrderNum(0);
            }

            unitService.createUnit(unit);
            return Result.success("单元创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建单元失败", e);

            return Result.error("创建单元失败，请稍后重试");
        }
    }

    /**
     * 更新单元
     */
    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUnit(@PathVariable Long id, @RequestBody Map<String, Object> unitData) {
        try {
            if (unitData.get("courseId") == null || unitData.get("name") == null) {
                return Result.error("courseId 和 name 为必填字段");
            }
            Object courseIdObj = unitData.get("courseId");
            if (courseIdObj == null) {
                return Result.error("courseId 为必填字段");
            }
            Long courseId;
            try {
                courseId = Long.valueOf(courseIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("courseId 必须为有效的数字");
            }
            com.english.entity.Course courseCheckUpdate = courseService.getCourseById(courseId);
            if (courseCheckUpdate == null || courseCheckUpdate.getDeleted() == 1) {
                throw new BusinessException("课程不存在或已被删除");
            }
            Unit unit = new Unit();
            unit.setId(id);
            unit.setCourseId(courseId);
            Object nameObj = unitData.get("name");
            if (nameObj == null) {
                return Result.error("name 为必填字段");
            }
            unit.setName(nameObj.toString());
            unit.setDescription(unitData.get("description") != null ? unitData.get("description").toString() : "");
            Object orderNumObj = unitData.get("orderNum");
            try {
                unit.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);
            } catch (NumberFormatException e) {
                logger.debug("orderNum 格式错误，使用默认值 0");
                unit.setOrderNum(0);
            }

            unitService.updateUnit(unit);
            return Result.success("单元更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新单元失败", e);

            return Result.error("更新单元失败，请稍后重试");
        }
    }

    /**
     * 删除单元
     */
    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteUnit(@PathVariable Long id) {
        try {
            unitService.deleteUnit(id);
            return Result.success("单元删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除单元失败", e);

            return Result.error("删除单元失败，请稍后重试");
        }
    }

    // ========== 单词管理相关 ==========

    /**
     * 获取单词列表
     */
    @GetMapping("/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (keyword != null) params.put("keyword", keyword);
            if (difficulty != null) params.put("difficulty", difficulty);

            Map<String, Object> result = wordService.getWordsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取单词列表失败", e);

            return Result.error("获取单词列表失败，请稍后重试");
        }
    }

    /**
     * 创建单词
     */
    @PostMapping("/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createWord(@RequestBody Map<String, Object> wordData) {
        try {
            if (wordData.get("word") == null || wordData.get("definition") == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            // 创建Word对象并保存
            Word word = new Word();
            Object wordObj = wordData.get("word");
            Object defObj = wordData.get("definition");
            if (wordObj == null || defObj == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            word.setWord(wordObj.toString());
            word.setPronunciation(wordData.get("pronunciation") != null ? wordData.get("pronunciation").toString() : "");
            word.setDefinition(defObj.toString());
            word.setExampleSentence(wordData.get("exampleSentence") != null ? wordData.get("exampleSentence").toString() : "");
            word.setPartOfSpeech(wordData.get("partOfSpeech") != null ? wordData.get("partOfSpeech").toString() : "noun");
            word.setDifficulty(wordData.get("difficulty") != null ? wordData.get("difficulty").toString() : "EASY");
            word.setAudioUrl(wordData.get("audioUrl") != null ? wordData.get("audioUrl").toString() : "");

            boolean success = wordService.createWord(word);
            if (!success) {
                return Result.error("单词创建失败");
            }
            return Result.success("单词创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建单词失败", e);

            return Result.error("创建单词失败，请稍后重试");
        }
    }

    /**
     * 更新单词
     */
    @PutMapping("/words/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateWord(@PathVariable Long id, @RequestBody Map<String, Object> wordData) {
        try {
            if (wordData.get("word") == null || wordData.get("definition") == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            // 检查单词是否存在
            Word existingWord = wordService.getWordById(id);
            if (existingWord == null) {
                return Result.error("单词不存在");
            }

            // 更新单词信息
            Word word = new Word();
            word.setId(id);
            Object wordObj = wordData.get("word");
            Object defObj = wordData.get("definition");
            if (wordObj == null || defObj == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            word.setWord(wordObj.toString());
            word.setPronunciation(wordData.get("pronunciation") != null ? wordData.get("pronunciation").toString() : "");
            word.setDefinition(defObj.toString());
            word.setExampleSentence(wordData.get("exampleSentence") != null ? wordData.get("exampleSentence").toString() : "");
            word.setPartOfSpeech(wordData.get("partOfSpeech") != null ? wordData.get("partOfSpeech").toString() : "noun");
            word.setDifficulty(wordData.get("difficulty") != null ? wordData.get("difficulty").toString() : "EASY");
            word.setAudioUrl(wordData.get("audioUrl") != null ? wordData.get("audioUrl").toString() : "");

            wordService.updateWord(word);
            return Result.success("单词更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新单词失败", e);

            return Result.error("更新单词失败，请稍后重试");
        }
    }

    /**
     * 删除单词
     */
    @DeleteMapping("/words/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteWord(@PathVariable Long id) {
        try {
            // 检查单词是否存在
            Word existingWord = wordService.getWordById(id);
            if (existingWord == null) {
                return Result.error("单词不存在");
            }

            wordService.deleteWord(id);
            return Result.success("单词删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除单词失败", e);
            return Result.error("删除单词失败，请稍后重试");
        }
    }

    /**
     * 将单词添加到单元
     */
    @PostMapping("/units/{unitId}/words/{wordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> addWordToUnit(@PathVariable Long unitId, @PathVariable Long wordId) {
        try {
            // 检查是否已存在
            if (unitWordService.exists(unitId, wordId)) {
                return Result.error("该单词已在单元中");
            }

            unitWordService.addToUnit(unitId, wordId);
            return Result.success("单词添加到单元成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加单词到单元失败", e);
            return Result.error("添加单词到单元失败，请稍后重试");
        }
    }

    /**
     * 从单元移除单词
     */
    @DeleteMapping("/units/{unitId}/words/{wordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> removeWordFromUnit(@PathVariable Long unitId, @PathVariable Long wordId) {
        try {
            unitWordService.removeFromUnit(unitId, wordId);
            return Result.success("单词从单元移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除单词失败", e);
            return Result.error("移除单词失败，请稍后重试");
        }
    }

    // ========== 课文管理相关 ==========

    /**
     * 获取课文统计数据
     */
    @GetMapping("/texts/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getTextStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            long totalTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0).eq(Text::getStatus, "APPROVED").count();
            long totalEasy = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0).eq(Text::getStatus, "APPROVED").eq(Text::getDifficulty, "EASY").count();
            long totalMedium = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0).eq(Text::getStatus, "APPROVED").eq(Text::getDifficulty, "MEDIUM").count();
            long totalHard = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0).eq(Text::getStatus, "APPROVED").eq(Text::getDifficulty, "HARD").count();
            long totalFeatured = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0).eq(Text::getStatus, "APPROVED").eq(Text::getIsFeatured, 1).count();

            stats.put("totalTexts", totalTexts);
            stats.put("totalEasy", totalEasy);
            stats.put("totalMedium", totalMedium);
            stats.put("totalHard", totalHard);
            stats.put("totalFeatured", totalFeatured);

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取课文统计失败", e);
            return Result.error("获取统计数据失败");
        }
    }

    /**
     * 获取课文列表
     */
    @GetMapping("/texts")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getTexts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer featured,
            @RequestParam(required = false) String articleType,
            @RequestParam(required = false) String bookVolume,
            @RequestParam(required = false) Integer unitNum) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (unitId != null) params.put("unitId", unitId);
            if (keyword != null) params.put("keyword", keyword);
            if (difficulty != null) params.put("difficulty", difficulty);
            if (tag != null) params.put("tag", tag);
            if (featured != null) params.put("featured", featured);
            if (articleType != null) params.put("articleType", articleType);
            if (bookVolume != null) params.put("bookVolume", bookVolume);
            if (unitNum != null) params.put("unitNum", unitNum);

            Map<String, Object> result = textService.getTextsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取课文列表失败", e);

            return Result.error("获取课文列表失败，请稍后重试");
        }
    }

    
    /**
     * 创建课文
     */
    @PostMapping("/texts")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> createText(@RequestBody Map<String, Object> textData) {
        try {
            // 获取当前用户ID
            Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return Result.error("用户未认证");
            }
            String username = authentication.getName();
            com.english.entity.User creator = userService.getUserByUsername(username);
            if (creator == null) {
                return Result.error("用户不存在");
            }
            Long createdBy = creator.getId();
            Text text = new Text();
            Object titleObj = textData.get("title");
            if (titleObj == null) {
                return Result.error("title 为必填字段");
            }
            text.setTitle(titleObj.toString());
            // 从前端获取unitId
            if (textData.get("unitId") != null) {
                try {
                    text.setUnitId(Long.valueOf(textData.get("unitId").toString()));
                } catch (NumberFormatException e) {
                    return Result.error("unitId 必须为有效的数字");
                }
            }
            text.setContent(textData.get("content") != null ? textData.get("content").toString() : "");
            text.setMarkdownContent(textData.get("markdownContent") != null ? textData.get("markdownContent").toString() : "");
            text.setContentType(textData.get("contentType") != null ? textData.get("contentType").toString() : "TEXT");
            text.setDifficulty(textData.get("difficulty") != null ? textData.get("difficulty").toString() : "EASY");
            text.setAudioUrl(textData.get("audioUrl") != null ? textData.get("audioUrl").toString() : "");
            text.setCoverUrl(textData.get("coverUrl") != null ? textData.get("coverUrl").toString() : "");
            text.setTags(textData.get("tags") != null ? textData.get("tags").toString() : "");
            text.setCategory(textData.get("category") != null ? textData.get("category").toString() : null);
            Result<String> classificationError = applyArticleClassification(text, textData);
            if (classificationError != null) {
                return Result.error(classificationError.getMessage());
            }
            Object isPublicObj = textData.get("isPublic");
            try {
                text.setIsPublic(isPublicObj != null ? Integer.parseInt(isPublicObj.toString()) : 1);
            } catch (NumberFormatException e) {
                logger.debug("isPublic 格式错误，使用默认值 1");
                text.setIsPublic(1);
            }

            // 课内/课外分类字段
            String articleTypeStr = textData.get("articleType") != null ? textData.get("articleType").toString() : "OUTER";
            try {
                text.setArticleType(ArticleType.valueOf(articleTypeStr));
            } catch (IllegalArgumentException e) {
                logger.debug("articleType 格式错误，使用默认值 OUTER");
                text.setArticleType(ArticleType.OUTER);
            }
            text.setBookVolume(textData.get("bookVolume") != null ? textData.get("bookVolume").toString() : null);
            Object unitNumObj = textData.get("unitNum");
            if (unitNumObj != null) {
                try {
                    text.setUnitNum(Integer.parseInt(unitNumObj.toString()));
                } catch (NumberFormatException e) {
                    logger.debug("unitNum 格式错误，使用默认值 null");
                }
            }

            // 先校验习题数据（在持久化课文之前），避免课文已入库但习题校验失败
            List<com.english.entity.TextExercise> preValidatedExercises = new ArrayList<>();
            List<List<com.english.entity.ExerciseOption>> preValidatedOptions = new ArrayList<>();
            boolean hasExercises = false;

            if (textData.containsKey("exercises")) {
                Object exercisesPreObj = textData.get("exercises");
                if (!(exercisesPreObj instanceof List)) {
                    return Result.error("exercises 字段必须是数组类型");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> exercisesPreData = (List<Map<String, Object>>) exercisesPreObj;

                if (!exercisesPreData.isEmpty()) {
                    hasExercises = true;
                    for (Map<String, Object> exerciseData : exercisesPreData) {
                        com.english.entity.TextExercise exercise = new com.english.entity.TextExercise();
                        Object questionObj = exerciseData.get("question");
                        Object exerciseTypeObj = exerciseData.get("exerciseType");
                        Object orderNumObj = exerciseData.get("orderNum");
                        if (questionObj == null || exerciseTypeObj == null || orderNumObj == null) {
                            continue;
                        }
                        exercise.setQuestion(questionObj.toString());
                        exercise.setExerciseType(exerciseTypeObj.toString());
                        try {
                            exercise.setOrderNum(Integer.parseInt(orderNumObj.toString()));
                        } catch (NumberFormatException e) {
                            logger.debug("习题 orderNum 格式错误，使用默认值 0");
                            exercise.setOrderNum(0);
                        }
                        exercise.setCreatedBy(createdBy);
                        preValidatedExercises.add(exercise);

                        Object optionsObj = exerciseData.get("options");
                        List<Map<String, Object>> optionsData = (optionsObj instanceof List) ? (List<Map<String, Object>>) optionsObj : null;
                        List<com.english.entity.ExerciseOption> options = new ArrayList<>();
                        if (optionsData != null && !optionsData.isEmpty()) {
                            for (Map<String, Object> optionData : optionsData) {
                                com.english.entity.ExerciseOption option = new com.english.entity.ExerciseOption();
                                Object optionContentObj = optionData.get("optionContent");
                                Object optionKeyObj = optionData.get("optionKey");
                                if (optionContentObj == null || optionKeyObj == null) {
                                    continue;
                                }
                                option.setOptionContent(optionContentObj.toString());
                                option.setOptionKey(optionKeyObj.toString());
                                Object isCorrectObj = optionData.get("isCorrect");
                                int isCorrectVal = 0;
                                if (isCorrectObj != null) {
                                    String isCorrectStr = isCorrectObj.toString().trim().toLowerCase();
                                    isCorrectVal = ("1".equals(isCorrectStr) || "true".equals(isCorrectStr)) ? 1 : 0;
                                }
                                option.setIsCorrect(isCorrectVal);
                                Object optionOrderObj = optionData.get("orderNum");
                                try {
                                    option.setOrderNum(optionOrderObj != null ? Integer.parseInt(optionOrderObj.toString()) : 0);
                                } catch (NumberFormatException e) {
                                    logger.debug("选项 orderNum 格式错误，使用默认值 0");
                                    option.setOrderNum(0);
                                }
                                options.add(option);
                            }
                        }
                        preValidatedOptions.add(options);
                    }
                }
            }

            // 所有校验通过后，创建课文
            text.setUploadedBy(createdBy);
            textService.createText(text);

            // 显式传入 unitIds 时，以多对多关系为准；空数组表示文章独立存在。
            Object unitIdsObj = textData.get("unitIds");
            if (unitIdsObj instanceof List) {
                textUnitMapper.deleteAllRelations(text.getId());
                Long firstUnitId = null;
                for (Object value : (List<?>) unitIdsObj) {
                    try {
                        Long targetUnitId = Long.valueOf(value.toString());
                        if (unitService.getUnitById(targetUnitId) != null) {
                            textUnitMapper.insertRelation(text.getId(), targetUnitId, text.getOrderNum() == null ? 0 : text.getOrderNum());
                            if (firstUnitId == null) firstUnitId = targetUnitId;
                        }
                    } catch (NumberFormatException ignored) {
                        // 忽略无效的可选单元 ID，主文章仍正常创建
                    }
                }
                textMapper.updateUnitReference(text.getId(), firstUnitId);
            }

            // 根据内容类型生成不同版本
            if ("MARKDOWN".equals(text.getContentType())) {
                articleVersionService.checkAndGenerateMarkdownVersions(text.getId());
            } else {
                articleVersionService.checkAndGenerateVersions(text.getId());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textId", text.getId());
            result.put("message", "课文创建成功");

            // 使用已预先校验的习题数据创建习题
            if (hasExercises && !preValidatedExercises.isEmpty()) {
                for (com.english.entity.TextExercise ex : preValidatedExercises) {
                    ex.setTextId(text.getId());
                }
                boolean exerciseSuccess = textExerciseService.batchCreateExercises(preValidatedExercises, preValidatedOptions);
                if (exerciseSuccess) {
                    result.put("exerciseCount", preValidatedExercises.size());
                    result.put("message", "课文和习题创建成功");
                } else {
                    result.put("message", "课文创建成功，但习题创建失败");
                }
            }

            return Result.success(result);
        } catch (BusinessException e) {
            logger.error("创建课文失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建课文失败", e);
            return Result.error("创建课文失败，请稍后重试");
        }
    }
    @PutMapping("/texts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @org.springframework.transaction.annotation.Transactional
    public Result<String> updateText(@PathVariable Long id, @RequestBody Map<String, Object> textData) {
        try {
            Text text = new Text();
            text.setId(id);
            if (textData.get("unitId") != null) {
                try {
                    text.setUnitId(Long.valueOf(textData.get("unitId").toString()));
                } catch (NumberFormatException e) {
                    return Result.error("unitId 必须为有效的数字");
                }
            }
            Object titleObj = textData.get("title");
            if (titleObj == null) {
                return Result.error("title 为必填字段");
            }
            text.setTitle(titleObj.toString());
            text.setContent(textData.get("content") != null ? textData.get("content").toString() : "");
            text.setMarkdownContent(textData.get("markdownContent") != null ? textData.get("markdownContent").toString() : "");
            text.setContentType(textData.get("contentType") != null ? textData.get("contentType").toString() : "TEXT");
            text.setDifficulty(textData.get("difficulty") != null ? textData.get("difficulty").toString() : "EASY");
            text.setAudioUrl(textData.get("audioUrl") != null ? textData.get("audioUrl").toString() : "");
            text.setCoverUrl(textData.get("coverUrl") != null ? textData.get("coverUrl").toString() : "");
            text.setTags(textData.get("tags") != null ? textData.get("tags").toString() : "");
            text.setCategory(textData.get("category") != null ? textData.get("category").toString() : null);
            Result<String> classificationError = applyArticleClassification(text, textData);
            if (classificationError != null) {
                return classificationError;
            }
            Object isPublicObj = textData.get("isPublic");
            try {
                text.setIsPublic(isPublicObj != null ? Integer.parseInt(isPublicObj.toString()) : 1);
            } catch (NumberFormatException e) {
                logger.debug("isPublic 格式错误，使用默认值 1");
                text.setIsPublic(1);
            }
            Object orderNumObj = textData.get("orderNum");
            try {
                text.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);
            } catch (NumberFormatException e) {
                logger.debug("orderNum 格式错误，使用默认值 0");
                text.setOrderNum(0);
            }

            // 课内/课外分类字段
            if (textData.get("articleType") != null) {
                try {
                    text.setArticleType(ArticleType.valueOf(textData.get("articleType").toString()));
                } catch (IllegalArgumentException e) {
                    logger.debug("articleType 格式错误，忽略");
                }
            }
            if (textData.get("bookVolume") != null) {
                text.setBookVolume(textData.get("bookVolume").toString());
            }
            Object unitNumObj = textData.get("unitNum");
            if (unitNumObj != null) {
                try {
                    text.setUnitNum(Integer.parseInt(unitNumObj.toString()));
                } catch (NumberFormatException e) {
                    logger.debug("unitNum 格式错误，忽略");
                }
            }

            textService.updateText(text);

            // unitIds 存在时替换全部关系；空数组会清空所有单元绑定。
            Object unitIdsObj = textData.get("unitIds");
            if (unitIdsObj instanceof List) {
                textUnitMapper.deleteAllRelations(id);
                Long firstUnitId = null;
                for (Object value : (List<?>) unitIdsObj) {
                    try {
                        Long targetUnitId = Long.valueOf(value.toString());
                        if (unitService.getUnitById(targetUnitId) != null) {
                            textUnitMapper.insertRelation(id, targetUnitId, text.getOrderNum() == null ? 0 : text.getOrderNum());
                            if (firstUnitId == null) firstUnitId = targetUnitId;
                        }
                    } catch (NumberFormatException ignored) {
                        // ignore invalid optional unit ids
                    }
                }
                textMapper.updateUnitReference(id, firstUnitId);
            }

            // 根据内容类型重新生成不同版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // 异步重新生成Markdown版本
                articleVersionService.checkAndGenerateMarkdownVersions(text.getId());
            } else {
                // 异步重新生成普通文本版本
                articleVersionService.checkAndGenerateVersions(text.getId());
            }

            return Result.success("课文更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新课文失败", e);

            return Result.error("更新课文失败，请稍后重试");
        }
    }

    private Result<String> applyArticleClassification(Text text, Map<String, Object> textData) {
        if (!textData.containsKey("articleType") || textData.get("articleType") == null) {
            return null;
        }
        String articleTypeValue = textData.get("articleType").toString().trim().toUpperCase();
        final ArticleType articleType;
        try {
            articleType = ArticleType.valueOf(articleTypeValue);
        } catch (IllegalArgumentException e) {
            return Result.error("articleType 必须为 INNER 或 OUTER");
        }

        text.setArticleType(articleType);
        if (articleType == ArticleType.OUTER) {
            text.setBookVolume(null);
            text.setContentSection(null);
            text.setUnitNum(null);
            return null;
        }

        String bookVolume = optionalString(textData.get("bookVolume"));
        String contentSection = optionalString(textData.get("contentSection"));
        if (bookVolume == null) {
            return Result.error("课内课文必须选择教材册号");
        }
        if (contentSection == null) {
            return Result.error("课内课文必须选择内容分类");
        }
        if (bookVolume != null && !Arrays.asList("综合教程1", "综合教程2", "综合教程3", "理解当代中国").contains(bookVolume)) {
            return Result.error("bookVolume 不是有效的教材");
        }
        if (contentSection != null) {
            List<String> allowedSections = "理解当代中国".equals(bookVolume)
                    ? Arrays.asList("Understanding China", "Exploring the world")
                    : Arrays.asList("iExplore1", "iExplore2");
            if (!allowedSections.contains(contentSection)) {
                return Result.error("contentSection 与所选教材不匹配");
            }
        }

        text.setBookVolume(bookVolume);
        text.setContentSection(contentSection);
        Object unitNumValue = textData.get("unitNum");
        if (unitNumValue != null && !unitNumValue.toString().trim().isEmpty()) {
            try {
                int unitNum = Integer.parseInt(unitNumValue.toString());
                if (unitNum < 1 || unitNum > 6) {
                    return Result.error("unitNum 必须在 1 到 6 之间");
                }
                text.setUnitNum(unitNum);
            } catch (NumberFormatException e) {
                return Result.error("unitNum 必须为有效的数字");
            }
        }
        return null;
    }

    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 重新生成课文改写版本（CET-4 / CET-6 / 学术 / 中英对照），异步执行。
     * version 取值：CET4、CET6、ACADEMIC、PARALLEL、ALL（缺省 ALL，大小写不敏感）。
     */
    @PostMapping("/texts/{id}/regenerate-versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> regenerateTextVersions(@PathVariable Long id,
                                                 @RequestParam(required = false, defaultValue = "ALL") String version) {
        String adminUser = "unknown";
        try {
            Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                adminUser = auth.getName();
            }
        } catch (Exception ignore) {
            // 安全上下文不可用时降级为 unknown，不影响主流程
        }

        logger.info("[改写重生成] 收到请求: 操作员={}, 课文ID={}, 版本参数={}", adminUser, id, version);

        try {
            Text text = textService.getTextById(id);
            if (text == null || (text.getDeleted() != null && text.getDeleted() == 1)) {
                logger.warn("[改写重生成] 课文不存在或已删除: 课文ID={}, 操作员={}", id, adminUser);
                return Result.error("课文不存在");
            }
            String normalized = version == null ? "ALL" : version.trim().toUpperCase();
            if (!java.util.Set.of("ALL", "CET4", "CET6", "ACADEMIC", "PARALLEL").contains(normalized)) {
                logger.warn("[改写重生成] 版本参数无效: 课文ID={}, 版本={}, 操作员={}", id, version, adminUser);
                return Result.error("不支持的版本参数：" + version);
            }
            logger.info("[改写重生成] 校验通过，准备提交异步任务: 课文ID={}, 标题=\"{}\", 内容类型={}, 版本={}, 操作员={}",
                    id, text.getTitle(), text.getContentType(), normalized, adminUser);
            articleVersionService.regenerateVersions(id, normalized);
            logger.info("[改写重生成] 任务已提交后台执行: 课文ID={}, 版本={}, 操作员={} —— 实际生成进度请关注 [版本重生成]/[版本生成] 日志",
                    id, normalized, adminUser);
            return Result.success("已提交重新生成任务，请稍后刷新查看");
        } catch (Exception e) {
            logger.error("[改写重生成] 提交失败: 课文ID={}, 版本={}, 操作员={}", id, version, adminUser, e);
            return Result.error("提交失败，请稍后重试");
        }
    }

    /**
     * 切换课文推荐状态
     */
    @PutMapping("/texts/{id}/featured")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> toggleFeatured(@PathVariable Long id,
                                         @RequestParam(required = false) Integer featured,
                                         @RequestParam(required = false, defaultValue = "false") Boolean forcePublic) {
        try {
            Text text = textService.getTextById(id);
            if (text == null || (text.getDeleted() != null && text.getDeleted() == 1)) {
                return Result.error("课文不存在");
            }
            int newValue;
            if (featured != null) {
                newValue = featured == 1 ? 1 : 0;
            } else {
                newValue = (text.getIsFeatured() != null && text.getIsFeatured() == 1) ? 0 : 1;
            }

            // 设为推荐时，确保文章已公开且已通过审核
            if (newValue == 1 && text.getUploadedBy() != null) {
                boolean isPublicAndApproved = (text.getIsPublic() != null && text.getIsPublic() == 1)
                        && "APPROVED".equals(text.getStatus());
                if (!isPublicAndApproved) {
                    // 默认所有上传都是公开的，直接设为公开并通过
                    text.setIsPublic(1);
                    text.setStatus("APPROVED");
                }
            }

            text.setIsFeatured(newValue);
            textService.updateText(text);
            return Result.success(newValue == 1 ? "已设为推荐" : "已取消推荐");
        } catch (Exception e) {
            logger.error("切换推荐状态失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 设置课文推荐优先级
     */
    @PutMapping("/texts/{id}/featured-priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> setFeaturedPriority(@PathVariable Long id,
                                              @RequestParam Integer priority) {
        try {
            Text text = textService.getTextById(id);
            if (text == null || (text.getDeleted() != null && text.getDeleted() == 1)) {
                return Result.error("课文不存在");
            }
            int safeValue = Math.max(0, Math.min(100, priority));
            text.setFeaturedPriority(safeValue);
            textService.updateText(text);
            return Result.success("优先级已更新为 " + safeValue);
        } catch (Exception e) {
            logger.error("设置推荐优先级失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 删除课文
     */
    @DeleteMapping("/texts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteText(@PathVariable Long id) {
        try {
            textService.deleteText(id);
            return Result.success("课文删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除课文失败", e);
            return Result.error("删除课文失败，请稍后重试");
        }
    }

    /**
     * 根据单元ID获取课文列表（带详细信息）
     */
    @GetMapping("/units/{unitId}/texts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> getTextsByUnitId(@PathVariable Long unitId) {
        try {
            logger.debug("获取单元课文列表, 单元ID: {}", unitId);

            List<Text> texts = textService.getTextsByUnit(unitId);
            logger.debug("找到课文数量: {}", texts.size());

            List<Map<String, Object>> result = new ArrayList<>();

            for (Text text : texts) {
                logger.debug("课文ID: {}, 标题: {}, unitId: {}", text.getId(), text.getTitle(), text.getUnitId());

                Map<String, Object> textMap = new HashMap<>();
                textMap.put("id", text.getId());
                textMap.put("unitId", text.getUnitId());
                textMap.put("unitIds", textUnitMapper.selectUnitIdsByTextId(text.getId()));
                textMap.put("title", text.getTitle());
                textMap.put("content", text.getContent());
                textMap.put("difficulty", text.getDifficulty());
                textMap.put("audioUrl", text.getAudioUrl());
                textMap.put("orderNum", text.getOrderNum());
                textMap.put("createdTime", text.getCreatedTime());
                result.add(textMap);
            }

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元课文失败", e);
            return Result.error("获取单元课文失败，请稍后重试");
        }
    }

    /**
     * 将课文添加到单元
     */
    @PostMapping("/units/{unitId}/texts/{textId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> addTextToUnit(@PathVariable Long unitId, @PathVariable Long textId) {
        try {
            // 检查课文是否存在
            Text existingText = textService.getTextById(textId);
            if (existingText == null) {
                return Result.error("课文不存在");
            }

            // 检查课文是否已属于某个单元（unit_id = 0 表示未分配）
            if (textUnitMapper.selectUnitIdsByTextId(textId).contains(unitId)) {
                return Result.error("该课文已属于其他单元，请先从原单元移除");
            }

            // 将课文分配给单元
            if (unitService.getUnitById(unitId) == null) {
                return Result.error("鍗曞厓涓嶅瓨鍦?");
            }
            textUnitMapper.insertRelation(textId, unitId, 0);
            if (existingText.getUnitId() == null) {
                existingText.setUnitId(unitId);
                textService.updateText(existingText);
            }

            return Result.success("课文添加到单元成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加课文到单元失败", e);
            return Result.error("添加课文到单元失败，请稍后重试");
        }
    }

    /**
     * 获取课文详情（包含习题）
     */
    @GetMapping("/texts/{textId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getTextDetailWithExercises(@PathVariable Long textId) {
        try {
            // 获取课文信息
            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", text.getId());
            result.put("unitId", text.getUnitId());
            result.put("unitIds", textUnitMapper.selectUnitIdsByTextId(textId));
            result.put("title", text.getTitle());
            result.put("content", text.getContent());
            result.put("difficulty", text.getDifficulty());
            result.put("audioUrl", text.getAudioUrl());
            result.put("orderNum", text.getOrderNum());
            result.put("createdTime", text.getCreatedTime());

            // 获取课文习题
            List<Map<String, Object>> exercises = textExerciseService.getExercisesWithOptions(textId);
            if (exercises == null) exercises = new ArrayList<>();
            result.put("exercises", exercises);
            result.put("exerciseCount", exercises.size());

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课文详情失败", e);
            return Result.error("获取课文详情失败，请稍后重试");
        }
    }

    /**
     * 更新课文的所有题目（批量替换）
     */
    @PutMapping("/texts/{textId}/exercises")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateTextExercises(@PathVariable Long textId, @RequestBody Map<String, Object> requestData) {
        try {
            // 验证课文存在
            Text text = textService.getTextById(textId);
            if (text == null || Integer.valueOf(1).equals(text.getDeleted())) {
                return Result.error("课文不存在或已被删除");
            }

            Object questionsObj = requestData.get("questions");
            if (!(questionsObj instanceof List)) {
                return Result.error("questions 字段必须是数组类型");
            }

            List<Map<String, Object>> questionsData = (List<Map<String, Object>>) questionsObj;
            if (questionsData.isEmpty()) {
                // 清空所有题目
                textExerciseService.deleteAllExercisesByTextId(textId);
                return Result.success("已清空所有题目");
            }

            // 删除旧题目
            textExerciseService.deleteAllExercisesByTextId(textId);

            // 创建新题目
            List<TextExercise> exercises = new ArrayList<>();
            List<List<ExerciseOption>> allOptions = new ArrayList<>();
            User currentUser = getCurrentUser();

            int orderNum = 1;
            for (Map<String, Object> qData : questionsData) {
                String question = (String) qData.get("question");
                if (question == null || question.trim().isEmpty()) {
                    return Result.error("题目内容不能为空");
                }

                TextExercise exercise = new TextExercise();
                exercise.setTextId(textId);
                exercise.setQuestion(question);
                exercise.setExerciseType("MULTIPLE_CHOICE");
                exercise.setQuestionType((String) qData.getOrDefault("questionType", "选择题"));
                exercise.setAnalysis((String) qData.get("analysis"));
                exercise.setOrderNum(orderNum++);
                exercise.setCreatedBy(currentUser.getId());

                exercises.add(exercise);

                // 处理选项
                List<ExerciseOption> options = new ArrayList<>();
                Object optionsObj = qData.get("options");
                if (optionsObj instanceof List) {
                    List<Map<String, Object>> optionsData = (List<Map<String, Object>>) optionsObj;
                    int optOrder = 1;
                    for (Map<String, Object> oData : optionsData) {
                        ExerciseOption option = new ExerciseOption();
                        option.setOptionKey((String) oData.get("optionKey"));
                        option.setOptionContent((String) oData.get("optionContent"));
                        Object isCorrectObj = oData.get("isCorrect");
                        option.setIsCorrect(isCorrectObj instanceof Number ? ((Number) isCorrectObj).intValue() : 0);
                        option.setOrderNum(optOrder++);
                        options.add(option);
                    }
                }
                allOptions.add(options);
            }

            boolean success = textExerciseService.batchCreateExercises(exercises, allOptions);
            if (success) {
                logger.info("更新课文题目成功: textId={}, 题目数={}", textId, exercises.size());
                return Result.success("题目保存成功");
            } else {
                return Result.error("题目保存失败");
            }
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新课文题目失败", e);
            return Result.error("保存失败，请稍后重试");
        }
    }

    /**
     * 从单元移除课文
     */
    @DeleteMapping("/units/{unitId}/texts/{textId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> removeTextFromUnit(@PathVariable Long unitId, @PathVariable Long textId) {
        try {
            logger.debug("开始移除课文, 单元ID: {}, 课文ID: {}", unitId, textId);

            // 检查课文是否存在
            Text existingText = textService.getTextById(textId);
            if (existingText == null) {
                return Result.error("课文不存在");
            }

            logger.debug("课文当前的unitId: {}, 标题: {}", existingText.getUnitId(), existingText.getTitle());

            // 检查课文是否属于当前单元（允许unitId为null的情况）
            if (!textUnitMapper.selectUnitIdsByTextId(textId).contains(unitId)
                    && existingText.getUnitId() != null && !unitId.equals(existingText.getUnitId())) {
                return Result.error("该课文不属于当前单元");
            }

            // 如果课文已经不属于任何单元，也算作成功
            if (existingText.getUnitId() == null && textUnitMapper.selectUnitIdsByTextId(textId).isEmpty()) {
                return Result.success("课文已经不属于任何单元");
            }

            // 设置unitId为null并更新
            textUnitMapper.deleteRelation(textId, unitId);
            List<Long> remainingUnitIds = textUnitMapper.selectUnitIdsByTextId(textId);
            textMapper.updateUnitReference(textId, remainingUnitIds.isEmpty() ? null : remainingUnitIds.get(0));

            // 验证更新是否成功
            Text updatedText = textService.getTextById(textId);
            if (updatedText == null) {
                return Result.error("课文不存在");
            }
            if (textUnitMapper.selectUnitIdsByTextId(textId).contains(unitId)) {
                logger.warn("更新后unitId仍不为null: {}", updatedText.getUnitId());
                return Result.error("移除失败，请重试");
            }

            return Result.success("课文从单元移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除课文失败", e);
            return Result.error("移除课文失败，请稍后重试");
        }
    }

    /**
     * 根据单元ID获取单词列表（带详细信息）
     */
    @GetMapping("/units/{unitId}/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> getWordsByUnitId(@PathVariable Long unitId) {
        try {
            List<Map<String, Object>> words = unitWordService.getWordsWithDetailsByUnitId(unitId);
            return Result.success(words);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元单词失败", e);
            return Result.error("获取单元单词失败，请稍后重试");
        }
    }

    /**
     * 批量导入用户
     * 通过上传Excel文件批量创建或更新用户
     * Excel格式: 序号 | 班级 | 学号 | 姓名 | ...
     */
    @PostMapping("/users/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        // 文件大小限制（动态读取 content_limits 配置，默认 10MB）
        String limitVal = systemConfigService.get("content_limits", "upload.content_size.excel_import", "10MB");
        long maxBytes = SizeParseUtil.parseSize(limitVal, 10 * 1024 * 1024L);
        if (file.getSize() > maxBytes) {
            return Result.error("文件大小超过限制（最大" + SizeParseUtil.formatSize(maxBytes) + "）");
        }

        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
            return Result.error("只支持.xls和.xlsx格式的Excel文件");
        }

        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        // 新增：班级相关统计
        int classCreatedCount = 0;      // 新创建的班级数
        int classJoinedCount = 0;       // 成功加入班级的用户数
        int classJoinFailedCount = 0;   // 加入班级失败的用户数
        List<String> createdClassNames = new ArrayList<>();  // 记录创建的班级名称

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = filename.endsWith(".xlsx")
                     ? new XSSFWorkbook(inputStream)
                     : new HSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 跳过前两行(标题行和表头行)
            int startRow = 2;

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                try {
                    // 读取数据: 序号(0) | 班级(1) | 学号(2) | 姓名(3)
                    Cell classCell = row.getCell(1);
                    Cell studentIdCell = row.getCell(2);
                    Cell nameCell = row.getCell(3);

                    // 检查必填字段
                    if (studentIdCell == null || nameCell == null) {
                        errors.add("第" + (i + 1) + "行: 学号或姓名为空");
                        failCount++;
                        continue;
                    }

                    String studentId = getCellValueAsString(studentIdCell);
                    String realName = getCellValueAsString(nameCell);
                    String className = classCell != null ? getCellValueAsString(classCell) : "";

                    // 验证数据
                    if (studentId.trim().isEmpty() || realName.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 学号或姓名为空");
                        failCount++;
                        continue;
                    }

                    // 检查用户是否已存在
                    User existingUser = userService.getUserByUsername(studentId);
                    User currentUser = null;  // 保存当前处理的用户对象

                    if (existingUser != null) {
                        // 更新已存在的用户
                        existingUser.setRealName(realName);
                        existingUser.setEmail(studentId + "@student.edu.cn");
                        // 不更新密码
                        userService.updateUser(existingUser);
                        currentUser = existingUser;
                        updateCount++;
                    } else {
                        // 创建新用户
                        User newUser = new User();
                        newUser.setUsername(studentId);
                        String tempPassword = generateTempPassword();
                        newUser.setPassword(tempPassword); // 随机临时密码
                        newUser.setRealName(realName);
                        newUser.setEmail(studentId + "@student.edu.cn");
                        newUser.setRole("STUDENT");

                        userService.createUser(newUser);
                        // 安全处理：临时密码不写入日志，应通过安全渠道（邮件/短信）下发给学生
                        logger.info("[用户导入] 新建学生账号: username={}", studentId);

                        // 创建后需要重新查询以获取生成的ID
                        currentUser = userService.getUserByUsername(studentId);
                        if (currentUser == null) {
                            errors.add("第" + (i + 1) + "行: 创建用户后无法查询，请检查数据库");
                            failCount++;
                            continue;
                        }
                        successCount++;
                    }

                    // === 新增：处理班级逻辑 ===
                    if (className != null && !className.trim().isEmpty()) {
                        try {
                            // 1. 查找班级是否存在
                            com.english.entity.Class classInfo = classService.getClassByName(className.trim());

                            // 2. 如果班级不存在，创建新班级
                            if (classInfo == null) {
                                com.english.entity.Class newClass = new com.english.entity.Class();
                                newClass.setName(className.trim());
                                newClass.setDescription("通过批量导入自动创建");
                                newClass.setCourseId(null);    // 不关联课程
                                newClass.setTeacherId(null);   // 不指定教师

                                classService.createClassForImport(newClass);

                                // 创建后重新查询以获取ID
                                classInfo = classService.getClassByName(className.trim());
                                if (classInfo == null) {
                                    errors.add("第" + (i + 1) + "行: 创建班级后无法查询");
                                    classJoinFailedCount++;
                                    continue;
                                }

                                classCreatedCount++;
                                if (!createdClassNames.contains(className.trim())) {
                                    createdClassNames.add(className.trim());
                                }
                            }

                            // 3. 将用户加入班级
                            if (classInfo != null && currentUser != null) {
                                // 检查用户是否已在班级中
                                if (!studentClassService.isStudentInClass(currentUser.getId(), classInfo.getId())) {
                                    studentClassService.joinClass(currentUser.getId(), classInfo.getId());
                                    classJoinedCount++;
                                }
                            }
                        } catch (Exception e) {
                            // 班级操作失败，记录错误但不影响用户创建
                            errors.add("第" + (i + 1) + "行: 班级操作失败");
                            logger.error("导入第{}行班级操作失败", i + 1, e);
                            classJoinFailedCount++;
                        }
                    }
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行: 导入失败");
                    logger.error("导入第{}行失败", i + 1, e);
                    failCount++;
                }
            }

            result.put("successCount", successCount);
            result.put("updateCount", updateCount);
            result.put("failCount", failCount);
            result.put("totalProcessed", successCount + updateCount + failCount);
            result.put("errors", errors);
            // 安全提示：新建用户的临时密码已记录到服务器日志，请查看后台日志获取
            result.put("passwordsNote", successCount > 0 ? "已新建 " + successCount + " 个账号，临时密码请查看服务器日志" : null);

            // 新增：班级相关统计
            result.put("classCreatedCount", classCreatedCount);
            result.put("classJoinedCount", classJoinedCount);
            result.put("classJoinFailedCount", classJoinFailedCount);
            result.put("createdClassNames", createdClassNames);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("导入失败", e);

            return Result.error("导入失败，请稍后重试");
        }
    }

    /**
     * 生成随机临时密码（12位，含大小写字母和数字）
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 获取Excel单元格的字符串值
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // 如果是数字,转换为字符串(去掉小数点)
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 格式化为整数字符串,避免科学记数法
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // 使用 DataFormatter 获取公式计算后的显示值，防止公式注入
                return new DataFormatter().formatCellValue(cell);
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    // ========== 内容审核相关 ==========

    /**
     * 获取免审核开关状态
     */
    @GetMapping("/content-review/auto-approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Boolean>> getAutoApproveSettings() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("textAutoApprove", "true".equals(systemConfigService.get("feature_toggle", "content.text_auto_approve")));
        result.put("videoAutoApprove", "true".equals(systemConfigService.get("feature_toggle", "content.video_auto_approve")));
        return Result.success(result);
    }

    /**
     * 设置免审核开关
     */
    @PutMapping("/content-review/auto-approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> setAutoApproveSettings(@RequestBody Map<String, Boolean> settings) {
        if (settings.containsKey("textAutoApprove")) {
            systemConfigService.set("feature_toggle", "content.text_auto_approve",
                    String.valueOf(settings.get("textAutoApprove")), "admin");
        }
        if (settings.containsKey("videoAutoApprove")) {
            systemConfigService.set("feature_toggle", "content.video_auto_approve",
                    String.valueOf(settings.get("videoAutoApprove")), "admin");
        }
        return Result.success("设置成功");
    }

    /**
     * 获取用户上传的课文列表（内容审核）
     */
    @GetMapping("/content-review/texts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getReviewTexts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag) {
        try {
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            final String finalKeyword = keyword;

            QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
            queryWrapper.isNotNull("uploaded_by"); // 仅查用户上传的课文
            queryWrapper.eq("deleted", 0);

            if (status != null && !status.trim().isEmpty()) {
                queryWrapper.eq("status", status);
            }
            if (finalKeyword != null && !finalKeyword.trim().isEmpty()) {
                queryWrapper.and(w -> w.like("title", finalKeyword).or().like("content", finalKeyword));
            }
            if (tag != null && !tag.trim().isEmpty()) {
                // 支持多标签筛选（逗号分隔），每个标签都必须匹配
                String[] tags = tag.split(",");
                for (String t : tags) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        queryWrapper.like("tags", trimmed);
                    }
                }
            }
            queryWrapper.orderByDesc("created_time");

            Page<Text> pageParam = new Page<>(page, size);
            IPage<Text> pageResult = textService.page(pageParam, queryWrapper);

            // 附带作者信息
            List<Map<String, Object>> records = new ArrayList<>();
            for (Text text : pageResult.getRecords()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", text.getId());
                item.put("title", text.getTitle());
                item.put("content", text.getContent());
                item.put("markdownContent", text.getMarkdownContent());
                item.put("contentType", text.getContentType());
                item.put("difficulty", text.getDifficulty());
                item.put("tags", text.getTags());
                item.put("category", text.getCategory());
                item.put("coverUrl", text.getCoverUrl());
                item.put("audioUrl", text.getAudioUrl());
                item.put("status", text.getStatus());
                item.put("isPublic", text.getIsPublic());
                item.put("viewCount", text.getViewCount());
                item.put("likeCount", text.getLikeCount());
                item.put("createdTime", text.getCreatedAt());
                item.put("uploadedBy", text.getUploadedBy());

                // 查询作者信息
                if (text.getUploadedBy() != null) {
                    User uploader = userService.getUserById(text.getUploadedBy());
                    if (uploader != null) {
                        item.put("uploaderName", uploader.getUsername());
                        item.put("uploaderRealName", uploader.getRealName());
                    }
                }
                records.add(item);
            }

            // 统计数量
            long pendingCount = textService.lambdaQuery()
                    .isNotNull(Text::getUploadedBy).eq(Text::getDeleted, 0)
                    .eq(Text::getStatus, "PENDING").count();
            long approvedCount = textService.lambdaQuery()
                    .isNotNull(Text::getUploadedBy).eq(Text::getDeleted, 0)
                    .eq(Text::getStatus, "APPROVED").count();
            long rejectedCount = textService.lambdaQuery()
                    .isNotNull(Text::getUploadedBy).eq(Text::getDeleted, 0)
                    .eq(Text::getStatus, "REJECTED").count();

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", pageResult.getTotal());
            result.put("current", pageResult.getCurrent());
            result.put("size", pageResult.getSize());
            result.put("pendingCount", pendingCount);
            result.put("approvedCount", approvedCount);
            result.put("rejectedCount", rejectedCount);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取待审核课文列表失败", e);
            return Result.error("获取待审核课文列表失败");
        }
    }

    /**
     * 课文审核通过（可同时分配到指定单元）
     */
    @PutMapping("/content-review/texts/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> approveText(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        try {
            Text text = textService.getTextById(id);
            if (text == null) {
                return Result.error("课文不存在");
            }
            text.setStatus("APPROVED");
            text.setIsPublic(1);

            // 如果管理员指定了单元，将课文分配到该单元
            if (body != null && body.get("unitId") != null) {
                try {
                    Long unitId = Long.valueOf(body.get("unitId").toString());
                    // 验证单元存在
                    Unit targetUnit = unitService.getUnitById(unitId);
                    if (targetUnit == null) {
                        return Result.error("目标单元不存在");
                    }
                    text.setUnitId(unitId);
                    logger.info("课文审核通过并分配到单元: textId={}, unitId={}", id, unitId);
                } catch (NumberFormatException e) {
                    return Result.error("unitId 格式错误");
                }
            }

            textService.updateText(text);

            // 事件驱动: 触发活动数据采集
            if (text.getUploadedBy() != null) {
                campaignStatsService.collectUserTasksByTypeAsync(text.getUploadedBy(), TaskType.TEXT_UPLOAD);
            }

            // 触发 AI 版本生成（CET-4、CET-6、学术、中英对照）
            if ("MARKDOWN".equals(text.getContentType())) {
                articleVersionService.checkAndGenerateMarkdownVersions(text.getId());
            } else {
                articleVersionService.checkAndGenerateVersions(text.getId());
            }

            return Result.success("审核通过");
        } catch (Exception e) {
            logger.error("课文审核通过失败 id={}", id, e);
            return Result.error("操作失败");
        }
    }

    /**
     * 课文审核驳回
     */
    @PutMapping("/content-review/texts/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> rejectText(@PathVariable Long id) {
        try {
            Text text = textService.getTextById(id);
            if (text == null) {
                return Result.error("课文不存在");
            }
            text.setStatus("REJECTED");
            text.setIsPublic(0);
            textService.updateText(text);
            return Result.success("已驳回");
        } catch (Exception e) {
            logger.error("课文审核驳回失败 id={}", id, e);
            return Result.error("操作失败");
        }
    }

    /**
     * 获取用户上传的视频列表（内容审核）
     */
    @GetMapping("/content-review/videos")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getReviewVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag) {
        try {
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            final String finalKeyword = keyword;

            QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
            queryWrapper.isNotNull("user_id"); // 仅查用户上传的视频
            queryWrapper.eq("deleted", 0);

            if (status != null && !status.trim().isEmpty()) {
                queryWrapper.eq("status", status);
            }
            if (finalKeyword != null && !finalKeyword.trim().isEmpty()) {
                queryWrapper.and(w -> w.like("title", finalKeyword).or().like("description", finalKeyword));
            }
            if (tag != null && !tag.trim().isEmpty()) {
                String[] tags = tag.split(",");
                for (String t : tags) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        queryWrapper.like("tags", trimmed);
                    }
                }
            }
            queryWrapper.orderByDesc("created_time");

            Page<Video> pageParam = new Page<>(page, size);
            IPage<Video> pageResult = videoService.page(pageParam, queryWrapper);

            // 附带作者信息
            List<Map<String, Object>> records = new ArrayList<>();
            for (Video video : pageResult.getRecords()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", video.getId());
                item.put("title", video.getTitle());
                item.put("description", video.getDescription());
                item.put("mediaId", video.getMediaId());
                item.put("coverMediaId", video.getCoverMediaId());
                item.put("videoUrl", video.getVideoUrl());
                item.put("coverUrl", video.getCoverUrl());
                item.put("tags", video.getTags());
                item.put("status", video.getStatus());
                item.put("isPublic", video.getIsPublic());
                item.put("viewCount", video.getViewCount());
                item.put("likeCount", video.getLikeCount());
                item.put("createdTime", video.getCreatedTime());
                item.put("userId", video.getUserId());
                item.put("subtitleContent", video.getSubtitleContent());

                // 查询作者信息
                if (video.getUserId() != null) {
                    User uploader = userService.getUserById(video.getUserId());
                    if (uploader != null) {
                        item.put("uploaderName", uploader.getUsername());
                        item.put("uploaderRealName", uploader.getRealName());
                    }
                }
                records.add(item);
            }

            // 统计数量
            long pendingCount = videoService.lambdaQuery()
                    .isNotNull(Video::getUserId).eq(Video::getDeleted, 0)
                    .eq(Video::getStatus, "PENDING").count();
            long approvedCount = videoService.lambdaQuery()
                    .isNotNull(Video::getUserId).eq(Video::getDeleted, 0)
                    .eq(Video::getStatus, "APPROVED").count();
            long rejectedCount = videoService.lambdaQuery()
                    .isNotNull(Video::getUserId).eq(Video::getDeleted, 0)
                    .eq(Video::getStatus, "REJECTED").count();

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", pageResult.getTotal());
            result.put("current", pageResult.getCurrent());
            result.put("size", pageResult.getSize());
            result.put("pendingCount", pendingCount);
            result.put("approvedCount", approvedCount);
            result.put("rejectedCount", rejectedCount);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取待审核视频列表失败", e);
            return Result.error("获取待审核视频列表失败");
        }
    }

    /**
     * 视频审核通过
     */
    @PutMapping("/content-review/videos/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> approveVideo(@PathVariable Long id) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            video.setStatus("APPROVED");
            video.setIsPublic(1);
            videoService.updateById(video);
            return Result.success("审核通过");
        } catch (Exception e) {
            logger.error("视频审核通过失败 id={}", id, e);
            return Result.error("操作失败");
        }
    }

    /**
     * 视频审核驳回
     */
    @PutMapping("/content-review/videos/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> rejectVideo(@PathVariable Long id) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            video.setStatus("REJECTED");
            video.setIsPublic(0);
            videoService.updateById(video);
            return Result.success("已驳回");
        } catch (Exception e) {
            logger.error("视频审核驳回失败 id={}", id, e);
            return Result.error("操作失败");
        }
    }

    /**
     * 管理员获取视频详情（不检查 isPublic，用于审核预览）
     */
    @GetMapping("/videos/{id}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Video> getVideoDetail(@PathVariable Long id) {
        try {
            Video video = videoService.getVideoById(id);
            if (video == null) {
                return Result.error("视频不存在");
            }
            return Result.success(video);
        } catch (Exception e) {
            logger.error("获取视频详情失败 id={}", id, e);
            return Result.error("获取视频详情失败");
        }
    }

    // ========== 内容标签管理 ==========

    /**
     * 获取所有内容标签
     */
    @GetMapping("/content-tags")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<ContentTag>> getContentTags() {
        try {
            List<ContentTag> tags = contentTagService.lambdaQuery()
                    .eq(ContentTag::getDeleted, 0)
                    .orderByAsc(ContentTag::getSortOrder)
                    .orderByAsc(ContentTag::getId)
                    .list();
            return Result.success(tags);
        } catch (Exception e) {
            logger.error("获取内容标签列表失败", e);
            return Result.error("获取标签列表失败");
        }
    }

    /**
     * 创建内容标签
     */
    @PostMapping("/content-tags")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ContentTag> createContentTag(@RequestBody Map<String, Object> data) {
        try {
            String name = (String) data.get("name");
            if (name == null || name.trim().isEmpty()) {
                return Result.error("标签名称不能为空");
            }
            name = name.trim();
            if (name.length() > 50) {
                return Result.error("标签名称不能超过50个字符");
            }

            // 检查重名
            long exists = contentTagService.lambdaQuery()
                    .eq(ContentTag::getName, name)
                    .eq(ContentTag::getDeleted, 0)
                    .count();
            if (exists > 0) {
                return Result.error("标签名称已存在");
            }

            ContentTag tag = new ContentTag();
            tag.setName(name);
            tag.setColor(data.get("color") != null ? data.get("color").toString() : null);
            try {
                tag.setSortOrder(data.get("sortOrder") != null ? Integer.parseInt(data.get("sortOrder").toString()) : 0);
            } catch (NumberFormatException e) {
                tag.setSortOrder(0);
            }
            // 1 = 内部标签：仅用于内容追溯，不在学生端展示
            Object internalFlag = data.get("isInternal");
            tag.setIsInternal(Boolean.TRUE.equals(internalFlag) || "1".equals(String.valueOf(internalFlag)) ? 1 : 0);
            tag.setDeleted(0);

            contentTagService.save(tag);
            return Result.success("创建成功", tag);
        } catch (Exception e) {
            logger.error("创建内容标签失败", e);
            return Result.error("创建标签失败");
        }
    }

    /**
     * 更新内容标签
     */
    @PutMapping("/content-tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateContentTag(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        try {
            ContentTag tag = contentTagService.getById(id);
            if (tag == null || (tag.getDeleted() != null && tag.getDeleted() == 1)) {
                return Result.error("标签不存在");
            }

            if (data.containsKey("name")) {
                String name = (String) data.get("name");
                if (name == null || name.trim().isEmpty()) {
                    return Result.error("标签名称不能为空");
                }
                name = name.trim();
                if (name.length() > 50) {
                    return Result.error("标签名称不能超过50个字符");
                }
                // 检查重名（排除自己）
                long exists = contentTagService.lambdaQuery()
                        .eq(ContentTag::getName, name)
                        .ne(ContentTag::getId, id)
                        .eq(ContentTag::getDeleted, 0)
                        .count();
                if (exists > 0) {
                    return Result.error("标签名称已存在");
                }
                tag.setName(name);
            }
            if (data.containsKey("color")) {
                tag.setColor(data.get("color") != null ? data.get("color").toString() : null);
            }
            if (data.containsKey("sortOrder")) {
                try {
                    tag.setSortOrder(data.get("sortOrder") != null ? Integer.parseInt(data.get("sortOrder").toString()) : 0);
                } catch (NumberFormatException e) {
                    tag.setSortOrder(0);
                }
            }
            if (data.containsKey("isInternal")) {
                Object flag = data.get("isInternal");
                tag.setIsInternal(flag == null ? 0
                        : (Boolean.TRUE.equals(flag) || "1".equals(flag.toString()) ? 1 : 0));
            }

            contentTagService.updateById(tag);
            return Result.success("更新成功");
        } catch (Exception e) {
            logger.error("更新内容标签失败 id={}", id, e);
            return Result.error("更新标签失败");
        }
    }

    /**
     * 删除内容标签
     */
    @DeleteMapping("/content-tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteContentTag(@PathVariable Long id) {
        try {
            ContentTag tag = contentTagService.getById(id);
            if (tag == null) {
                return Result.error("标签不存在");
            }
            contentTagService.removeById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            logger.error("删除内容标签失败 id={}", id, e);
            return Result.error("删除标签失败");
        }
    }

    // ==================== 做题记录管理 ====================

    @GetMapping("/quiz-attempts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> listQuizAttempts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long textId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            int offset = (page - 1) * pageSize;
            StringBuilder where = new StringBuilder("WHERE 1=1 ");
            List<Object> params = new ArrayList<>();
            if (studentId != null) { where.append("AND sea.student_id = ? "); params.add(studentId); }
            if (textId != null) { where.append("AND te.text_id = ? "); params.add(textId); }
            if (startTime != null && !startTime.isBlank()) { where.append("AND sea.answer_time >= ? "); params.add(startTime); }
            if (endTime != null && !endTime.isBlank()) { where.append("AND sea.answer_time <= ? "); params.add(endTime); }

            String baseSql =
                "FROM student_exercise_answers sea " +
                "JOIN text_exercises te ON sea.exercise_id = te.id " +
                "LEFT JOIN users u ON sea.student_id = u.id " +
                "LEFT JOIN texts t ON te.text_id = t.id " +
                where +
                "GROUP BY sea.student_id, te.text_id, u.real_name, u.username, t.title ";

            String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql + ") sub";
            Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

            List<Object> dataParams = new ArrayList<>(params);
            dataParams.add(pageSize);
            dataParams.add(offset);
            String dataSql =
                "SELECT sea.student_id AS studentId, te.text_id AS textId, " +
                "u.real_name AS studentRealName, u.username AS studentUsername, " +
                "t.title AS textTitle, " +
                "COUNT(sea.id) AS totalQuestions, " +
                "SUM(CASE WHEN sea.is_correct = 1 THEN 1 ELSE 0 END) AS correctCount, " +
                "SUM(sea.time_spent_seconds) AS totalTimeSeconds, " +
                "MAX(sea.answer_time) AS submittedTime " +
                baseSql +
                "ORDER BY MAX(sea.answer_time) DESC LIMIT ? OFFSET ?";

            List<Map<String, Object>> list = jdbcTemplate.queryForList(dataSql, dataParams.toArray());

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total != null ? total : 0);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("查询做题记录失败", e);
            return Result.error("查询做题记录失败");
        }
    }

    @GetMapping("/quiz-attempts/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getQuizAttemptDetail(
            @RequestParam Long studentId,
            @RequestParam Long textId) {
        try {
            String attemptSql =
                "SELECT sea.student_id AS studentId, te.text_id AS textId, " +
                "u.real_name AS studentRealName, u.username AS studentUsername, " +
                "t.title AS textTitle, " +
                "COUNT(sea.id) AS totalQuestions, " +
                "SUM(CASE WHEN sea.is_correct = 1 THEN 1 ELSE 0 END) AS correctCount, " +
                "SUM(sea.time_spent_seconds) AS totalTimeSeconds, " +
                "MAX(sea.answer_time) AS submittedTime " +
                "FROM student_exercise_answers sea " +
                "JOIN text_exercises te ON sea.exercise_id = te.id " +
                "LEFT JOIN users u ON sea.student_id = u.id " +
                "LEFT JOIN texts t ON te.text_id = t.id " +
                "WHERE sea.student_id = ? AND te.text_id = ? " +
                "GROUP BY sea.student_id, te.text_id, u.real_name, u.username, t.title";
            List<Map<String, Object>> attempts = jdbcTemplate.queryForList(attemptSql, studentId, textId);
            Map<String, Object> attempt = attempts.isEmpty() ? new HashMap<>() : attempts.get(0);

            String recordsSql =
                "SELECT sea.id, sea.exercise_id AS exerciseId, sea.answer_content AS userAnswer, " +
                "sea.is_correct AS isCorrect, sea.time_spent_seconds AS timeSpentSeconds, " +
                "sea.answer_time AS submittedTime, " +
                "te.question AS questionSnapshot, te.exercise_type AS questionType, " +
                "(SELECT eo.option_key FROM exercise_options eo WHERE eo.exercise_id = te.id AND eo.is_correct = 1 LIMIT 1) AS correctAnswer " +
                "FROM student_exercise_answers sea " +
                "JOIN text_exercises te ON sea.exercise_id = te.id " +
                "WHERE sea.student_id = ? AND te.text_id = ? " +
                "ORDER BY sea.answer_time ASC";
            List<Map<String, Object>> records = jdbcTemplate.queryForList(recordsSql, studentId, textId);

            Map<String, Object> result = new HashMap<>();
            result.put("attempt", attempt);
            result.put("records", records);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("查询做题详情失败 studentId={} textId={}", studentId, textId, e);
            return Result.error("查询做题详情失败");
        }
    }

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new BusinessException("未认证");
        }
        User user = userService.getUserByUsername(auth.getName());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

}
