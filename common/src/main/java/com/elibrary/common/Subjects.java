package com.elibrary.common;

import java.util.UUID;

/** 从 OAuth2 JWT 的 sub 声明解析可信的 accountId。 */
public final class Subjects {
    private Subjects() {}

    public static UUID accountId(String subject) {
        try {
            return Ids.parse(subject);
        } catch (ApiException exception) {
            throw new ApiException(401, "UNAUTHENTICATED", "Invalid authenticated subject");
        }
    }
}