package pt.isec.server.model.answer;

import pt.isec.server.model.common.OptionLetter;

import java.io.Serializable;
import java.util.*;

/**
 * Estatísticas de respostas para uma questão
 * Mostra quantas respostas foram dadas para cada opção
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

    public AnswerStatistics() {
        this.countByOption = new HashMap<>();
        this.percentageByOption = new HashMap<>();
    }

    public AnswerStatistics(Integer questionId, OptionLetter correctOption) {
        this();
        this.questionId = questionId;
        this.correctOption = correctOption;
        this.totalAnswers = 0;
        this.correctAnswersCount = 0;
        this.correctPercentage = 0.0;
    }

    /**
     * Adiciona uma resposta às estatísticas
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
     * Adiciona múltiplas respostas às estatísticas
     */
    public void addAnswers(List<Answer> answers) {
        for (Answer answer : answers) {
            addAnswer(answer.getSelectedOption());
        }
    }

    /**
     * Recalcula as percentagens
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
     * Define as contagens diretamente (útil ao carregar da BD)
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
     * Obtém a contagem para uma opção específica
     */
    public int getCountFor(OptionLetter option) {
        return countByOption.getOrDefault(option, 0);
    }

    /**
     * Obtém a percentagem para uma opção específica
     */
    public double getPercentageFor(OptionLetter option) {
        return percentageByOption.getOrDefault(option, 0.0);
    }

    /**
     * Retorna a opção mais escolhida
     */
    public OptionLetter getMostChosenOption() {
        return countByOption.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Retorna a opção menos escolhida
     */
    public OptionLetter getLeastChosenOption() {
        return countByOption.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Verifica se há respostas
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
            sb.setLength(sb.length() - 2); // Remove última vírgula
        }

        sb.append("}}");
        return sb.toString();
    }

    /**
     * Retorna uma representação formatada para exibição
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  ESTATÍSTICAS DA QUESTÃO #").append(questionId).append("\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("Total de Respostas: ").append(totalAnswers).append("\n");
        sb.append("Taxa de Acerto: ").append(String.format("%.2f", correctPercentage)).append("%\n");
        sb.append("───────────────────────────────────────\n");
        sb.append("Distribuição por Opção:\n");

        for (OptionLetter option : OptionLetter.values()) {
            int count = countByOption.getOrDefault(option, 0);
            if (count > 0 || option.equals(correctOption)) {
                double pct = percentageByOption.getOrDefault(option, 0.0);
                sb.append(String.format("  [%s] %3d respostas (%5.1f%%) %s\n",
                    option, count, pct,
                    option.equals(correctOption) ? "✓ CORRETA" : ""));
            }
        }

        sb.append("═══════════════════════════════════════\n");
        return sb.toString();
    }
}

