package no.brreg.begrep.exceptions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
public class ExtractExceptionTest {

    @Test
    public void throwException() {
        Assertions.assertThrows(ExtractException.class, () -> {
            throw new ExtractException("test");
        });
    }

    @Test
    public void throwEmbeddedException() throws ExtractException {
        Assertions.assertThrows(ExtractException.class, () -> {
            try {
                throw new NullPointerException();
            } catch (Exception e) {
                throw new ExtractException("test", e);
            }
        });
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

        Assertions.fail();
    }

    @Test
    public void verifyMessage() {
        try {
            throw new ExtractException("test");
        } catch (Exception e) {
            Assertions.assertEquals("test", e.getMessage());
        }
    }

}
