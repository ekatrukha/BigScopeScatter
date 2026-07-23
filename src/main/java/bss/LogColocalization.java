package bss;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.math3.analysis.function.Asinh;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.HistogramNd;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import bss.io.SpimDataLoader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;


public class LogColocalization implements PlugIn 
{

	@Override
	public void run( String arg )
	{
    }
	
	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads
	 * an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) throws Exception 
	{
		
		new ImageJ();
		AbstractSpimData< ? > spimData = SpimDataLoader.
				loadHDF5( "/home/eugene/Desktop/projects/BrainQuant/registered/registered_crop.xml" );
				//loadHDF5( "/home/eugene/Desktop/projects/BrainQuant/registered/registered002.xml" );

//				loadBioFormats("/home/eugene/Desktop/people/Jasper/20250922_cytogram/1-3.tif" );
		
//		AbstractSpimData< ? > spimData = SpimDataLoader.
//				loadBioFormats("/home/eugene/Desktop/people/Jasper/20250922_cytogram/1-3.tif" );
		
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		
		
		RandomAccessibleInterval<UnsignedShortType> channel1 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(0).getImage(0));
		RandomAccessibleInterval<UnsignedShortType> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(1).getImage(0));

		double [] minmax1 = new double [] {20, 65535};
		double [] minmax2 = new double [] {20, 65535};
		int nBins = 512;

//		final ImagePlus imp = getHistogramDoubleF(channel1, channel2, nBins, 
//
//				x -> Math.log( (x + Math.sqrt( x*x + 765*765 ))*0.5), 
//				x -> Math.log( (x + Math.sqrt( x*x + 212*212 ))*0.5), 
//				minmax1, minmax2);
		
		
//		final ImagePlus imp = getHistogram(channel1, channel2, nBins, 
//				x -> Math.log(x), 
//				minmax1, minmax2);
//
//		
//		imp.show();
		
