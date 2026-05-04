package co.edu.unal.paralela;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Sustentación - Ejercicio 1: Paralelismo basado en Tareas.
 *
 * Algoritmo: Suma de los Recíprocos de un arreglo  ->  S = Σ (1 / x_i).
 *
 * Tipo de paralelismo: PARALELISMO DE DATOS (Data Parallelism).
 *   Cada división 1/x_i es independiente del resto, por lo que el problema
 *   se presta naturalmente al enfoque "divide y vencerás" del framework
 *   Fork-Join de Java.
 *
 * Mapa del código (referencia al guion de sustentación):
 *   - Sección D: getPool()                  -> Caché del ForkJoinPool (Singleton)
 *   - Sección A: ReciprocalArraySumTask     -> Unidad de trabajo / nódulo hoja
 *   - Sección B: parArraySum()              -> Paralelismo estricto de 2 tareas
 *   - Sección C: parManyTaskArraySum()      -> Paralelismo escalable a N tareas
 */
public final class ReciprocalArraySum {

    // =========================================================================
    // SECCIÓN D - OPTIMIZACIÓN CRÍTICA: "CACHÉ" DEL POOL (Patrón Singleton)
    // =========================================================================
    // Problema: el benchmark llama parManyTaskArraySum 60 veces. Si en cada
    //           llamada hacemos 'new ForkJoinPool(numTasks)' el OVERHEAD de
    //           crear/destruir hilos del SO se vuelve dominante y el código
    //           paralelo termina más lento que el secuencial.
    // Solución: guardamos la piscina de hilos en una variable estática y la
    //           reutilizamos durante toda la vida del programa. Solo se
    //           recrea si cambia el paralelismo solicitado.
    // Resultado: aumento drástico del Speedup en los tests con muchas tareas.
    private static ForkJoinPool sharedPool;

    private static synchronized ForkJoinPool getPool(final int numTasks) {
        if (sharedPool == null || sharedPool.getParallelism() != numTasks) {
            if (sharedPool != null) {
                sharedPool.shutdown();
            }
            sharedPool = new ForkJoinPool(numTasks);
        }
        return sharedPool;
    }

    /**
     * Constructor.
     */
    private ReciprocalArraySum() {
    }

    /**
     * Calcula secuencialmente la suma de valores recíprocos para un arreglo.
     *
     * @param input Arreglo de entrada
     * @return La suma de los recíprocos del arreglo de entrada
     */
    protected static double seqArraySum(final double[] input) {
        double sum = 0;

        // Calcula la suma de los recíprocos de los elementos del arreglo
        for (int i = 0; i < input.length; i++) {
            sum += 1 / input[i];
        }

        return sum;
    }

    /**
     * calcula el tamaño de cada trozo o sección, de acuerdo con el número de secciones para crear
     * a través de un número dado de elementos.
     *
     * @param nChunks El número de secciones (chunks) para crear
     * @param nElements El número de elementos para dividir
     * @return El tamaño por defecto de la sección (chunk)
     */
    private static int getChunkSize(final int nChunks, final int nElements) {
        // Función techo entera
        return (nElements + nChunks - 1) / nChunks;
    }

    /**
     * Calcula el índice del elemento inclusivo donde la sección/trozo (chunk) inicia,
     * dado que hay cierto número de secciones/trozos (chunks).
     *
     * @param chunk la sección/trozo (chunk) para cacular la posición de inicio
     * @param nChunks Cantidad de secciones/trozos (chunks) creados
     * @param nElements La cantidad de elementos de la sección/trozo que deben atravesarse
     * @return El índice inclusivo donde esta sección/trozo (chunk) inicia en el conjunto de
     *         nElements
     */
    private static int getChunkStartInclusive(final int chunk,
            final int nChunks, final int nElements) {
        final int chunkSize = getChunkSize(nChunks, nElements);
        return chunk * chunkSize;
    }

