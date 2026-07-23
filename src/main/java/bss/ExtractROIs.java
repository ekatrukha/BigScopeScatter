package bss;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class ExtractROIs < T extends RealType< T > & NativeType< T > > implements PlugIn
{
	/**cytofluorogram parameters **/
	final CFGParameters cfgParams = new CFGParameters();
	
	/** ROI manager instance **/
	RoiManager rm;
	
	ArrayList<Roi> rois = new ArrayList<>();
	
	@Override
	public void run( String arg )
	{
		ImagePlus imp = IJ.getImage();		
		if (imp == null)
		{
		    IJ.noImage();
		    return;
		}	
		rm = RoiManager.getInstance2();
		if (rm == null) 
		{
			IJ.error( "BigScopeScatter error", "No ROIs in ROI manager." );
			return;
		}
		else if(rm.getCount() < 1)
		{
			IJ.error("BigScopeScatter error", "ROI manager must contain at least one ROI." );
			return ;			
		}
		//verify that ROIs are ok
		if(!verifyROIs())
			return;
		
		IJ.log( "BigScopeScatter v." + GlobalParameters.sVersion + " reading parameters from current image." );
		if(!cfgParams.loadFromImagePlus( imp ))
		{
			return;
		}
		IJ.log( "Parameters loaded, see values below." );
		cfgParams.printParams();
		CFGParameters.applyHistParameters(imp, cfgParams);
		
		//try to read the data
		AbstractSpimData< ? > spimData = CFGParameters.getDataFromFilename(cfgParams.getFullDataPathFilename(), cfgParams);
		//no data, let's ask user for something else in case it was moved
		if(spimData == null)
		{
			if(!IJ.showMessageWithCancel( "Data file is missing", "Cannot find associated data file " + cfgParams.sDataFilename +
				"\n at " + cfgParams.sDataPath + "\n It was moved? Do you want to open it in from the new location?"	 ))
			{
				return;
			}
			String sFilenameINI = BuildCytoFluo.openFilenameDialog();
			if(sFilenameINI == null)
				return;	
			spimData = CFGParameters.getDataFromFilename(sFilenameINI, cfgParams);
			if(spimData == null)
			{
				IJ.error("Error loading", "Error loading " + cfgParams.getFullDataPathFilename() + ". \nNot an image file?");
				
				return;
			}
			IJ.log( "Loaded the data from " + cfgParams.getFullDataPathFilename());
		}
		//ok, assume spimData not a null now
		int nChannels = spimData.getSequenceDescription().getViewSetupsOrdered().size();
		
		if(nChannels < 2)
		{
			IJ.log( "You need image with at least 2 channels as input");
			return;
		}
		if(cfgParams.nChannel1 > nChannels || cfgParams.nChannel2 > nChannels)
		{
			IJ.log( "Loaded image does not have channels from stored cytofluorogram!");
			return;
			
		}
		
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		
		final RandomAccessibleInterval<T> channel1 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(cfgParams.nChannel1).getImage(0));
		final RandomAccessibleInterval<T> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(cfgParams.nChannel2).getImage(0));
		double [] voxDims = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().dimensionsAsDoubleArray();
		String sUnit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		final Calibration cal = new Calibration ();
		cal.pixelWidth  = voxDims[0];
		cal.pixelHeight = voxDims[1];
		cal.pixelDepth  = voxDims[2];
		cal.setUnit( sUnit );
		for (final Roi roi:rois)
		{
			ImagePlus extractedImp = getFilteredPairFromROIMap(roi, channel1, channel2, cfgParams);
			CompositeImage cExtracted = new CompositeImage(extractedImp);
			cExtracted.setMode( IJ.COMPOSITE );
			cExtracted.setCalibration( cal );
			cExtracted.setTitle( roi.getName() );			
			cExtracted.setC( 2 );
			IJ.run(imp, "Enhance Contrast", "saturated=0.35");
			cExtracted.setC( 1 );
			IJ.run(imp, "Enhance Contrast", "saturated=0.35");
			cExtracted.show();
		}
		//impOut.setCalibration( cal );

	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus 
	getFilteredPairFromROIMap(final Roi roi, final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, final CFGParameters cfgP)
	{
		final DoubleUnaryOperator f = cfgP.getMapFunction();
		double min1 = f.applyAsDouble( cfgP.minmax1[0] );
		double max1 = f.applyAsDouble( cfgP.minmax1[1] );
		double min2 = f.applyAsDouble( cfgP.minmax2[0] );
		double max2 = f.applyAsDouble( cfgP.minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, cfgP.nBinsX, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, cfgP.nBinsY, false);

		long[] dimsSingle = channel1.dimensionsAsLongArray();
		long [] dims = new long [dimsSingle.length + 1];
		for(int d = 0; d < 2; d ++)
		{
			dims[d] = dimsSingle[d];
		}
		//add 2 channel dimensions, always #2 (after XY (01))
		dims[2] = 2;
		dims[3] = dimsSingle[2];
		
		int[] blockSize = { 32 };
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
					chunk.forEachPixel( (c1, c2, co1, co2) -> 
					{
						long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
						long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));
						if(x >= 0 && x < cfgP.nBinsX && y >= 0 && y < cfgP.nBinsY)
						{
							if(cfgP.bFlipY)
							{
								y = cfgP.nBinsY - y - 1;
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
				}
				);
		
		final ImagePlus impOut = ImageJFunctions.wrap( out, "")  ;
		
		//redo dimensions to ImageJ
		impOut.setDimensions( 2, (int)dimsSingle[2], 1 );
		return impOut;
	}
	
	boolean verifyROIs()
	{
		final Roi[] allRois = rm.getRoisAsArray();
		for (final Roi roi:allRois)
		{
			final int nType = roi.getType();
			if(nType == Roi.POLYGON || nType == Roi.FREEROI || nType == Roi.OVAL 
					|| nType == Roi.RECTANGLE)
			{
				rois.add( roi );
			}
		}
		if(rois.size() == 0)
			return false;
		IJ.log( "Found " + rois.size() + " area ROIs.");
		return true;
	}
	
	public static void main(String[] args) throws Exception 
	{
		new ImageJ();
		//ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/cytofluorogram_1-3.tif");
		ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/cytofluorogram_1-3_inverted.tif");
		
		image.show();
		RoiManager rMan = RoiManager.getInstance2();
		if (rMan == null) {
			rMan = new RoiManager(); // creates a new one if needed
		}
		//rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/RoiSet.zip" );
		rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/RoiSet_inverted.zip" );
		
		ExtractROIs<?> test = new ExtractROIs<>();
		test.run( null);
	}
}
