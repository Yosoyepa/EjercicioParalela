package co.edu.unal.paralela;

import static edu.rice.pcdp.PCDP.finish;
import static edu.rice.pcdp.PCDP.async;

/**
 * Una clase simple para evaluar la compilación de un proyecto hecho con PCDP de la universidad de Rice.
 */
public final class Setup {

    /**
     * Constructor.
     */
    private Setup() {
    }

    /**
     * Un método simple para evaluar la compilación de un proyecto hecho con PCDP.
     * @param val Valor de entrada
     * @return Valor Dummy
     */
    public static int setup(final int val) {
        final int[] resultado = new int[1];
        finish(() -> {
            async(() -> {
                resultado[0] = val;
            });
        });
        return resultado[0];
    }
}
