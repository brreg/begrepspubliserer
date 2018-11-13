package no.brreg.begrep.etl;


public class Mapping {
    private String field = "";
    private String language;


    public Mapping(final String value) {
        if (value!=null && !value.isEmpty()) {
            int languageSeparatorPos = value.lastIndexOf('@');
            if (languageSeparatorPos == -1) {
                field = value.trim();
            } else {
                field = value.substring(0, languageSeparatorPos).trim();
                language = value.substring(languageSeparatorPos + 1).trim();
            }
        }

        if ("".equals(language)) {
            language = null;
        }
    }

    public String getField() {
        return this.field;
    }

    public String getLanguage() {
        return this.language;
    }

}
