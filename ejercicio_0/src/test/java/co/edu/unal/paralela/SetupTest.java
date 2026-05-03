package co.edu.unal.paralela;

import java.util.Random;

import junit.framework.TestCase;

public class SetupTest extends TestCase {

    /*
     * Un caso de prueba simple.
     */
    public void testSetup() {
        final int resultado = Setup.setup(42);
        assertEquals(42, resultado);
    }
}
