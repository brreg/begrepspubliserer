package no.brreg.begrep.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.brreg.begrep.Application;
import no.brreg.begrep.controller.BegrepController;
import no.brreg.begrep.exceptions.ExtractException;
import no.difi.skos_ap_no.begrep.builder.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
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


@SuppressWarnings({"squid:S00103"})
public class JiraExtractor {
    private static Logger LOGGER = LoggerFactory.getLogger(JiraExtractor.class);

    private static final String BEGREP_COLLECTION_URI = "http://data.brreg.no/begrep";
    private static final String BEGREP_COLLECTION_NAME = "begrepssamling";
    private static final String BEGREP_URI = BEGREP_COLLECTION_URI + "/{0}";
    private static final String JIRA_URI = getEnvOrDefault("JIRA_URI", "https://jira.brreg.no/rest/api/2/issue/{0}");

    private static final String ANSVARLIG_VIRKSOMHET_ORGNR = System.getenv("ANSVARLIG_VIRKSOMHET_ORGNR");
    private static final String DEFAULT_ANSVARLIG_VIRKSOMHET_ORGNR = "974760673";

    private static final String JIRA_URL = getEnvOrDefault("JIRA_URL", "https://jira.brreg.no/rest/api/2/search?orderBy=id&jql=project=BEGREP+and+status=Godkjent+and+\"Offentlig tilgjengelig?\"=Ja&maxResults={0}&startAt={1}");
    private static final String JIRA_USER = System.getenv("JIRA_BEGREP_USER");
    private static final String JIRA_PASSWORD = System.getenv("JIRA_BEGREP_PASSWORD");
    private static final int JIRA_MAX_RESULTS = 50;

    private static Map<String, Mapping> fieldMappings = null;


    private static final Pattern STRIP_JIRA_LINKS_PATTERN = Pattern.compile("\\[(.*?)\\|.*?\\]");

    private AtomicBoolean isExtracting = new AtomicBoolean(false);

    private final Application application;


    private JiraExtractor() {
        this.application = null;
    }

    public JiraExtractor(final Application application) {
        this.application = application;
    }

