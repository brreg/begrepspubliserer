package no.brreg.begrep;

public class ExtractException extends Exception {

    public ExtractException(final String msg) {
        super(msg);
    }

    public ExtractException(final String msg, final Throwable throwable) {
        super(msg, throwable);
    }

}
