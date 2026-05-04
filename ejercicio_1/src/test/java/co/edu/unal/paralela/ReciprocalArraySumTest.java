package co.edu.unal.paralela;

import java.util.Random;

import junit.framework.TestCase;

public class ReciprocalArraySumTest extends TestCase {
    // ANSI color codes para impresión en terminal
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";

    // Número de veces que se debe repetir cada prueba para dar consistencia de los resultados de en el tiempo.
    final static private int REPEATS = 60;

    private static int getNCores() {
            return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Crea un arreglo double[] de longitud N para utilizar como entrada para las pruebas.
     *
     * @param N Tamaño del arreglo a crear
     * @return Arreglo double de longitud N inicializado
     */
    private double[] createArray(final int N) {
        final double[] input = new double[N];
        final Random rand = new Random(314);

        for (int i = 0; i < N; i++) {
            input[i] = rand.nextInt(100);
            // No se permiten valores en cero en el arreglo de entrada para evitar la división por cero
            if (input[i] == 0.0) {
                i--;
            }
        }

        return input;
    }

    /**
     * Una implementación de referencia de seqArraysum, en caso de que alguno del archvo del código fuente principal sea modificado accidentalmente.
     *
     * @param input Enrada para calcular secuencialmente la suma de los recíprocos
     * @return Suma de los recíprocos de la entrada
     */
    private double seqArraySum(final double[] input) {
        double sum = 0;

        // Calcula la suma de los recíprocos de los elementos del arreglo
        for (int i = 0; i < input.length; i++) {
            sum += 1 / input[i];
        }

        return sum;
    }

    /**
     * Una función 'helper' para hacer las pruebas de la implementación de dos y la implementación de muchas tareas en paralelo.
     *
     * @param N Tamaño del arreglo utilizado para las pruebas
     * @param useManyTaskVersion Switch entre el código la versión de dos tareas en paralelo y la versión de muchas tareas en paralelo
     * @param ntasks Número de tareas a utilizar
     * @return La mejora en la rapidez (speedup) alcanzada, no todas las pruebas utilizan esta información
     */
    private double parTestHelper(final int N, final boolean useManyTaskVersion, final int ntasks) {
        // Crea un arreglo de entrada de manera aleatoria
        final double[] input = createArray(N);
        // Utilza una version secuencial para calcular el resultado correcto
        final double correct = seqArraySum(input);
        // Utiliza la implementación paralela para calcular el resultado
        double sum;
        if (useManyTaskVersion) {
            sum = ReciprocalArraySum.parManyTaskArraySum(input, ntasks);
        } else {
            assert ntasks == 2;
            sum = ReciprocalArraySum.parArraySum(input);
        }
        final double err = Math.abs(sum - correct);
        // Asegura que la salida esperada sea la calculada
        final String errMsg = String.format("No concuerda el resultado para N = %d, valor esperado = %f, valor calculado = %f, error " +
                "absoluto = %f", N, correct, sum, err);
        assertTrue(errMsg, err < 1E-2);

        /*
         * Ejecuta varias repeticiones de la versiones secuncial y paralela para obtener una medida más exacta del desempeño paralelo.
         */
        final long seqStartTime = System.nanoTime();
        for (int r = 0; r < REPEATS; r++) {
            seqArraySum(input);
        }
        final long seqEndTime = System.nanoTime();

        final long parStartTime = System.nanoTime();
        for (int r = 0; r < REPEATS; r++) {
            if (useManyTaskVersion) {
                ReciprocalArraySum.parManyTaskArraySum(input, ntasks);
            } else {
                assert ntasks == 2;
                ReciprocalArraySum.parArraySum(input);
            }
        }
        final long parEndTime = System.nanoTime();

        final double seqTime = (double)(seqEndTime - seqStartTime) / REPEATS;
        final double parTime = (double)(parEndTime - parStartTime) / REPEATS;

        return seqTime / parTime;
    }

    /**
     * Prueba que la implementación de dos tareas en paralelo calcula correctamente los resultados para arreglos con un millón de elementos.
     */
    public void testParSimpleTwoMillion() {
        System.out.println(ANSI_CYAN + "\n=========================================================================" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "▶ EJECUTANDO PRUEBA 1: Paralelismo Simple (2 tareas) - 2 Millones de elementos" + ANSI_RESET);

        final double minimalExpectedSpeedup = 1.5;
        final double speedup = parTestHelper(2_000_000, false, 2);
        final String errMsg = String.format("Se esperaba que la implementación de dos tareas en paralelo pudiera ejecutarse " +
                " %fx veces más rápido, pero solo alcanzo a mejorar la rapidez (speedup) %fx veces", minimalExpectedSpeedup, speedup);
        
        if (speedup >= minimalExpectedSpeedup) {
            System.out.println(ANSI_GREEN + "   ✅ PRUEBA EXITOSA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + minimalExpectedSpeedup + "x)" + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "   ❌ PRUEBA FALLIDA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + minimalExpectedSpeedup + "x)" + ANSI_RESET);
        }

        System.out.println(ANSI_CYAN + "=========================================================================\n" + ANSI_RESET);
        assertTrue(errMsg, speedup >= minimalExpectedSpeedup);
    }

