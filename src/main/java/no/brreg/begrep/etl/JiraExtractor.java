package no.brreg.begrep.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.brreg.begrep.Application;
import no.brreg.begrep.controller.BegrepController;
import no.brreg.begrep.exceptions.ExtractException;
import org.apache.commons.codec.binary.Base64;
import org.apache.jena.rdf.model.*;
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
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;


public class JiraExtractor {
    private static Logger LOGGER = LoggerFactory.getLogger(JiraExtractor.class);

    private static final String RDF_NS = "rdf";
    private static final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDFS_NS = "rdfs";
    private static final String RDFS_URI = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String SKOS_NS = "skos";
    private static final String SKOS_URI = "http://www.w3.org/2004/02/skos/core#";
    private static final String DCT_NS = "dct";
    private static final String DCT_URI = "http://purl.org/dc/terms/";
    private static final String SKOSXL_NS = "skosxl";
    private static final String SKOSXL_URI = "http://www.w3.org/2008/05/skos-xl#";
    private static final String XKOS_NS = "xkos";
    private static final String XKOS_URI = "http://rdf-vocabulary.ddialliance.org/xkos#";
    private static final String DCAT_NS = "dcat";
    private static final String DCAT_URI = "http://www.w3.org/ns/dcat#";
    private static final String SKOSNO_NS = "skosno";
    private static final String SKOSNO_URI = "http://difi.no/skosno#";
    private static final String SCHEMA_NS = "schema";
    private static final String SCHEMA_URI = "http://schema.org/";
    private static final String XSD_NS = "xsd";
    private static final String XSD_URI = "http://www.w3.org/2001/XMLSchema#";
    private static final String VCARD_NS = "vcard";
    private static final String VCARD_URI = "http://www.w3.org/2006/vcard/ns#";

    private static final String JSON_FORMAT = "RDF/JSON";
    private static final String RDF_FORMAT = "RDF/XML";
    private static final String TURTLE_FORMAT = "TURTLE";

    private static final String BEGREP_URI = "http://data.brreg.no/begrep/{0}";
    private static final String JIRA_URI = "https://jira.brreg.no/rest/api/2/issue/{0}";
    private static final String ENHETSREGISTER_URI = "https://data.brreg.no/enhetsregisteret/api/enheter/{0}";

    private static final String ANSVARLIG_VIRKSOMHET_ORGNR = System.getenv("ANSVARLIG_VIRKSOMHET_ORGNR");
    private static final String DEFAULT_ANSVARLIG_VIRKSOMHET_ORGNR = "974760673";

    private static final String JIRA_URL = "https://jira.brreg.no/rest/api/2/search?orderBy=id&jql=project=BEGREP+and+status=Godkjent+and+\"Offentlig tilgjengelig?\"=Ja&maxResults={0}&startAt={1}";
    private static final String JIRA_USER = System.getenv("JIRA_BEGREP_USER");
    private static final String JIRA_PASSWORD = System.getenv("JIRA_BEGREP_PASSWORD");
    private static final int JIRA_MAX_RESULTS = 50;

    private static Map<String, Mapping> fieldMappings = null;


