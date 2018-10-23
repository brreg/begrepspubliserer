package no.brreg.begrep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;


@SpringBootApplication
@EnableScheduling
public class Application {
    private static Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private static final JiraExtractor EXTRACTOR = new JiraExtractor();


    @Scheduled(fixedRate = 6*60*60*1000L, initialDelay = 0L)
    public void scheduledExtractFromJira() {
        LOGGER.debug("Scheduled job triggered");
        EXTRACTOR.extract();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
