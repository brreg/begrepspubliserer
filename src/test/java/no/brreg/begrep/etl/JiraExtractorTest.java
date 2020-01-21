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
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;


@RunWith(SpringRunner.class)
public class JiraExtractorTest {

    private Reader resourceAsReader(final String resourceName) {
        return new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resourceName), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void resetMocks() {
        Mockito.reset();
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

        Model resultModel = ModelFactory.createDefaultModel();
        resultModel.read(new StringReader(application.getBegrepDump(BegrepController.TURTLE_MIMETYPE)), "", BegrepController.TURTLE_MIMETYPE.toString());

        Model fasitModel = ModelFactory.createDefaultModel();
        fasitModel.read(resourceAsReader("jira-example-result.ttl"), "", BegrepController.TURTLE_MIMETYPE.toString());

        if (!resultModel.isIsomorphicWith(fasitModel)) {
            Assert.fail("\nModels are not isomorphic. Got actual:\n" + application.getBegrepDump(BegrepController.TURTLE_MIMETYPE));
        }
    }

    @Test(expected = ExtractException.class)
    public void extractBegrepLoadMappingsFail() throws IOException, ExtractException {
        Application application = new Application();
        JiraExtractor jiraExtractorSpy = spy(new JiraExtractor(application));

        RestTemplate restTemplateMock = mock(RestTemplate.class);
        when(jiraExtractorSpy.createRestTemplate()).thenReturn(restTemplateMock);

        doThrow(IOException.class).when(jiraExtractorSpy).loadMappings();

        jiraExtractorSpy.extract();

        verify(restTemplateMock, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)); //Should not get to exchange(...)
    }

    @Test(expected = ExtractException.class)
    public void extractBegrepRestTemplateExchangeFails() throws ExtractException {
        Application application = new Application();
        JiraExtractor jiraExtractorSpy = spy(new JiraExtractor(application));

        RestTemplate restTemplateMock = mock(RestTemplate.class);
        when(jiraExtractorSpy.createRestTemplate()).thenReturn(restTemplateMock);

        ResponseEntity<ObjectNode> responseEntityMock = mock(ResponseEntity.class);
        when(restTemplateMock.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(responseEntityMock);
        when(responseEntityMock.getStatusCode()).thenReturn(HttpStatus.resolve(501));

        jiraExtractorSpy.extract();
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
