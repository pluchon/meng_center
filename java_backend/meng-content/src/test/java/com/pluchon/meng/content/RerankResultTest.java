package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.*;
import com.pluchon.meng.content.client.AiComputation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RerankResultTest {
    @Test void sortsScoresAndRejectsForeignOrNonFiniteValues() {
        assertEquals(List.of(1,0),AiComputation.ordered(new AiComputation.RetrievalResponse(
            List.of(0,1),List.of(0.4,0.9),true),2));
        assertThrows(IllegalArgumentException.class,() -> AiComputation.ordered(
            new AiComputation.RetrievalResponse(List.of(2),List.of(0.9),true),2));
        assertThrows(IllegalArgumentException.class,() -> AiComputation.ordered(
            new AiComputation.RetrievalResponse(List.of(0),List.of(Double.NaN),true),2));
        assertEquals(List.of(1,0),AiComputation.ordered(new AiComputation.RetrievalResponse(
            List.of(1,0),List.of(),false),2));
    }
}
