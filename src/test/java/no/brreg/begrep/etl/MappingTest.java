package no.brreg.begrep.etl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
public class MappingTest {

    @Test
    public void mappingHappyDay() {
        Mapping mapping = new Mapping("field@nb");
        Assertions.assertEquals("field", mapping.getField());
        Assertions.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithoutLanguage() {
        Mapping mapping = new Mapping("field");
        Assertions.assertEquals("field", mapping.getField());
        Assertions.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void mappingWithoutField() {
        Mapping mapping = new Mapping("@nb");
        Assertions.assertEquals("", mapping.getField());
        Assertions.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithMultipleLanguages() {
        Mapping mapping = new Mapping("field@nb@nn");
        Assertions.assertEquals("field@nb", mapping.getField());
        Assertions.assertEquals("nn", mapping.getLanguage());
    }

    @Test
    public void mappingWithMultipleLanguageMarkers() {
        Mapping mapping = new Mapping("field@@nb");
        Assertions.assertEquals("field@", mapping.getField());
        Assertions.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithEmptyMultipleLanguageMarkers() {
        Mapping mapping = new Mapping("field@@");
        Assertions.assertEquals("field@", mapping.getField());
        Assertions.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void emptyMapping() {
        Mapping mapping = new Mapping("");
        Assertions.assertEquals("", mapping.getField());
        Assertions.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void nullMapping() {
        Mapping mapping = new Mapping(null);
        Assertions.assertEquals("", mapping.getField());
        Assertions.assertEquals(null, mapping.getLanguage());
    }

}
