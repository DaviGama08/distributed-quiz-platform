package pt.isec.common.dto.question;

import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Option;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record EditQuestionDTO(
        Integer questionId,
        Integer teacherId,
        String statement,
        List<Option> options,
        OptionLetter correctOption,
        LocalDateTime startAt,
        LocalDateTime endAt
) implements Serializable {}
