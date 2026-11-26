package com.seecha.web;

final class Nickname {
    static final String HELP = "닉네임은 한글, 영문, 숫자, 밑줄을 사용해 1~16자로 입력해 주세요.";

    static final String TAKEN = "이미 사용 중인 닉네임이에요. 다른 닉네임을 입력해 주세요.";

    private Nickname() {}

    static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    static boolean valid(String value) {
        return value.matches("[가-힣A-Za-z0-9_]{1,16}");
    }
}
