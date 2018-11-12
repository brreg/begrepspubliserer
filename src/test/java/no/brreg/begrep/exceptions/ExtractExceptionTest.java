package no.brreg.begrep.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.javaws.exceptions.InvalidArgumentException;
import no.brreg.begrep.Application;
import no.brreg.begrep.controller.BegrepController;
import no.brreg.begrep.exceptions.ExtractException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RIOT;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;


@RunWith(SpringRunner.class)
public class ExtractExceptionTest {

    @Test(expected = ExtractException.class)
    public void throwException() throws ExtractException {
        throw new ExtractException("test");
    }

    @Test(expected = ExtractException.class)
    public void throwEmbeddedException() throws ExtractException {
        try {
            throw new NullPointerException();
        } catch (Exception e) {
            throw new ExtractException("test", e);
        }
    }

    @Test
    public void verifySuperclass() {
        try {
            throw new ExtractException("test");
        } catch (Exception e) {
            if (e instanceof ExtractException) {
                return;
            }
        }

        Assert.fail();
    }

    @Test
    public void verifyMessage() {
        try {
            throw new ExtractException("test");
        } catch (Exception e) {
            Assert.assertEquals("test", e.getMessage());
        }
    }

}