    private static String getEnvOrDefault(final String env, final String def) {
        String value = System.getenv(env);
        return value != null ? value : def;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134"})
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

            RestTemplate restTemplate = createRestTemplate();

            try {
                HttpHeaders customHeaders = new HttpHeaders();
                byte[] encodedAuth = Base64.encodeBase64((JIRA_USER + ":" + JIRA_PASSWORD).getBytes(StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.US_ASCII);
                customHeaders.set("Authorization", authHeader);

                int startAt = 0;
                int totalToFetch = -1;

                int currentFetched = 0;
                int totalFetched = 0;

                BegrepssamlingBuilder begrepssamlingBuilder = ModellBuilder.builder().begrepssamlingBuilder(BEGREP_COLLECTION_URI);
                begrepssamlingBuilder.navn(BEGREP_COLLECTION_NAME);
                begrepssamlingBuilder.ansvarligVirksomhet(ANSVARLIG_VIRKSOMHET_ORGNR!=null && !ANSVARLIG_VIRKSOMHET_ORGNR.isEmpty() ? ANSVARLIG_VIRKSOMHET_ORGNR : DEFAULT_ANSVARLIG_VIRKSOMHET_ORGNR);

                do {
                    String url = MessageFormat.format(JIRA_URL, JIRA_MAX_RESULTS, startAt);
                    LOGGER.info("Fetching begrep from Jira: "+url);
                    ResponseEntity<ObjectNode> response = restTemplate.exchange(
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
                            addBegrepToCollection(begrepssamlingBuilder, it.next());
                            currentFetched++;
                        }
                    }

                    startAt += JIRA_MAX_RESULTS;
                    totalFetched += currentFetched;
                } while (totalFetched < totalToFetch && currentFetched > 0);

                if (totalFetched != totalToFetch) {
                    throw new ExtractException("Expected " + totalToFetch + ". Got " + totalFetched);
                }

                LOGGER.info("Finished fetching " + totalFetched + " begrep from Jira");

                Model model = begrepssamlingBuilder.build().build();

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

    private BegrepssamlingBuilder addBegrepToCollection(final BegrepssamlingBuilder begrepssamlingBuilder, final JsonNode jsonNode) {
        JsonNode idNode = jsonNode.findValue("id");
        if (idNode == null || idNode.isNull()) {
            return begrepssamlingBuilder;
        }

        BegrepBuilder begrepBuilder = begrepssamlingBuilder.begrepBuilder(MessageFormat.format(BEGREP_URI, idNode.asText()))
                .identifikator(MessageFormat.format(JIRA_URI, idNode.asText()));

        DefinisjonBuilder definisjonBuilder = null;
        KontaktpunktBegrepBuilder kontaktpunktBuilder = null;
        KildeBuilder kildeBuilder = null;

        for (Map.Entry<String, Mapping> fieldMappingEntry : fieldMappings.entrySet()) {
            String[] fieldPaths = fieldMappingEntry.getKey().split("\\.");
            JsonNode fieldNode = jsonNode;
            for (String fieldPath : fieldPaths) {
                if (fieldNode.has(fieldPath)) {
                    fieldNode = fieldNode.get(fieldPath);
                } else {
                    fieldNode = null;
                    break;
                }
            }

            if (fieldNode == null || fieldNode.isNull()) {
                continue;
            }

            Mapping fieldMapping = fieldMappingEntry.getValue();
            String fieldValue = fieldMapping.getField();
            String language = fieldMapping.getLanguage();

            if ("Begrep.anbefaltTerm".equals(fieldValue)) {
                begrepBuilder.anbefaltTermBuilder().term(stripJiraLinks(fieldNode.asText()), language).build();
            } else if ("Begrep.definisjon".equals(fieldValue)) {
                if (definisjonBuilder == null) {
                    definisjonBuilder = begrepBuilder.definisjonBuilder();
                }
                definisjonBuilder.tekst(stripJiraLinks(fieldNode.asText()), language).build();
            } else if ("Begrep.tillattTerm".equals(fieldValue)) {
                begrepBuilder.tillattTermBuilder().term(stripJiraLinks(fieldNode.asText()), language).build();
            } else if ("Begrep.frarådetTerm".equals(fieldValue)) {
                begrepBuilder.frarådetTermBuilder().term(stripJiraLinks(fieldNode.asText()), language).build();
            } else if ("Begrep.fagområde.tekst".equals(fieldValue)) {
                begrepBuilder.fagområde(stripJiraLinks(fieldNode.asText()), language);
            } else if ("Betydningsbeskrivelse.kilde.tekst".equals(fieldValue)) {
                if (definisjonBuilder == null) {
                    definisjonBuilder = begrepBuilder.definisjonBuilder();
                }
                if (kildeBuilder == null) {
                    kildeBuilder = definisjonBuilder.kildeBuilder();
                }
                kildeBuilder.tekst(stripJiraLinks(fieldNode.asText()), language);
            } else if ("Betydningsbeskrivelse.merknad.tekst".equals(fieldValue)) {
                if (definisjonBuilder == null) {
                    definisjonBuilder = begrepBuilder.definisjonBuilder();
                }
                definisjonBuilder.merknad(stripJiraLinks(fieldNode.asText()), language);
            } else if ("Begrep.kontaktpunkt.epost".equals(fieldValue)) {
                if (kontaktpunktBuilder == null) {
                    kontaktpunktBuilder = begrepBuilder.kontaktpunktBuilder();
                }
                kontaktpunktBuilder.epost(fieldNode.asText());
            } else if ("Begrep.kontaktpunkt.tlf".equals(fieldValue)) {
                if (kontaktpunktBuilder == null) {
                    kontaktpunktBuilder = begrepBuilder.kontaktpunktBuilder();
                }
                kontaktpunktBuilder.telefon(fieldNode.asText());
            } else if ("Begrep.eksempel.tekst".equals(fieldValue)) {
                begrepBuilder.eksempel(stripJiraLinks(fieldNode.asText()), language);
            }
        }

        if (kildeBuilder != null) {
            kildeBuilder.build();
        }

        if (kontaktpunktBuilder != null) {
            kontaktpunktBuilder.build();
        }

        if (definisjonBuilder != null) {
            definisjonBuilder.build();
        }

        return begrepBuilder.build();
    }

    private String stripJiraLinks(final String text) {
        if (text.indexOf('[') == -1) {
            return text;
        }
        return STRIP_JIRA_LINKS_PATTERN.matcher(text).replaceAll("$1");
    }

    private static String mimeTypeToFormat(final MimeType mimeType) {
        String format = null;
        if (BegrepController.JSON_MIMETYPE.equals(mimeType)) {
            format = new RDFFormat(Lang.RDFJSON).toString();
        } else if (BegrepController.RDF_MIMETYPE.equals(mimeType)) {
            format = new RDFFormat(Lang.RDFXML).toString();
        } else if (BegrepController.TURTLE_MIMETYPE.equals(mimeType)) {
            format = new RDFFormat(Lang.TURTLE).toString();
        }
        return format;
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

    private void dumpModel(final Model model, final MimeType mimeType) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            model.write(baos, mimeTypeToFormat(mimeType));
            application.updateBegrepDump(mimeType, new String(baos.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    RestTemplate createRestTemplate() { //For mock/spy in tests
        return new RestTemplate();
    }

}
