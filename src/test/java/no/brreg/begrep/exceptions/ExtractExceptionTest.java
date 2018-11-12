package no.brreg.begrep.exceptions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
public class ExtractExceptionTest {

    @Test(expected = ExtractException.class)
    public void throwException() throws ExtractException {
        throw new ExtractException("test");
    }

    @Test(expected = ExtractException.class)
    public void throwEmbeddedException() throws ExtractException {
        try {
            throw new NullPointerException();
        } catch (Exception e) {
            throw new ExtractException("test", e);
        }
    }

    @Test
    public void verifySuperclass() {
        try {
            throw new ExtractException("test");
        } catch (Exception e) {
            if (e instanceof ExtractException) {
                return;
            }
        }

        Assert.fail();
    }

    @Test
    public void verifyMessage() {
        try {
            throw new ExtractException("test");
        } catch (Exception e) {
            Assert.assertEquals("test", e.getMessage());
        }
    }

}
