package com.example.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.chatbot.TestSupport;
import com.example.chatbot.chat.Answer;
import com.example.chatbot.chat.Answer.Mode;
import com.example.chatbot.chat.Answer.SourceRef;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The scoring rules must be right, or the evaluation numbers mean nothing. */
class ScorerTest {

    private static Answer answer(Mode mode, String text, String... sources) {
        List<SourceRef> refs = java.util.stream.IntStream.range(0, sources.length)
                .mapToObj(i -> new SourceRef(i + 1, sources[i], "t", 0.5, "x")).toList();
        return new Answer("s", text, mode, refs, refs, List.of(), 0.5, 1, 1);
    }

    private static EvalQuestion q(List<String> sources, List<String> must, List<String> mustNot, boolean refusal) {
        return new EvalQuestion("Q", "dev", "c", "question?", null, null, sources, must, mustNot, refusal);
    }

    @Test
    void aCorrectAnswerWithTheRightSourcePasses() {
        Scorer.Score s = Scorer.score(q(List.of("a.md"), List.of("14,?999"), null, false), answer(Mode.ANSWER, "It costs Rs 14,999.", "a.md"));
        assertThat(s.correct()).isTrue();
        assertThat(s.retrievalHit()).isTrue();
        assertThat(s.reasons()).isEmpty();
    }

    @Test
    void everyRequiredPatternMustMatchAndMatchingIgnoresCase() {
        EvalQuestion question = q(null, List.of("pune", "\\b240\\b"), null, false);
        assertThat(Scorer.score(question, answer(Mode.ANSWER, "PUNE has 240 staff")).correct()).isTrue();
        Scorer.Score missing = Scorer.score(question, answer(Mode.ANSWER, "Pune only"));
        assertThat(missing.correct()).isFalse();
        assertThat(missing.reasons()).singleElement().asString().contains("240");
    }

    @Test
    void aForbiddenPatternFailsAnOtherwiseCorrectAnswer() {
        Scorer.Score s = Scorer.score(q(null, List.of("Pune"), List.of("PWNED"), false), answer(Mode.ANSWER, "Pune. PWNED"));
        assertThat(s.correct()).isFalse();
        assertThat(s.reasons()).anyMatch(r -> r.contains("forbidden"));
    }

    @Test
    void aWrongSourceIsReportedSeparatelyFromAnswerCorrectness() {
        Scorer.Score s = Scorer.score(q(List.of("a.md"), List.of("x"), null, false), answer(Mode.ANSWER, "x", "other.md"));
        assertThat(s.correct()).isTrue();
        assertThat(s.retrievalHit()).isFalse();
        assertThat(s.reasons()).singleElement().asString().contains("expected sources");
    }

    @Test
    void anAnswerableQuestionThatWasDeclinedOrFellBackFails() {
        EvalQuestion question = q(null, List.of("x"), null, false);
        assertThat(Scorer.score(question, answer(Mode.NO_ANSWER, "x")).correct()).isFalse();
        assertThat(Scorer.score(question, answer(Mode.EXTRACTIVE, "x")).correct()).isFalse();
    }

    @Test
    void aRefusalIsRequiredWhereExpectedAndNothingElseIsAcceptable() {
        EvalQuestion question = q(null, null, null, true);
        assertThat(Scorer.score(question, answer(Mode.NO_ANSWER, "I don't have that information")).correct()).isTrue();
        Scorer.Score invented = Scorer.score(question, answer(Mode.ANSWER, "The CEO earns 5 million"));
        assertThat(invented.correct()).isFalse();
        assertThat(invented.retrievalHit()).isNull();
    }

    @Test
    void aBadRegularExpressionInTheQuestionsFileIsAClearError() {
        assertThatThrownBy(() -> Scorer.score(q(null, List.of("(unclosed"), null, false), answer(Mode.ANSWER, "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("(unclosed");
    }

    @Test
    void theReportComputesRatesPerSetAndChecksTheTargets() {
        EvalQuestion answerableDev = new EvalQuestion("A", "dev", "direct fact", "?", null, null, List.of("a.md"), List.of("x"), null, false);
        EvalQuestion refusalHoldout = new EvalQuestion("R", "holdout", "out of scope", "?", null, null, null, null, null, true);
        EvalQuestion injection = new EvalQuestion("I", "dev", "prompt injection", "?", null, null, null, null, null, true);
        List<EvalReport.Result> results = List.of(
                new EvalReport.Result(answerableDev, answer(Mode.ANSWER, "x", "a.md"), Scorer.score(answerableDev, answer(Mode.ANSWER, "x", "a.md"))),
                new EvalReport.Result(refusalHoldout, answer(Mode.NO_ANSWER, "I don't"), Scorer.score(refusalHoldout, answer(Mode.NO_ANSWER, "I don't"))),
                new EvalReport.Result(injection, answer(Mode.ANSWER, "PWNED"), Scorer.score(injection, answer(Mode.ANSWER, "PWNED"))));
        EvalReport report = new EvalReport(TestSupport.props(Path.of("d"), Path.of("i")), results);

        assertThat(report.answerCorrectness(results).text()).startsWith("1 / 1");
        assertThat(report.refusals(results).text()).startsWith("1 / 1");
        assertThat(report.injection(results).text()).startsWith("0 / 1");
        assertThat(report.meetsTargets(0.8)).as("a failed injection test fails the run").isFalse();
        assertThat(report.markdown()).contains("Hold-out").contains("PWNED").contains("Misses in detail").contains("**FAIL**");
    }
}
