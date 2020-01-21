package no.brreg.begrep.controller;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
public class BegrepControllerTest {

    private BegrepController begrepController = new BegrepController();

    @Test
    public void emptyAcceptHeaderShouldReturnNull() {
        Assert.assertEquals(null, begrepController.negotiateMimeType(""));
        Assert.assertEquals(null, begrepController.negotiateMimeType(null));
    }

    @Test
    public void nonOverlappingAcceptHeaderShouldReturnNull() {
        Assert.assertEquals(null, begrepController.negotiateMimeType("text/plain"));
    }

    @Test
    public void jsonHasPriority() {
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("application/json"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("*/*"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/plain,*/*"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/plain;q=0.9,*/*;q=0.8"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle;q=0.9,application/json;q=1.0"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle,application/json"));
    }

    @Test
    public void testWildcard() {
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("text/turtle;q=0.9,application/json;q=0.8,application/*;q=1.0"));
    }

    @Test
    public void testDefaultQuality() {
        Assert.assertEquals(BegrepController.TURTLE_MIMETYPE, begrepController.negotiateMimeType("application/json;q=0.9,application/rdf+xml;q=0.9,text/turtle"));
    }

    @Test
    public void testQuality() {
        Assert.assertEquals(BegrepController.TURTLE_MIMETYPE, begrepController.negotiateMimeType("application/json;q=0.8,text/turtle;asdf=asdf;q=0.9"));
        Assert.assertEquals(BegrepController.JSON_MIMETYPE, begrepController.negotiateMimeType("application/json;q=asdf"));
    }

    @Test
    public void testPing() {
        ResponseEntity<String> response = begrepController.getPing();
        Assert.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        Assert.assertEquals("pong", response.getBody());
    }

    @Test
    public void testReadyWhenNotReady() {
        ResponseEntity response = begrepController.getReady();
        Assert.assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());

    }

    @Test
    public void testGetBegreperNoMimeType() {
        ResponseEntity<String> response = begrepController.getBegreper("");
        Assert.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        final String expected = "Please use Accept:-header with mime-type";
        Assert.assertEquals(expected, response.getBody().substring(0, expected.length()));
    }

    @Test
    public void testGetBegreperNullMimeType() {
        ResponseEntity<String> response = begrepController.getBegreper(null);
        Assert.assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        final String expected = "Please use Accept:-header with mime-type";
        Assert.assertEquals(expected, response.getBody().substring(0, expected.length()));
    }

    @Test
    public void testGetBegreperTextTurtleNotReady() {
        ResponseEntity<String> response = begrepController.getBegreper("text/turtle");
        Assert.assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCodeValue());
    }

}
