package ru.pyshkov.llm.core.client;

/** Одно сообщение чата. */
public record Message(String role, String content) {

    public static Message system(String text) {
        return new Message("system", text);
    }

    public static Message user(String text) {
        return new Message("user", text);
    }
}