    /**
     * Calcula el índice del elemento exclusivo que es proporcionado al final de la sección/trozo (chunk),
     * dado que hay cierto número de secciones/trozos (chunks).
     *
     * @param chunk La sección para calcular donde termina
     * @param nChunks Cantidad de secciones/trozos (chunks) creados
     * @param nElements La cantidad de elementos de la sección/trozo que deben atravesarse
     * @return El índice de terminación exclusivo para esta sección/trozo (chunk)
     */
    private static int getChunkEndExclusive(final int chunk, final int nChunks,
            final int nElements) {
        final int chunkSize = getChunkSize(nChunks, nElements);
        final int end = (chunk + 1) * chunkSize;
        if (end > nElements) {
            return nElements;
        } else {
            return end;
        }
    }

    // =========================================================================
    // SECCIÓN A - LA UNIDAD DE TRABAJO (Tarea indivisible / "nódulo hoja")
    // =========================================================================
    // Cada instancia de esta clase representa UN trozo (chunk) del arreglo
    // que un único hilo procesará de forma aislada.
    //
    // Hereda de RecursiveAction porque la tarea NO devuelve un valor por
    // 'return' (compute() es void); en su lugar, deja el subtotal en el
    // campo 'value' y el hilo padre lo recoge con getValue().
    //
    // Como cada tarea escribe únicamente sobre SU PROPIO campo 'value' y
    // sobre SU PROPIO rango [start, end) del arreglo, no existen escrituras
    // compartidas: el diseño evita por construcción cualquier RACE CONDITION.
    private static class ReciprocalArraySumTask extends RecursiveAction {
        /**
         * Iniciar el índice para el recorrido transversal hecho por esta tarea.
         */
        private final int startIndexInclusive;
        /**
         * Concluir el índice para el recorrido transversal hecho por esta tarea.
         */
        private final int endIndexExclusive;
        /**
         * Arreglo de entrada para la suma de recíprocos.
         */
        private final double[] input;
        /**
         * Valor intermedio producido por esta tarea.
         */
        private double value;

        /**
         * Constructor.
         * @param setStartIndexInclusive establece el índice inicial para comenzar
         *        el recorrido trasversal.
         * @param setEndIndexExclusive establece el índice final para el recorrido trasversal.
         * @param setInput Valores de entrada
         */
        ReciprocalArraySumTask(final int setStartIndexInclusive,
                final int setEndIndexExclusive, final double[] setInput) {
            this.startIndexInclusive = setStartIndexInclusive;
            this.endIndexExclusive = setEndIndexExclusive;
            this.input = setInput;
        }

        /**
         * Adquiere el valor calculado por esta tarea.
         * @return El valor calculado por esta tarea
         */
        public double getValue() {
            return value;
        }

        @Override
        protected void compute() {
            // SECCIÓN A: cuerpo de la tarea hoja.
            // Recorre EXCLUSIVAMENTE su chunk [startIndexInclusive, endIndexExclusive)
            // y acumula el subtotal en 'value'. Es la operación atómica de
            // trabajo desde el punto de vista del framework Fork-Join.
            value = 0;
            for (int i = startIndexInclusive; i < endIndexExclusive; i++) {
                value += 1 / input[i];
            }
        }
    }