//		ImagePlus mapImg = IJ.openImage( "/home/eugene/Desktop/projects/BrainQuant/analysis/cytofluorogram/002/map_002_RegionC.tif" );
//		double [] voxDims = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().dimensionsAsDoubleArray();
//		getFilteredPairFromROIMap(mapImg, channel1, channel2, nBins, x -> Math.log(x), 
//				minmax1, minmax2, voxDims);

		ImagePlus mapImg = IJ.openImage( "/home/eugene/Desktop/projects/BrainQuant/analysis/cytofluorogram/001/map_RegionA_extra.tif" );
		double [] voxDims = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().dimensionsAsDoubleArray();
		getRatioImageFromROIMap(mapImg, channel1, channel2, nBins, x -> Math.log(x), 
				minmax1, minmax2, voxDims);
		
		
	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus getHistogram(
			final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final int nBins, DoubleUnaryOperator f, 
			final double [] minmax1, 
			final double [] minmax2)
	{
		double min1 = f.applyAsDouble( minmax1[0] );
		double max1 = f.applyAsDouble( minmax1[1] );
		double min2 = f.applyAsDouble( minmax2[0] );
		double max2 = f.applyAsDouble( minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, nBins, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, nBins, false);
		
		final ArrayList<BinMapper1d<FloatType>> mappers = new ArrayList<>();
		mappers.add (mapper1);
		mappers.add (mapper2);
		HistogramNd<FloatType> histogram = new HistogramNd<>(mappers);
		ArrayList<Iterable<FloatType>> list = new ArrayList<>();
		RandomAccessibleInterval< FloatType > real1 = 
				Converters.convert( channel1, (i,o) -> 
				{o.set( (float)f.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		RandomAccessibleInterval< FloatType > real2 = 
				Converters.convert( channel2, (i,o) -> 
				{o.set( (float)f.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		list.add( real1 );
		list.add( real2 );
		histogram.countData( list );

		RandomAccessibleInterval< FloatType > histFloat = 
				Converters.convert( histogram, (i,o) -> 
				o.set(i.getIntegerLong()), new FloatType() );
		final ImagePlus imp = ImageJFunctions.show(histFloat);
    	final double binWx = (max1 - min1) / nBins;
    	final double binWy = (max2 - min2) / nBins;	
		final ImageCanvas canvas = imp.getCanvas();
		final ImageProcessor ip = imp.getProcessor();
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int x = canvas.offScreenX(e.getX());
		        int y = canvas.offScreenY(e.getY());

		        double myX = Math.exp(min1 + (x + 0.5) * binWx);//,10);
		        double myY = Math.exp(min2 + (y + 0.5) * binWy);//,10);
		        float fCount = ip.getf( x, y );
		        IJ.showStatus(
		            String.format("Count %.0f, Int1=%.2f (%d), Int2=%.2f (%d)", fCount, myX, x, myY, y)
		        );
		    }
		});
		
		//ImagePlus imp = ImageJFunctions.show( histogram );
		return imp;
	}
	public static < T extends RealType< T > & NativeType< T > > void 
	getRatioImageFromROIMap(final ImagePlus mapImp, final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final int nBins, DoubleUnaryOperator f, 
			final double [] minmax1, 
			final double [] minmax2, final double [] voxDims)
	{
		double min1 = f.applyAsDouble( minmax1[0] );
		double max1 = f.applyAsDouble( minmax1[1] );
		double min2 = f.applyAsDouble( minmax2[0] );
		double max2 = f.applyAsDouble( minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, nBins, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, nBins, false);

		final ImageProcessor mapIP = mapImp.getProcessor();
		
		int[] blockSize = { 32, 32, 32 };
		DiskCachedCellImgOptions options = DiskCachedCellImgOptions.options()
			    .cellDimensions(blockSize);
		DiskCachedCellImgFactory<FloatType> factory = 
			    new DiskCachedCellImgFactory<>(new FloatType(), options);
		
		long[] dims = channel1.dimensionsAsLongArray();
		
		DiskCachedCellImg< FloatType, ? > out = factory.create(dims);
		
		AtomicLong globalPixelCount = new AtomicLong(0);

		final long totalPixels = dims[0] * dims[1] * dims[2];
		
		LoopBuilder.setImages( channel1, channel2, 
				 out).
				multiThreaded().forEachChunk( chunk->
				{
					long[] localCount = new long[1];
					chunk.forEachPixel( (c1,c2,co)-> 
					{
						long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
						long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));
						if(x >= 0 && x <= nBins && y >= 0 && y <= nBins)
						{
							if(mapIP.get( (int)x, (int) y ) > 0)
							{
								float ch1 = (c1.getRealFloat() - 120.0f);
								float ch2 = (c2.getRealFloat() - 120.0f);
								co.set(  ch1/ch2 );
							}
						}
						localCount[0]++;
					});
					long overallProcessed = globalPixelCount.addAndGet(localCount[0]);
					double progress = (double) overallProcessed / totalPixels;//*100;
					IJ.showProgress( progress );
					return null;
				}
				);
		
		final ImagePlus impOut = ImageJFunctions.show( out );
		impOut.setDimensions( 1, (int)dims[2], 1 );

		final Calibration cal = new Calibration ();
		cal.pixelWidth = voxDims[0];
		cal.pixelHeight = voxDims[1];
		cal.pixelDepth = voxDims[2];
		impOut.setCalibration( cal );

	}
	public static < T extends RealType< T > & NativeType< T > > void 
	getFilteredPairFromROIMap(final ImagePlus mapImp, final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final int nBins, DoubleUnaryOperator f, 
			final double [] minmax1, 
			final double [] minmax2, final double [] voxDims)
	{
		double min1 = f.applyAsDouble( minmax1[0] );
		double max1 = f.applyAsDouble( minmax1[1] );
		double min2 = f.applyAsDouble( minmax2[0] );
		double max2 = f.applyAsDouble( minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, nBins, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, nBins, false);

		final ImageProcessor mapIP = mapImp.getProcessor();
		long[] dimsSingle = channel1.dimensionsAsLongArray();
		long [] dims = new long [dimsSingle.length + 1];
		for(int d = 0; d < 2; d ++)
		{
			dims[d] = dimsSingle[d];
		}
		dims[2] = 2;
		dims[3] = dimsSingle[2];
		
		int[] blockSize = { 32, 32, 32 };
		DiskCachedCellImgOptions options = DiskCachedCellImgOptions.options()
			    .cellDimensions(blockSize);
		DiskCachedCellImgFactory<T> factory = 
			    new DiskCachedCellImgFactory<>(channel1.getType(), options);
		DiskCachedCellImg< T, ? > out = factory.create(dims);
		
		AtomicLong globalPixelCount = new AtomicLong(0);
		
		final long totalPixels = dimsSingle[0] * dimsSingle[1] * dimsSingle[2];
		
		LoopBuilder.setImages( channel1, channel2, 
				Views.hyperSlice( out, 2, 0 ),Views.hyperSlice( out, 2, 1 )).
				multiThreaded().forEachChunk( chunk->
				{
					long[] localCount = new long[1];
					chunk.forEachPixel( (c1,c2,co1,co2)-> 
					{
						long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
						long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));
						if(x >= 0 && x <= nBins && y >= 0 && y <= nBins)
						{
							if(mapIP.get( (int)x, (int) y )>0)
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
				}
				);
		
		final ImagePlus impOut = ImageJFunctions.show( out );
		impOut.setDimensions( 2, (int)dimsSingle[2], 1 );

		final Calibration cal = new Calibration ();
		cal.pixelWidth = voxDims[0];
		cal.pixelHeight = voxDims[1];
		cal.pixelDepth = voxDims[2];
		impOut.setCalibration( cal );

	}

	public static < T extends RealType< T > & NativeType< T > > ImagePlus getHistogramDoubleF(
			final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final int nBins, DoubleUnaryOperator f1,
			DoubleUnaryOperator f2,
			final double [] minmax1, 
			final double [] minmax2)
	{
		double min1 = f1.applyAsDouble( minmax1[0] );
		double max1 = f1.applyAsDouble( minmax1[1] );
		double min2 = f2.applyAsDouble( minmax2[0] );
		double max2 = f2.applyAsDouble( minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, nBins, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, nBins, false);
		
		final ArrayList<BinMapper1d<FloatType>> mappers = new ArrayList<>();
		mappers.add (mapper1);
		mappers.add (mapper2);
		HistogramNd<FloatType> histogram = new HistogramNd<>(mappers);
		ArrayList<Iterable<FloatType>> list = new ArrayList<>();
		RandomAccessibleInterval< FloatType > real1 = 
				Converters.convert( channel1, (i,o) -> 
				{o.set( (float)f1.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		RandomAccessibleInterval< FloatType > real2 = 
				Converters.convert( channel2, (i,o) -> 
				{o.set( (float)f2.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		list.add( real1 );
		list.add( real2 );
		histogram.countData( list );

		RandomAccessibleInterval< FloatType > histFloat = 
				Converters.convert( histogram, (i,o) -> 
				o.set(i.getIntegerLong()), new FloatType() );
		final ImagePlus imp = ImageJFunctions.show(histFloat);
    	final double binWx = (max1 - min1) / nBins;
    	final double binWy = (max2 - min2) / nBins;	
		final ImageCanvas canvas = imp.getCanvas();
		final ImageProcessor ip = imp.getProcessor();
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int x = canvas.offScreenX(e.getX());
		        int y = canvas.offScreenY(e.getY());

		        double myX = Math.exp(min1 + (x + 0.5) * binWx);//,10);
		        double myY = Math.exp(min2 + (y + 0.5) * binWy);//,10);
		        float fCount = ip.getf( x, y );
		        IJ.showStatus(
		            String.format("Count %.0f, Int1=%.2f (%d), Int2=%.2f (%d)", fCount, myX, x, myY, y)
		        );
		    }
		});
		
		//ImagePlus imp = ImageJFunctions.show( histogram );
		return imp;
	}
}
