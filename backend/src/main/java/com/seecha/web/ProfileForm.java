package com.seecha.web;

import com.seecha.dao.ProfileDao.Profile;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

final class ProfileForm {
    private static final List<String> AGES = List.of("19~24세", "25~29세", "30대", "40대", "50대 이상");
    static final Map<String, String> AGE_LABELS = Map.of(
            "19~24세", "만 19~24세", "25~29세", "만 25~29세", "30대", "만 30~39세",
            "40대", "만 40~49세", "50대 이상", "만 50세 이상");
    private static final List<String> REGIONS = List.of("서울", "경기", "인천", "부산", "대구", "광주", "대전",
            "울산", "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    private static final List<String> STAGES = List.of("대학생", "대학원생", "취준생", "직장인", "알바생", "자영업자/프리랜서", "기타");
    private static final List<String> MBTIS = List.of("모름", "ISTJ", "ISFJ", "INFJ", "INTJ", "ISTP", "ISFP", "INFP",
            "INTP", "ESTP", "ESFP", "ENFP", "ENTP", "ESTJ", "ESFJ", "ENFJ", "ENTJ");
    private static final List<String> FIELDS = List.of("IT/공학", "인문/사회", "경영/경제", "예술/디자인",
            "의료/보건", "교육", "기타");
    private ProfileForm() {}

    static Profile read(HttpServletRequest request) {
        return new Profile(input(request, "ageGroup"), input(request, "childhoodRegion"),
                input(request, "lifeStage"), input(request, "mbti"), input(request, "field"));
    }

    static boolean valid(Profile profile) {
        return AGES.contains(profile.ageGroup()) && REGIONS.contains(profile.childhoodRegion())
                && STAGES.contains(profile.lifeStage()) && optional(MBTIS, profile.mbti())
                && optional(FIELDS, profile.field());
    }

    static Profile forStorage(Profile profile) {
        return new Profile(profile.ageGroup(), profile.childhoodRegion(), profile.lifeStage(),
                nullable(profile.mbti()), nullable(profile.field()));
    }

    static void prepare(HttpServletRequest request, Profile profile) {
        request.setAttribute("profile", profile == null ? Map.of() : values(profile));
        request.setAttribute("ages", AGES);
        request.setAttribute("ageLabels", AGE_LABELS);
        request.setAttribute("regions", REGIONS);
        request.setAttribute("stages", STAGES);
        request.setAttribute("mbtis", MBTIS);
        request.setAttribute("fields", FIELDS);
    }

    private static boolean optional(List<String> options, String value) {
        return value.isEmpty() || options.contains(value);
    }

    private static String nullable(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String input(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value;
    }

    static Map<String, String> values(Profile profile) {
        return Map.of("ageGroup", profile.ageGroup(), "childhoodRegion", profile.childhoodRegion() == null ? "" : profile.childhoodRegion(), "lifeStage", profile.lifeStage(),
                "mbti", profile.mbti() == null ? "" : profile.mbti(), "field", profile.field() == null ? "" : profile.field());
    }

}
