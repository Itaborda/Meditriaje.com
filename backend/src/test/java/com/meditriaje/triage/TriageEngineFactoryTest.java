package com.meditriaje.triage;

import com.meditriaje.repository.TriageRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriageEngineFactoryTest {

    @Test
    void buildsEngineOnceAndCachesIt() {
        TriageRulesRepository repo = mock(TriageRulesRepository.class);
        when(repo.loadActiveSymptoms()).thenReturn(List.of(new TriageSymptom("FIEBRE", false)));
        when(repo.loadActiveRules("vX")).thenReturn(
                List.of(new TriageRule("FIEBRE", BigDecimal.ZERO, null, 0, 10, PriorityLevel.IV)));
        TriageEngineFactory factory = new TriageEngineFactory(repo, "vX");

        TriageEngine a = factory.getEngine();
        TriageEngine b = factory.getEngine();

        assertThat(a).isSameAs(b);
        verify(repo, times(1)).loadActiveSymptoms();
        verify(repo, times(1)).loadActiveRules("vX");
        assertThat(a.evaluate(new TriageInput(List.of(new ReportedSymptom("FIEBRE", BigDecimal.ONE, 5)))).rulesVersion())
                .isEqualTo("vX");
    }
}
