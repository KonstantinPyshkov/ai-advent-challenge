package ru.pyshkov.llm.core.client;

import java.io.IOException;
import java.util.Map;

/** Маршрутизирует запрос к клиенту по идентификатору модели. */
public final class RoutingChatModel implements ChatModel {

    private final Map<String, ChatModel> clients;

    public RoutingChatModel(Map<String, ChatModel> clients) {
        this.clients = Map.copyOf(clients);
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
        ChatModel client = clients.get(request.model());
        if (client == null) {
            throw new IOException("Нет клиента для модели: " + request.model());
        }
        return client.chat(request);
    }
}
