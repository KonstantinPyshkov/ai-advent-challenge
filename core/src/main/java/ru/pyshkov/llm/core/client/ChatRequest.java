package ru.pyshkov.llm.core.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Параметры одного запроса к модели. */
public record ChatRequest(
        String model,
        List<Message> messages,
        double temperature,
        int maxPredict,
        List<String> stop,
        boolean jsonMode,
        JsonNode schema,
        boolean think,
        int numCtx,
        boolean emulateStop
) {

    public ChatRequest {
        messages = List.copyOf(messages);
        stop = List.copyOf(stop);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model;
        private final List<Message> messages = new ArrayList<>();
        private double temperature = 0.7;
        private int maxPredict = 2048;
        private final List<String> stop = new ArrayList<>();
        private boolean jsonMode;
        private JsonNode schema;
        private boolean think;
        private int numCtx = 8192;
        private boolean emulateStop;

        public Builder model(String value) {
            this.model = value;
            return this;
        }

        public Builder message(Message value) {
            this.messages.add(value);
            return this;
        }

        public Builder messages(List<Message> values) {
            this.messages.addAll(values);
            return this;
        }

        public Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        public Builder maxPredict(int value) {
            this.maxPredict = value;
            return this;
        }

        public Builder stop(String value) {
            this.stop.add(value);
            return this;
        }

        public Builder stop(List<String> values) {
            this.stop.addAll(values);
            return this;
        }

        public Builder jsonMode() {
            this.jsonMode = true;
            return this;
        }

        public Builder jsonMode(boolean value) {
            this.jsonMode = value;
            return this;
        }

        public Builder schema(JsonNode value) {
            this.schema = value;
            return this;
        }

        public Builder think(boolean value) {
            this.think = value;
            return this;
        }

        public Builder numCtx(int value) {
            this.numCtx = value;
            return this;
        }

        public Builder emulateStop(boolean value) {
            this.emulateStop = value;
            return this;
        }

        public ChatRequest build() {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("Не задана модель");
            }
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("Не заданы сообщения");
            }
            return new ChatRequest(model, messages, temperature, maxPredict,
                    stop, jsonMode, schema, think, numCtx, emulateStop);
        }
    }
}
