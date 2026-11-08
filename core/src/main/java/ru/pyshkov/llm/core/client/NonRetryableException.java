package ru.pyshkov.llm.core.client;

import java.io.IOException;

/** Ошибка запроса, которую бессмысленно повторять: 4xx, кроме 429. */
public final class NonRetryableException extends IOException {

    public NonRetryableException(String message) {
        super(message);
    }
}
