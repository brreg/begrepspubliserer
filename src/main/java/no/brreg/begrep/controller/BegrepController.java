package no.brreg.begrep.controller;

import no.brreg.begrep.Application;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;


@Controller
@SuppressWarnings({"squid:S00103"})
public class BegrepController {

    public static final MimeType JSON_MIMETYPE    = MimeType.valueOf("application/json");
    public static final MimeType RDF_MIMETYPE     = MimeType.valueOf("application/rdf+xml");
    public static final MimeType TURTLE_MIMETYPE  = MimeType.valueOf("text/turtle");
    private static final List<MimeType> SUPPORTED_MIMETYPES = Arrays.asList(JSON_MIMETYPE, RDF_MIMETYPE, TURTLE_MIMETYPE);

    private static final MimeType DEFAULT_MIMETYPE = MimeType.valueOf("text/plain");
    private static final String DEFAULT_TEXT = "Please use Accept:-header with mime-type '"+JSON_MIMETYPE+"', '"+RDF_MIMETYPE+"' or '"+TURTLE_MIMETYPE+"'";

    private static final int PARAM_PAIR_LENGTH = 2;


    @RequestMapping(value="/ping", method=GET, produces={"text/plain"})
    public ResponseEntity<String> getPing() {
        return ResponseEntity.ok("pong");
    }

    @RequestMapping(value="/ready", method=GET)
    public ResponseEntity getReady() {
        if (Application.getBegrepDump(TURTLE_MIMETYPE) == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } else {
            return ResponseEntity.ok().build();
        }
    }

    @RequestMapping(value="/", method=GET, produces={"application/json","application/rdf+xml","text/turtle"})
    public ResponseEntity<String> getBegreper(@RequestHeader(value = "Accept", required = false) String acceptHeader) {
        MimeType negotiatedMimeType = negotiateMimeType(acceptHeader);
        String content;

        if (negotiatedMimeType == null) {
            negotiatedMimeType = DEFAULT_MIMETYPE;
            content = DEFAULT_TEXT;
        } else {
            content = Application.getBegrepDump(negotiatedMimeType);
        }

        if (content == null) {
            //Not downloaded subjects from Jira yet
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } else {
            return ResponseEntity.ok()
                                 .contentType(MediaType.asMediaType(negotiatedMimeType))
                                 .body(content);
        }
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S135"})
    MimeType negotiateMimeType(final String acceptHeader) {
        if (acceptHeader==null || acceptHeader.isEmpty()) {
            return null;
        }

        //This will contain mime-types and matching quality. <URL: https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1 >
        Map<MimeType,Double> acceptedMimeTypes = new HashMap<>();

        //Accept:-header is comma-separated mediaRange
        String[] mediaRanges = acceptHeader.split(",");
        for (int mediaRangeIndex=0; mediaRangeIndex<mediaRanges.length; mediaRangeIndex++) {
            MimeType mediaType = null;
            double quality = 0.0;

            //mediaRange is either "type/subtype" or "type/subtype;parameter=token[;parameter=token]"
            String mediaRange = mediaRanges[mediaRangeIndex].trim();
            if (mediaRange==null || mediaRange.isEmpty()) {
                //This should not happen, but better safe than sorry
                continue;
            }

            String[] parameters = mediaRange.split(";");
            if (parameters==null || parameters.length<=1) {
                mediaType = MimeType.valueOf(mediaRange);
                quality = 1.0;
            } else {
                //There are parameters present!
                mediaType = MimeType.valueOf(parameters[0]);
                quality = 1.0; //Default to 1.0, but check if quality is one of the parameters

                for (int acceptExtensionIndex=1; acceptExtensionIndex<parameters.length; acceptExtensionIndex++) {
                    String acceptParams = parameters[acceptExtensionIndex].trim();
                    if (acceptParams==null || acceptParams.isEmpty()) {
                        //This should not happen, but better safe than sorry
                        continue;
                    }

                    String[] acceptExtension = acceptParams.split("=");
                    if (acceptExtension!=null && acceptExtension.length==PARAM_PAIR_LENGTH && "q".equals(acceptExtension[0].trim())) {
                        try {
                            quality = Double.valueOf(acceptExtension[1]);
                        } catch (NumberFormatException e) {
                            quality = 0.0;
                        }
                    }
                }
            }

            if (mediaType==null) {
                continue;
            }

            //Collect highest quality value
            if (!acceptedMimeTypes.containsKey(mediaType) || acceptedMimeTypes.get(mediaType)<quality) {
                acceptedMimeTypes.put(mediaType, quality);
            }
        }

        //Check if any compatible wildcard type/subtypes have higher quality
        for (MimeType acceptedMimeType : acceptedMimeTypes.keySet()) {
            if (!acceptedMimeType.isConcrete()) {
                double acceptedQuality = acceptedMimeTypes.get(acceptedMimeType);
                for (Map.Entry<MimeType, Double> entry : acceptedMimeTypes.entrySet()) {
                    if (entry.getKey().isConcrete() &&
                        entry.getKey().isCompatibleWith(acceptedMimeType) &&
                        entry.getValue() < acceptedQuality) {
                        entry.setValue(acceptedQuality);
                    }
                }
            }
        }

        //acceptedMimeTypes now contains all concrete and wildcard mimetypes, and their quality value

        //Find the highest quality mimetype matching our list of supported mimetypes.
        double negotiatedQuality = 0.0;
        MimeType negotiatedMimeType = null;
        for (MimeType supportedMimeType : SUPPORTED_MIMETYPES) {
            for (Map.Entry<MimeType, Double> entry : acceptedMimeTypes.entrySet()) {
                if (entry.getKey().isCompatibleWith(supportedMimeType) &&
                    (negotiatedMimeType==null || entry.getValue()>negotiatedQuality)) {
                    negotiatedMimeType = supportedMimeType;
                    negotiatedQuality = entry.getValue();
                }
            }
        }

        return negotiatedMimeType;
    }

}
