package com.zn.agentarena.modules.turing.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TuringHuntHumanActionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsTextBeforeAnOversizedRequestReachesTheGameEngine() {
        TuringHuntHumanActionRequest request = new TuringHuntHumanActionRequest();
        request.setAction("A".repeat(33));
        request.setAnswer("答".repeat(91));
        request.setReasonSummary("理".repeat(51));
        request.setQuestion("问".repeat(46));
        request.setResponse("回".repeat(71));
        request.setDefense("辩".repeat(81));

        Set<String> fields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("action", "answer", "reasonSummary", "question", "response", "defense"), fields);
    }
}
