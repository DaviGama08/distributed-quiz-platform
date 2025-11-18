package pt.isec.common.dto.question;

import java.io.Serializable;

public record CreateQuestionResponseDTO(
        Integer questionId,
        String accessCode
) implements Serializable {}