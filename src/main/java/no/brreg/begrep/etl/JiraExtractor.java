package no.brreg.begrep.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.brreg.begrep.Application;
import no.brreg.begrep.controller.BegrepController;
import no.brreg.begrep.exceptions.ExtractException;
import org.apache.commons.codec.binary.Base64;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RIOT;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;


public class JiraExtractor {
    private static Logger LOGGER = LoggerFactory.getLogger(JiraExtractor.class);

    private static final String JSON_FORMAT   = "RDF/JSON";
    private static final String RDF_FORMAT    = "RDF/XML";
    private static final String TURTLE_FORMAT = "TURTLE";

    private static final String BEGREP_URI = "http://data.brreg.no/begrep/{0}";

    private static final String JIRA_URL = "https://jira.brreg.no/rest/api/2/search?orderBy=id&jql=project=BEGREP+and+status=\"Godkjent\"&maxResults={0}&startAt={1}";
    private static final String JIRA_USER = System.getenv("JIRA_BEGREP_USER");
    private static final String JIRA_PASSWORD = System.getenv("JIRA_BEGREP_PASSWORD");
    private static final int    JIRA_MAX_RESULTS = 50;

    private static Map<String,String> fieldMappings = null;


    private static Model model = null;
    private static Property skosPrefLabelProperty    = null;
    private static Property skosnoDefinisjonProperty = null;
    private static Property skosAltLabelProperty     = null;
    private static Property skosHiddenLabelProperty  = null;

    private static final Pattern STRIP_JIRA_LINKS_PATTERN = Pattern.compile("\\[(.*?)\\|.*?\\]");

    private AtomicBoolean isExtracting = new AtomicBoolean(false);

    private final Application application;


    private JiraExtractor() {
        this.application = null;
    }

    public JiraExtractor(final Application application) {
        this.application = application;
    }

