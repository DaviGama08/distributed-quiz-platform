package pt.isec.common.dto.question;

import java.io.Serializable;

public record DeleteQuestionDTO(
        Integer questionId,
        Integer teacherId
) implements Serializable {}
