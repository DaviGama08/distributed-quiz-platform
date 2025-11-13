package pt.isec.common.dto.question;

import java.io.Serializable;

public record JoinQuestionDTO(
        String accessCode,
        Integer studentId
) implements Serializable {}