    public void extract() throws ExtractException {
        boolean isAlreadyExtracting = isExtracting.getAndSet(true);
        if (isAlreadyExtracting == false) { //If it isn't already extracting, extract (and make sure isExtracting is reset in the end)
            if (fieldMappings == null) {
                try {
                    loadMappings();
                } catch (IOException e) {
                    throw new ExtractException("Failed to load mapping file: "+e.getMessage(), e);
                }
            }

            initializeStaticModel();

            RestTemplate restTemplate = createRestTemplate();
            ResponseEntity<ObjectNode> response = null;

            try {
                HttpHeaders customHeaders = new HttpHeaders();
                byte[] encodedAuth = Base64.encodeBase64((JIRA_USER+":"+JIRA_PASSWORD).getBytes(Charset.forName("US-ASCII")) );
                String authHeader = "Basic " + new String(encodedAuth, "US-ASCII");
                customHeaders.set("Authorization", authHeader);

                int startAt = 0;
                int totalToFetch = -1;

                int currentFetched = 0;
                int totalFetched = 0;

                do {
                    String url = MessageFormat.format(JIRA_URL, JIRA_MAX_RESULTS, startAt);
                    response = restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(customHeaders),
                            new ParameterizedTypeReference<ObjectNode>() {
                            });

                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new ExtractException("RestTemplate exception: " + response.getStatusCode().toString() + " " + response.getStatusCode().getReasonPhrase());
                    }

                    ObjectNode objectNode = response.getBody();

                    // Update totalToFetch (if possible)
                    JsonNode totalNode = objectNode.findValue("total");
                    if (totalNode!=null && totalNode.canConvertToInt()) {
                        totalToFetch = totalNode.asInt();
                    }

                    currentFetched = 0;
                    JsonNode issues = objectNode.findValue("issues");
                    if (issues!=null && issues.isArray()) {
                        for (Iterator<JsonNode> it = issues.elements(); it.hasNext(); ) {
                            addBegrepToModel(model, it.next());
                            currentFetched++;
                        }
                    }

                    startAt += JIRA_MAX_RESULTS;
                    totalFetched += currentFetched;
                } while (totalFetched<totalToFetch && currentFetched>0);

                if (totalFetched != totalToFetch) {
                    throw new ExtractException("Expected "+Integer.toString(totalToFetch)+". Got "+Integer.toString(totalFetched));
                }

                dumpModel(model, BegrepController.JSON_MIMETYPE);
                dumpModel(model, BegrepController.RDF_MIMETYPE);
                dumpModel(model, BegrepController.TURTLE_MIMETYPE);
            }
            catch (ExtractException e) {
                throw e;
            }
            catch (Exception e) {
                throw new ExtractException("Extract exception: "+e.getMessage(), e);
            }
            finally {
                isExtracting.set(false);
            }
        }
    }

    private void addBegrepToModel(final Model model, final JsonNode jsonNode) {
        JsonNode idNode = jsonNode.findValue("id");
        if (idNode==null || idNode.isNull()) {
            return;
        }

        Resource begrep = model.createResource(MessageFormat.format(BEGREP_URI, idNode.asText()));
        if (begrep == null) {
            return;
        }

        begrep.addProperty(RDF.type, SKOS.Concept);

        JsonNode fieldsNode = jsonNode.findValue("fields");
        if (fieldsNode != null) {
            for (String fieldKey : fieldMappings.keySet()) {
                String fieldValue = fieldMappings.get(fieldKey);
                JsonNode fieldNode = fieldsNode.findValue(fieldValue);
                if (fieldNode==null || fieldNode.isNull()) {
                    continue;
                }

                if ("skos:prefLabel".equals(fieldKey)) {
                    begrep.addProperty(skosPrefLabelProperty, fieldNode.asText());
                } else if ("skosno:definisjon".equals(fieldKey)) {
                    begrep.addProperty(skosnoDefinisjonProperty, stripJiraLinks(fieldNode.asText()));
                } else if ("skos:altLabel".equals(fieldKey)) {
                    begrep.addProperty(skosAltLabelProperty, fieldNode.asText());
                } else if ("skos:hiddenLabel".equals(fieldKey)) {
                    begrep.addProperty(skosHiddenLabelProperty, fieldNode.asText());
                }
            }
        }
    }

    private String stripJiraLinks(final String text) {
        if (text.indexOf('[') == -1) {
            return text;
        }
        return STRIP_JIRA_LINKS_PATTERN.matcher(text).replaceAll("$1");
    }

    private static String mimeTypeToFormat(final MimeType mimeType) {
        if (BegrepController.JSON_MIMETYPE.equals(mimeType)) {
            return JSON_FORMAT;
        } else if (BegrepController.RDF_MIMETYPE.equals(mimeType)) {
            return RDF_FORMAT;
        } else if (BegrepController.TURTLE_MIMETYPE.equals(mimeType)) {
            return TURTLE_FORMAT;
        } else {
            return null;
        }
    }

    private void loadMappings() throws IOException {
        fieldMappings = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("mapping.txt"), StandardCharsets.UTF_8))) {
            String s;
            while ((s=reader.readLine()) != null) {
                String[] keyValueArray = s.split("=");
                if (keyValueArray != null && keyValueArray.length >= 2 && !keyValueArray[0].isEmpty() && !keyValueArray[1].isEmpty()) {
                    fieldMappings.put(keyValueArray[0], keyValueArray[1]);
                }
            }
        }
    }

    private void initializeStaticModel() {
        RIOT.init();

        model = ModelFactory.createDefaultModel();
        model.setNsPrefix("skos", SKOS.uri);
        model.setNsPrefix("skosno", "http://difi.no/skosno#");
        skosPrefLabelProperty    = model.createProperty(SKOS.uri, "prefLabel");
        skosnoDefinisjonProperty = model.createProperty("http://difi.no/skosno#", "definisjon");
        skosAltLabelProperty     = model.createProperty(SKOS.uri, "altLabel");
        skosHiddenLabelProperty  = model.createProperty(SKOS.uri, "hiddenLabel");
    }

    private void dumpModel(final Model model, final MimeType mimeType) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            model.write(baos, mimeTypeToFormat(mimeType));
            application.updateBegrepDump(mimeType, new String(baos.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    RestTemplate createRestTemplate() {
        return new RestTemplate();
    }

}
