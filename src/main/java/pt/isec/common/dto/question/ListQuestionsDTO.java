package pt.isec.common.dto.question;

import java.io.Serializable;

public record ListQuestionsDTO(
        Integer teacherId,
        String filter  // "active" | "future" | "expired" | null
) implements Serializable {}
