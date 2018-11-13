package no.brreg.begrep.etl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
public class MappingTest {

    @Test
    public void mappingHappyDay() {
        Mapping mapping = new Mapping("field@nb");
        Assert.assertEquals("field", mapping.getField());
        Assert.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithoutLanguage() {
        Mapping mapping = new Mapping("field");
        Assert.assertEquals("field", mapping.getField());
        Assert.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void mappingWithoutField() {
        Mapping mapping = new Mapping("@nb");
        Assert.assertEquals("", mapping.getField());
        Assert.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithMultipleLanguages() {
        Mapping mapping = new Mapping("field@nb@nn");
        Assert.assertEquals("field@nb", mapping.getField());
        Assert.assertEquals("nn", mapping.getLanguage());
    }

    @Test
    public void mappingWithMultipleLanguageMarkers() {
        Mapping mapping = new Mapping("field@@nb");
        Assert.assertEquals("field@", mapping.getField());
        Assert.assertEquals("nb", mapping.getLanguage());
    }

    @Test
    public void mappingWithEmptyMultipleLanguageMarkers() {
        Mapping mapping = new Mapping("field@@");
        Assert.assertEquals("field@", mapping.getField());
        Assert.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void emptyMapping() {
        Mapping mapping = new Mapping("");
        Assert.assertEquals("", mapping.getField());
        Assert.assertEquals(null, mapping.getLanguage());
    }

    @Test
    public void nullMapping() {
        Mapping mapping = new Mapping(null);
        Assert.assertEquals("", mapping.getField());
        Assert.assertEquals(null, mapping.getLanguage());
    }

}
