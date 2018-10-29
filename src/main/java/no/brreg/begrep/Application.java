package no.brreg.begrep;

import no.brreg.begrep.etl.JiraExtractor;
import no.brreg.begrep.exceptions.ExtractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.MimeType;

import java.util.HashMap;
import java.util.Map;


@SpringBootApplication
@EnableScheduling
public class Application {
    private static Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private static final JiraExtractor EXTRACTOR = new JiraExtractor();
    private static final Map<MimeType,String> begrepDumps = new HashMap<>();


    @Scheduled(fixedRate = 6*60*60*1000L, initialDelay = 0L)
    public void scheduledExtractFromJira() throws ExtractException {
        LOGGER.debug("Scheduled job triggered");
        EXTRACTOR.extract();
    }

    public static void updateBegrepDump(final MimeType type, final String dump) {
        synchronized (begrepDumps) {
            begrepDumps.put(type, dump);
        }
    }

    public static String getBegrepDump(final MimeType type) {
        synchronized (begrepDumps) {
            return begrepDumps.get(type);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
