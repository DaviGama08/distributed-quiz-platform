package pt.isec.common.model.question;

import java.io.Serializable;
import java.util.*;

/**
 * Statistics of answers for a question.
 * <p>
 * Tracks how many answers were given for each option and computes percentages.
 */
public class AnswerStatistics implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer questionId;
    private int totalAnswers;
    private Map<OptionLetter, Integer> countByOption;
    private Map<OptionLetter, Double> percentageByOption;
    private OptionLetter correctOption;
    private int correctAnswersCount;
    private double correctPercentage;

    /**
     * Creates an empty statistics object.
     */
    public AnswerStatistics() {
        this.countByOption = new HashMap<>();
        this.percentageByOption = new HashMap<>();
    }

    /**
     * Creates an empty statistics object for a given question and correct option.
     *
     * @param questionId    question id
     * @param correctOption correct option letter
     */
    public AnswerStatistics(Integer questionId, OptionLetter correctOption) {
        this();
        this.questionId = questionId;
        this.correctOption = correctOption;
        this.totalAnswers = 0;
        this.correctAnswersCount = 0;
        this.correctPercentage = 0.0;
    }

    /**
     * Adds a single answer to the statistics.
     *
     * @param option chosen option
     */
    public void addAnswer(OptionLetter option) {
        countByOption.put(option, countByOption.getOrDefault(option, 0) + 1);
        totalAnswers++;

        if (option.equals(correctOption)) {
            correctAnswersCount++;
        }

        recalculatePercentages();
    }

    /**
     * Adds multiple answers to the statistics.
     *
     * @param answers list of answers
     */
    public void addAnswers(List<Answer> answers) {
        for (Answer answer : answers) {
            addAnswer(answer.getSelectedOption());
        }
    }

    /**
     * Recomputes percentages based on the current counts.
     */
    private void recalculatePercentages() {
        percentageByOption.clear();

        if (totalAnswers == 0) {
            correctPercentage = 0.0;
            return;
        }

        for (Map.Entry<OptionLetter, Integer> entry : countByOption.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / totalAnswers;
            percentageByOption.put(entry.getKey(), percentage);
        }

        correctPercentage = (correctAnswersCount * 100.0) / totalAnswers;
    }

    /**
     * Sets counts for each option directly (useful when loading from DB).
     *
     * @param countByOption map of option to answer count
     */
    public void setCountByOption(Map<OptionLetter, Integer> countByOption) {
        this.countByOption = new HashMap<>(countByOption);
        this.totalAnswers = countByOption.values().stream().mapToInt(Integer::intValue).sum();
        this.correctAnswersCount = countByOption.getOrDefault(correctOption, 0);
        recalculatePercentages();
    }

    // Getters
    public Integer getQuestionId() {
        return questionId;
    }

    public int getTotalAnswers() {
        return totalAnswers;
    }

    public Map<OptionLetter, Integer> getCountByOption() {
        return Collections.unmodifiableMap(countByOption);
    }

    public Map<OptionLetter, Double> getPercentageByOption() {
        return Collections.unmodifiableMap(percentageByOption);
    }

    public OptionLetter getCorrectOption() {
        return correctOption;
    }

    public int getCorrectAnswersCount() {
        return correctAnswersCount;
    }

    public int getIncorrectAnswersCount() {
        return totalAnswers - correctAnswersCount;
    }

    public double getCorrectPercentage() {
        return correctPercentage;
    }

    public double getIncorrectPercentage() {
        return 100.0 - correctPercentage;
    }

    /**
     * Gets the count for a specific option.
     *
     * @param option option letter
     * @return number of answers with that option
     */
    public int getCountFor(OptionLetter option) {
        return countByOption.getOrDefault(option, 0);
    }

    /**
     * Gets the percentage for a specific option.
     *
     * @param option option letter
     * @return percentage of answers with that option
     */
    public double getPercentageFor(OptionLetter option) {
        return percentageByOption.getOrDefault(option, 0.0);
    }

    /**
     * Returns the most chosen option (or {@code null} if none).
     *
     * @return option letter or {@code null}
     */
    public OptionLetter getMostChosenOption() {
        return countByOption.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Returns the least chosen option (or {@code null} if none).
     *
     * @return option letter or {@code null}
     */
    public OptionLetter getLeastChosenOption() {
        return countByOption.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Checks whether there is at least one answer.
     *
     * @return {@code true} if there are answers
     */
    public boolean hasAnswers() {
        return totalAnswers > 0;
    }

    // Setters
    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public void setCorrectOption(OptionLetter correctOption) {
        this.correctOption = correctOption;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AnswerStatistics{questionId=").append(questionId);
        sb.append(", totalAnswers=").append(totalAnswers);
        sb.append(", correctPercentage=").append(String.format("%.2f", correctPercentage)).append("%");
        sb.append(", breakdown={");

        for (Map.Entry<OptionLetter, Integer> entry : countByOption.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            sb.append(" (").append(String.format("%.1f", percentageByOption.get(entry.getKey()))).append("%)");
            if (entry.getKey().equals(correctOption)) {
                sb.append("✓");
            }
            sb.append(", ");
        }

        if (!countByOption.isEmpty()) {
            sb.setLength(sb.length() - 2); // remove trailing comma
        }

        sb.append("}}");
        return sb.toString();
    }
}
