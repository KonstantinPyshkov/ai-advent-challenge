package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.client.ChatRequest;

import java.util.List;
import java.util.function.Function;

/** Шаг прогона. Может зависеть от результатов предыдущих шагов. */
public interface Step {

    String label();

    ChatRequest request(List<StepResult> previous);

    static Step fixed(String label, ChatRequest request) {
        return new Step() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public ChatRequest request(List<StepResult> previous) {
                return request;
            }
        };
    }

    static Step of(String label, Function<List<StepResult>, ChatRequest> builder) {
        return new Step() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public ChatRequest request(List<StepResult> previous) {
                return builder.apply(previous);
            }
        };
    }
}