    // =========================================================================
    // SECCIÓN B - PARALELISMO ESTRICTO DE 2 TAREAS
    // =========================================================================
    // Patrón clásico del framework Fork-Join (ver Anexo III de la guía):
    //   left.fork()      -> envía la mitad izquierda a otro hilo (asíncrono)
    //   right.compute()  -> el hilo principal trabaja la mitad derecha
    //   left.join()      -> sincroniza: espera a que el hilo asíncrono termine
    //
    // ¿Por qué 'right.compute()' y no 'right.fork()'?
    // Si forkeáramos ambas mitades, el hilo principal quedaría OCIOSO
    // esperando, desperdiciando un núcleo. Haciéndolo trabajar en la mitad
    // derecha aprovechamos los dos procesadores al 100%.
    //
    /**
     * Para hacer: Modificar este método para calcular la misma suma de recíprocos como le realizada en
     * seqArraySum, pero utilizando dos tareas ejecutándose en paralelo dentro del framework ForkJoin de Java
     * Se puede asumir que el largo del arreglo de entrada
     * es igualmente divisible por 2.
     *
     * @param input Arreglo de entrada
     * @return La suma de los recíprocos del arreglo de entrada
     */
    protected static double parArraySum(final double[] input) {
        assert input.length % 2 == 0;

        // 1) División matemática exacta del arreglo en dos chunks iguales.
        final int mid = input.length / 2;
        final ReciprocalArraySumTask left =
                new ReciprocalArraySumTask(0, mid, input);
        final ReciprocalArraySumTask right =
                new ReciprocalArraySumTask(mid, input.length, input);

        // 2) Patrón Fork-Join de la guía:
        left.fork();      // mitad izquierda -> hilo asíncrono
        right.compute();  // mitad derecha   -> hilo principal trabaja
        left.join();      // sincronización: esperamos al asíncrono

        // 3) Reducción final: combinamos los dos subtotales.
        return left.getValue() + right.getValue();
    }

    // =========================================================================
    // SECCIÓN C - PARALELISMO ESCALABLE A N TAREAS
    // =========================================================================
    // Generalización del caso de 2 tareas a 'numTasks' tareas. Aquí entran
    // en juego TODOS los conceptos del curso:
    //
    //   - getChunkStartInclusive / getChunkEndExclusive  -> calculan las
    //     "fronteras" de cada chunk para que ningún hilo invada la memoria
    //     de otro (prevención de RACE CONDITIONS por construcción).
    //
    //   - getPool(numTasks)  -> Sección D, reutilización del ForkJoinPool.
    //
    //   - pool.execute(t_i)  -> despacha cada tarea al ForkJoinPool. El
    //     framework usa internamente WORK-STEALING: si un núcleo termina
    //     antes, "roba" tareas pendientes a un núcleo ocupado.
    //
    //   - tasks[i].join()    -> sincroniza el hilo principal con CADA una
    //     de las tareas hijas antes de hacer la reducción final.
    //
    /**
     * Para hacer: extender el trabajo hecho para implementar parArraySum que permita utilizar un número establecido
     * de tareas para calcular la suma del arreglo recíproco.
     * getChunkStartInclusive y getChunkEndExclusive pueden ser útiles para cacular
     * el rango de elementos índice que pertenecen a cada sección/trozo (chunk).
     *
     * @param input Arreglo de entrada
     * @param numTasks El número de tareas para crear
     * @return La suma de los recíprocos del arreglo de entrada
     */
    protected static double parManyTaskArraySum(final double[] input,
            final int numTasks) {
        // 1) Construcción de las N tareas, una por cada chunk del arreglo.
        //    Las "fronteras" se calculan con los helpers ya provistos.
        final ReciprocalArraySumTask[] tasks = new ReciprocalArraySumTask[numTasks];

        for (int i = 0; i < numTasks; i++) {
            final int start = getChunkStartInclusive(i, numTasks, input.length);
            final int end   = getChunkEndExclusive(i, numTasks, input.length);
            tasks[i] = new ReciprocalArraySumTask(start, end, input);
        }

        // 2) Recuperamos (o creamos por primera vez) el ForkJoinPool cacheado.
        //    Sección D: este reuso es lo que dispara el Speedup real.
        final ForkJoinPool pool = getPool(numTasks);

        // 3) Despachamos cada tarea al pool. El framework usa Work-Stealing
        //    para balancear la carga entre los hilos disponibles.
        for (int i = 0; i < numTasks; i++) {
            pool.execute(tasks[i]);
        }

        // 4) Sincronización: el hilo principal espera a que TODAS las tareas
        //    hayan completado antes de proceder con la reducción.
        for (int i = 0; i < numTasks; i++) {
            tasks[i].join();
        }

        // 5) Reducción final: sumamos los subtotales de cada chunk.
        double sum = 0;
        for (int i = 0; i < numTasks; i++) {
            sum += tasks[i].getValue();
        }

        return sum;
    }
}
