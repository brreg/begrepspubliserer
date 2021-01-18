package no.brreg.begrep.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


@SpringBootTest
public class BegrepControllerTest {

    @Test
    public void emptyAcceptHeaderShouldReturnNull() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(null, begrepController.negotiateMimeType(""));
        Assertions.assertEquals(null, begrepController.negotiateMimeType(null));
    }

    @Test
    public void nonOverlappingAcceptHeaderShouldReturnNull() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(null, begrepController.negotiateMimeType("text/plain"));
    }

    @Test
    public void jsonHasPriority() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("application/json"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("*/*"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/plain,*/*"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/plain;q=0.9,*/*;q=0.8"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle;q=0.9,application/json;q=1.0"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle,application/json"));
    }

    @Test
    public void testWildcard() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle;q=0.9,application/json;q=0.8,application/*;q=1.0"));
    }

    @Test
    public void testDefaultQuality() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(BegrepController.TURTLE_MIMETYPE, begrepController.negotiateMimeType("application/json;q=0.9,application/rdf+xml;q=0.9,text/turtle"));
    }

    @Test
    public void testQuality() {
        BegrepController begrepController = new BegrepController();
        Assertions.assertEquals(BegrepController.TURTLE_MIMETYPE, begrepController.negotiateMimeType("application/json;q=0.8,text/turtle;asdf=asdf;q=0.9"));
        Assertions.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("application/json;q=asdf"));
    }

    @Test
    public void testPing() {
        BegrepController begrepController = new BegrepController();
        ResponseEntity<String> response = begrepController.getPing();
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        Assertions.assertEquals("pong", response.getBody());
    }

    @Test
    public void testGetBegreperNoMimeType() {
        BegrepController begrepController = new BegrepController();
        ResponseEntity<String> response = begrepController.getBegreper("");
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        final String expected = "Please use Accept:-header with mime-type";
        Assertions.assertEquals(expected, response.getBody().substring(0, expected.length()));
    }

    @Test
    public void testGetBegreperNullMimeType() {
        BegrepController begrepController = new BegrepController();
        ResponseEntity<String> response = begrepController.getBegreper(null);
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        final String expected = "Please use Accept:-header with mime-type";
        Assertions.assertEquals(expected, response.getBody().substring(0, expected.length()));
    }

}
