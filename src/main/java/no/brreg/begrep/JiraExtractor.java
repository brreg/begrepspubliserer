package no.brreg.begrep;


import java.util.concurrent.atomic.AtomicBoolean;

public class JiraExtractor {

    private AtomicBoolean isExtracting = new AtomicBoolean(false);


    public void extract() {
        boolean isAlreadyExtracting = isExtracting.getAndSet(true);
        if (isAlreadyExtracting == false) { //If it isn't already extracting, extract (and make sure isExtracting is reset in the end)
            try {

            } finally {
                isExtracting.set(false);
            }
        }
    }

}