    /**
     * Prueba que la implementación de dos tareas en paralelo calcula correctamente los resultados para arreglos con cientos de millones de elementos..
     */
    public void testParSimpleTwoHundredMillion() {
        System.out.println(ANSI_CYAN + "\n=========================================================================" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "▶ EJECUTANDO PRUEBA 2: Paralelismo Simple (2 tareas) - 200 Millones de elementos" + ANSI_RESET);

        final double speedup = parTestHelper(200_000_000, false, 2);
        final double minimalExpectedSpeedup = 1.5;
        final String errMsg = String.format("Se esperaba que la implementación de dos tareas en paralelo pudiera ejecutarse " +
                "%fx veces más rápido, pero solo alcanzo a mejorar la rapidez (speedup) %fx veces", minimalExpectedSpeedup, speedup);
        
        if (speedup >= minimalExpectedSpeedup) {
            System.out.println(ANSI_GREEN + "   ✅ PRUEBA EXITOSA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + minimalExpectedSpeedup + "x)" + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "   ❌ PRUEBA FALLIDA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + minimalExpectedSpeedup + "x)" + ANSI_RESET);
        }

        System.out.println(ANSI_CYAN + "=========================================================================\n" + ANSI_RESET);
        assertTrue(errMsg, speedup >= minimalExpectedSpeedup);
    }

    /**
     * Prueba que la implementación de muchas tareas en paralelo calcula correctamente los resultados para arreglos con un millónde elementos.
     */
    public void testParManyTaskTwoMillion() {
        System.out.println(ANSI_CYAN + "\n=========================================================================" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "▶ EJECUTANDO PRUEBA 3: Paralelismo Múltiple (Muchas tareas) - 2 Millones de elementos" + ANSI_RESET);

        final int ncores = getNCores();
        System.out.println(ANSI_YELLOW + "   [*] Utilizando " + ncores + " núcleos detectados en el sistema" + ANSI_RESET);
        
        final double minimalExpectedSpeedup = (double)ncores * 0.6;
        final double speedup = parTestHelper(2_000_000, true, ncores);
        final String errMsg = String.format("Se esperaba que la implmentación de muchas tareas en paralelo pudiera ejecutarse " +
                "%fx veces más rápido, pero solo alcanzo a mejorar la rapidez (speedup) %fx veces", minimalExpectedSpeedup, speedup);
        
        if (speedup >= minimalExpectedSpeedup) {
            System.out.println(ANSI_GREEN + "   ✅ PRUEBA EXITOSA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + String.format("%.2f", minimalExpectedSpeedup) + "x)" + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "   ❌ PRUEBA FALLIDA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + String.format("%.2f", minimalExpectedSpeedup) + "x)" + ANSI_RESET);
        }

        System.out.println(ANSI_CYAN + "=========================================================================\n" + ANSI_RESET);
        assertTrue(errMsg, speedup >= minimalExpectedSpeedup);
    }

    /**
     * Prueba que la implementación de muchas tareas en paralelo calcula correctamente los resultados para arreglos con cientos de millones de elementos.
     */
    public void testParManyTaskTwoHundredMillion() {
        System.out.println(ANSI_CYAN + "\n=========================================================================" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "▶ EJECUTANDO PRUEBA 4: Paralelismo Múltiple (Muchas tareas) - 200 Millones de elementos" + ANSI_RESET);

        final int ncores = getNCores();
        System.out.println(ANSI_YELLOW + "   [*] Utilizando " + ncores + " núcleos detectados en el sistema" + ANSI_RESET);

        final double speedup = parTestHelper(200_000_000, true, ncores);
        final double minimalExpectedSpeedup = (double)ncores * 0.8;
        final String errMsg = String.format("Se esperaba que la implmentación de muchas tareas en paralelo pudiera ejecutarse " +
                " %fx veces más rápido, pero solo alcanzo a mejorar la rapidez (speedup) %fx veces", minimalExpectedSpeedup, speedup);
        
        if (speedup >= minimalExpectedSpeedup) {
            System.out.println(ANSI_GREEN + "   ✅ PRUEBA EXITOSA: Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + String.format("%.2f", minimalExpectedSpeedup) + "x)" + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "   ❌ PRUEBA FALLIDA (LIMITACIÓN DE HARDWARE): Speedup = " + String.format("%.2f", speedup) + "x (Esperado: >=" + String.format("%.2f", minimalExpectedSpeedup) + "x)" + ANSI_RESET);
        }

        System.out.println(ANSI_CYAN + "=========================================================================\n" + ANSI_RESET);
        assertTrue(errMsg, speedup >= minimalExpectedSpeedup);
    }
}
