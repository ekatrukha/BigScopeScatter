package bss;

@FunctionalInterface
public interface HistProgressListener
{
    /**
     * @param completed Number of completed units (e.g. chunks)
     * @param total Total number of units
     */
    void onProgress( long completed, long total );
}