    private static Model model = null;
    private static Resource skosnoDefinisjon = null;
    private static Resource skosxlLabel = null;
    private static Property dctIdentifierProperty = null;
    private static Property dctPublisherProperty = null;
    private static Property dctSourceProperty = null;
    private static Property dctSubjectProperty = null;
    private static Property rdfsLabelProperty = null;
    private static Property skosxlPrefLabelProperty = null;
    private static Property skosnoDefinisjonProperty = null;
    private static Property skosAltLabelProperty = null;
    private static Property skosHiddenLabelProperty = null;
    private static Property skosnoBetydningsbeskrivelseProperty = null;
    private static Property skosxlLiteralForm = null;

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
        if (!isAlreadyExtracting) { //If it isn't already extracting, extract (and make sure isExtracting is reset in the end)
            if (fieldMappings == null) {
                try {
                    loadMappings();
                } catch (IOException e) {
                    throw new ExtractException("Failed to load mapping file: " + e.getMessage(), e);
                }
            }

            initializeStaticModel();

            RestTemplate restTemplate = createRestTemplate();
            ResponseEntity<ObjectNode> response = null;

            try {
                HttpHeaders customHeaders = new HttpHeaders();
                byte[] encodedAuth = Base64.encodeBase64((JIRA_USER + ":" + JIRA_PASSWORD).getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.US_ASCII);
                customHeaders.set("Authorization", authHeader);

                int startAt = 0;
                int totalToFetch = -1;

                int currentFetched = 0;
                int totalFetched = 0;

                do {
                    String url = MessageFormat.format(JIRA_URL, JIRA_MAX_RESULTS, startAt);
                    LOGGER.info("Fetching begrep from Jira: "+url);
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
                    if (totalNode != null && totalNode.canConvertToInt()) {
                        totalToFetch = totalNode.asInt();
                    }

                    currentFetched = 0;
                    JsonNode issues = objectNode.findValue("issues");
                    if (issues != null && issues.isArray()) {
                        for (Iterator<JsonNode> it = issues.elements(); it.hasNext(); ) {
                            addBegrepToModel(model, it.next());
                            currentFetched++;
                        }
                    }

                    startAt += JIRA_MAX_RESULTS;
                    totalFetched += currentFetched;
                } while (totalFetched < totalToFetch && currentFetched > 0);

                if (totalFetched != totalToFetch) {
                    throw new ExtractException("Expected " + Integer.toString(totalToFetch) + ". Got " + Integer.toString(totalFetched));
                }

                dumpModel(model, BegrepController.JSON_MIMETYPE);
                dumpModel(model, BegrepController.RDF_MIMETYPE);
                dumpModel(model, BegrepController.TURTLE_MIMETYPE);
            } catch (ExtractException e) {
                throw e;
            } catch (Exception e) {
                throw new ExtractException("Extract exception: " + e.getMessage(), e);
            } finally {
                isExtracting.set(false);
            }
        }
    }

    private void addBegrepToModel(final Model model, final JsonNode jsonNode) {
        JsonNode idNode = jsonNode.findValue("id");
        if (idNode == null || idNode.isNull()) {
            return;
        }

        Resource begrep = model.createResource(MessageFormat.format(BEGREP_URI, idNode.asText()));
        if (begrep == null) {
            return;
        }
        begrep.addProperty(RDF.type, SKOS.Concept);
        begrep.addProperty(dctIdentifierProperty, model.createResource(MessageFormat.format(JIRA_URI, idNode.asText())));

        begrep.addProperty(dctPublisherProperty,
                model.createResource(MessageFormat.format(ENHETSREGISTER_URI, (ANSVARLIG_VIRKSOMHET_ORGNR != null && ENHETSREGISTER_URI.isEmpty()) ? ENHETSREGISTER_URI : DEFAULT_ANSVARLIG_VIRKSOMHET_ORGNR)));

        JsonNode fieldsNode = jsonNode.findValue("fields");
        if (fieldsNode != null) {
            for (Map.Entry<String, Mapping> fieldMappingEntry : fieldMappings.entrySet()) {
                JsonNode fieldNode = fieldsNode.findValue(fieldMappingEntry.getKey());
                if (fieldNode == null || fieldNode.isNull()) {
                    continue;
                }

                Mapping fieldMapping = fieldMappingEntry.getValue();
                String fieldValue = fieldMapping.getField();
                String language = fieldMapping.getLanguage();
                if ("Begrep.anbefaltTerm".equals(fieldValue)) {
                    model.add(begrep, skosxlPrefLabelProperty, createSkosxlLabel(model, stripJiraLinks(fieldNode.asText()), language));
                } else if ("Begrep.definisjon".equals(fieldValue)) {
                    Resource definition = model.createResource(skosnoDefinisjon);
                    Resource source = model.createResource();
                    source.addProperty(rdfsLabelProperty, stripJiraLinks(fieldNode.asText()), language);
                    model.add(definition, dctSourceProperty, source);
                    model.add(begrep, skosnoBetydningsbeskrivelseProperty, definition);
                } else if ("Begrep.tillattTerm".equals(fieldValue)) {
                    model.add(begrep, skosAltLabelProperty, createSkosxlLabel(model, stripJiraLinks(fieldNode.asText()), language));
                } else if ("Begrep.frarådetTerm".equals(fieldValue)) {
                    model.add(begrep, skosHiddenLabelProperty, createSkosxlLabel(model, stripJiraLinks(fieldNode.asText()), language));
                } else if ("Begrep.fagområde.tekst".equals(fieldValue) && fieldNode.has("value")) {
                    model.add(begrep, dctSubjectProperty, stripJiraLinks(fieldNode.get("value").asText()), language);
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

    private Resource createSkosxlLabel(final Model model, final String labelText, final String language) {
        Resource resource = model.createResource(skosxlLabel);
        resource.addProperty(skosxlLiteralForm, labelText, language);
        return resource;
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
                if (keyValueArray.length >= 2 && !keyValueArray[0].isEmpty() && !keyValueArray[1].isEmpty()) {
                    fieldMappings.put(keyValueArray[0], new Mapping(keyValueArray[1]));
                }
            }
        }
    }

    private void initializeStaticModel() {
        RIOT.init();

        model = ModelFactory.createDefaultModel();
        model.setNsPrefix(RDF_NS,    RDF_URI);
        model.setNsPrefix(RDFS_NS,   RDFS_URI);
        model.setNsPrefix(SKOS_NS,   SKOS_URI);
        model.setNsPrefix(DCT_NS,    DCT_URI);
        model.setNsPrefix(SKOSXL_NS, SKOSXL_URI);
        model.setNsPrefix(XKOS_NS,   XKOS_URI);
        model.setNsPrefix(DCAT_NS,   DCAT_URI);
        model.setNsPrefix(SKOSNO_NS, SKOSNO_URI);
        model.setNsPrefix(SCHEMA_NS, SCHEMA_URI);
        model.setNsPrefix(XSD_NS,    XSD_URI);
        model.setNsPrefix(VCARD_NS,  VCARD_URI);
        skosnoDefinisjon         = model.createResource(SKOSNO_URI + "Definisjon");
        skosxlLabel              = model.createResource(SKOSXL_URI + "Label");
        dctIdentifierProperty    = model.createProperty(DCT_URI, "identifier");
        dctPublisherProperty     = model.createProperty(DCT_URI, "publisher");
        dctSourceProperty        = model.createProperty(DCT_URI, "source");
        dctSubjectProperty       = model.createProperty(DCT_URI, "subject");
        rdfsLabelProperty        = model.createProperty(RDFS_URI, "label");
        skosnoDefinisjonProperty = model.createProperty(SKOSNO_URI, "definisjon");
        skosAltLabelProperty     = model.createProperty(SKOS_URI, "altLabel");
        skosHiddenLabelProperty  = model.createProperty(SKOS_URI, "hiddenLabel");
        skosnoBetydningsbeskrivelseProperty = model.createProperty(SKOSNO_URI, "betydningsbeskrivelse");
        skosxlLiteralForm        = model.createProperty(SKOSXL_URI, "literalForm");
        skosxlPrefLabelProperty  = model.createProperty(SKOSXL_URI, "prefLabel");
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
