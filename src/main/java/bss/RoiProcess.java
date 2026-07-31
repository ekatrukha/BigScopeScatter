package bss;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import ij.IJ;
import ij.gui.Roi;


public class RoiProcess
{
	public static < T extends RealType< T > & NativeType< T > > DiskCachedCellImg< T, ? > 
	getFilteredPairFromROIMap(final Roi roi, final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final FGParameters fgP)
	{
		final DoubleUnaryOperator f = fgP.getMapFunction();
		double min1 = f.applyAsDouble( fgP.minmax1[0] );
		double max1 = f.applyAsDouble( fgP.minmax1[1] );
		double min2 = f.applyAsDouble( fgP.minmax2[0] );
		double max2 = f.applyAsDouble( fgP.minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, fgP.nBinsX, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, fgP.nBinsY, false);

		int numD = channel1.numDimensions();
		long[] dimsSingle = channel1.dimensionsAsLongArray();
		long [] dims = new long [numD + 1];
		for(int d = 0; d < 2; d ++)
		{
			dims[d] = dimsSingle[d];
		}
		//add 2 channel dimensions, always #2 (after XY (01))
		dims[2] = 2;
		for( int d = 2; d < numD; d++)
		{
			dims[d + 1] = dimsSingle[d];
		}
		
		int[] blockSize = { 32 };
		DiskCachedCellImgOptions options = DiskCachedCellImgOptions.options()
			    .cellDimensions(blockSize);
		DiskCachedCellImgFactory<T> factory = 
			    new DiskCachedCellImgFactory<>(channel1.getType(), options);
		final DiskCachedCellImg< T, ? > out = factory.create(dims);
		
		AtomicLong globalPixelCount = new AtomicLong(0);
		
		final long totalPixels = dimsSingle[0] * dimsSingle[1] * dimsSingle[2];
		//half for now
		int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

		//keep the order of channels		
		int nOut1 = 0;
		int nOut2 = 1;
		if(fgP.nChannel1 > fgP.nChannel2)
		{
			nOut1 = 1;
			nOut2 = 0;
		}
		// Create a TaskExecutor with the target thread count
		try (TaskExecutor taskExecutor = TaskExecutors.fixedThreadPool(numThreads)) 
		{
			LoopBuilder.setImages( channel1, channel2, 
					Views.hyperSlice( out, 2, nOut1 ), Views.hyperSlice( out, 2, nOut2 )).
			multiThreaded(taskExecutor).forEachChunk( chunk ->
			{
				long[] localCount = new long[1];
				chunk.forEachPixel( (c1, c2, co1, co2) -> 
				{
					long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
					long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));
					if(x >= 0 && x < fgP.nBinsX && y >= 0 && y < fgP.nBinsY)
					{
						if(fgP.bFlipY)
						{
							y = fgP.nBinsY - y - 1;
						}
						if(roi.contains( (int)x, (int)y ))
						{
							co1.set( c1 );
							co2.set( c2 );
						}
					}
					localCount[0]++;
				});
				long overallProcessed = globalPixelCount.addAndGet(localCount[0]);
				double progress = (double) overallProcessed / totalPixels;//*100;
				IJ.showProgress( progress );
				return null;
			});
		} catch (RuntimeException e) {
		    // 1. CATCH: Executes if LoopBuilder or task execution throws an exception
		    System.err.println("BigScopeScatter execution failed: " + e.getMessage());
		    e.printStackTrace();

		} catch (Exception e) {
		    // Generic catch for any other checked exceptions (if applicable)
		    System.err.println("An unexpected error occurred: " + e.getMessage());
		} 
		
		return out;
	}
	
	public static < T extends RealType< T > & NativeType< T > > 
			DiskCachedCellImg< FloatType, ? > getRatioImageFromROIMap(
					final Roi roi, final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final FGParameters fgP, final float fBG1, final float fBG2 )
	{
		final DoubleUnaryOperator f = fgP.getMapFunction();
		double min1 = f.applyAsDouble( fgP.minmax1[0] );
		double max1 = f.applyAsDouble( fgP.minmax1[1] );
		double min2 = f.applyAsDouble( fgP.minmax2[0] );
		double max2 = f.applyAsDouble( fgP.minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, fgP.nBinsX, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, fgP.nBinsY, false);
		
		int[] blockSize = { 32 };
		
		DiskCachedCellImgOptions options = DiskCachedCellImgOptions.options()
			    .cellDimensions(blockSize);
		DiskCachedCellImgFactory<FloatType> factory = 
			    new DiskCachedCellImgFactory<>(new FloatType(), options);
		
		long[] dims = channel1.dimensionsAsLongArray();
		
		final DiskCachedCellImg< FloatType, ? > out = factory.create(dims);
		
		AtomicLong globalPixelCount = new AtomicLong(0);

		final long totalPixels = dims[0] * dims[1] * dims[2];
		//half for now
		int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
		
		// Create a TaskExecutor with the target thread count
		try (TaskExecutor taskExecutor = TaskExecutors.fixedThreadPool(numThreads)) 
		{
			LoopBuilder.setImages( channel1, channel2, out).
			multiThreaded(taskExecutor).forEachChunk( chunk ->
			{
				long[] localCount = new long[1];
				chunk.forEachPixel( (c1, c2, co) -> 
				{
					long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
					long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));

					if(x >= 0 && x < fgP.nBinsX && y >= 0 && y < fgP.nBinsY)
					{
						if(fgP.bFlipY)
						{
							y = fgP.nBinsY - y - 1;
						}
						if(roi.contains( (int)x, (int)y ))
						{
							float ch1 = (c1.getRealFloat() - fBG1);
							float ch2 = (c2.getRealFloat() - fBG2);
							co.set(  ch1/ch2 );
						}
					}
					localCount[0]++;
				});
				long overallProcessed = globalPixelCount.addAndGet(localCount[0]);
				double progress = (double) overallProcessed / totalPixels;//*100;
				IJ.showProgress( progress );
				return null;
			});
		} catch (RuntimeException e) {
			// 1. CATCH: Executes if LoopBuilder or task execution throws an exception
			System.err.println("BigScopeScatter execution failed: " + e.getMessage());
			e.printStackTrace();

		} catch (Exception e) {
			// Generic catch for any other checked exceptions (if applicable)
			System.err.println("An unexpected error occurred: " + e.getMessage());
		} 
		return out;
	}
}
