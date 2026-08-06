package com.pixierge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void exposesResponseStatusReasonAsProblemDetail() {
    ResponseEntity<ProblemDetail> response =
        handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path does not exist"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDetail()).isEqualTo("Source path does not exist");
  }
}
