package pt.isec.common.dto.answer;

import java.io.Serializable;

public record ViewAnswersDTO(
        Integer questionId,
        Integer teacherId
) implements Serializable {}

