package com.zn.agentarena.common.handler;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ignoresIoExceptionWhenStreamingResponseIsAlreadyCommitted() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.flushBuffer();

        ResponseEntity<Response<Void>> result = handler.handleIOException(
                new IOException("client disconnected"), response);

        assertNull(result);
    }

    @Test
    void committedStreamingDisconnectDoesNotTriggerASecondMvcResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DisconnectingStreamController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/test/disconnected-stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(""));
    }

    @Test
    void returnsRegularErrorResponseWhenIoExceptionOccursBeforeCommit() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Response<Void>> result = handler.handleIOException(
                new IOException("server-side I/O failure"), response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(ResultCodeEnum.FAIL.getCode(), result.getBody().getCode());
    }

    @RestController
    private static class DisconnectingStreamController {

        @GetMapping(value = "/test/disconnected-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        void disconnect(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.flushBuffer();
            throw new IOException("client disconnected");
        }
    }
}
