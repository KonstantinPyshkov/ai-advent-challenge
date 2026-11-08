package ru.pyshkov.llm.core.client;

import java.io.IOException;

/** Порт к любой модели: локальной или удалённой. */
public interface ChatModel {

    ChatResponse chat(ChatRequest request) throws IOException, InterruptedException;
}
