package no.brreg.begrep.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class JiraExtractorTest {

    private Reader resourceAsReader(final String resourceName) {
        return new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resourceName), StandardCharsets.UTF_8);
    }

    private String resourceAsString(final String resourceName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(resourceAsReader(resourceName))) {
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void extractBegrep() throws ExtractException, IOException {
        Application application = new Application();
        JiraExtractor jiraExtractorSpy = spy(new JiraExtractor(application));

        RestTemplate restTemplateMock = mock(RestTemplate.class);
        when(jiraExtractorSpy.createRestTemplate()).thenReturn(restTemplateMock);

        ResponseEntity<ObjectNode> responseEntityMock = mock(ResponseEntity.class);
        when(restTemplateMock.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(responseEntityMock);
        when(responseEntityMock.getStatusCode()).thenReturn(HttpStatus.resolve(200));

        ObjectNode exampleJsonAsNode = (ObjectNode) new ObjectMapper().readTree(resourceAsReader("jira-example.json"));
        when(responseEntityMock.getBody()).thenReturn(exampleJsonAsNode);

        jiraExtractorSpy.extract();

        String resultTurtle = application.getBegrepDump(BegrepController.TURTLE_MIMETYPE);
        String fasitTurtle = resourceAsString("jira-example-result.ttl");
        Assert.assertEquals(fasitTurtle, resultTurtle);
    }

    @Test
    public void reimportBegrep() {
        RIOT.init();
        Model model = ModelFactory.createDefaultModel();
        model.read(resourceAsReader("jira-example-result.ttl"), "http://data.brreg.no", "TURTLE");

        Assert.assertTrue(model.contains(
                model.createResource("http://data.brreg.no/begrep/57994"),
                RDF.type,
                SKOS.Concept));

        Assert.assertFalse(model.contains(
                model.createResource("http://data.brreg.no/begrep/57994asdf"),
                RDF.type,
                SKOS.Concept));
    }

}
