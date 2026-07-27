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
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import bss.io.GetFolderDialog;
import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class ExtractROIs < T extends RealType< T > & NativeType< T > > implements PlugIn
{
	/**cytofluorogram parameters **/
	final FGParameters fgParams = new FGParameters();	
	
	/** ROI manager instance **/
	RoiManager rm;
	
	ArrayList<Roi> rois = new ArrayList<>();
	
	String nOutputPath = "";
	
	@Override
	public void run( String arg )
	{
		ImagePlus impFG = IJ.getImage();		
		if (impFG == null)
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
		
		IJ.log( "BigScopeScatter v." + BSSsettings.sVersion + " reading parameters from current image." );
		if(!fgParams.loadFromImagePlus( impFG ))
		{
			return;
		}
		IJ.log( "Parameters loaded, see values below." );
		fgParams.printParams();
		FGParameters.applyHistParameters(impFG, fgParams);
		
		//try to read the data
		AbstractSpimData< ? > spimData = FGParameters.getDataFromFilename(fgParams.getFullDataPathFilename(), fgParams);
		//no data, let's ask user for something else in case it was moved
		if(spimData == null)
		{
			if(!IJ.showMessageWithCancel( "Data file is missing", "Cannot find associated data file " + fgParams.sDataFilename +
				"\n at " + fgParams.sDataPath + "\n It was moved? Do you want to open it in from the new location?"	 ))
			{
				return;
			}
			String sFilenameINI = BuildFluorogram.openFilenameDialog();
			if(sFilenameINI == null)
				return;	
			spimData = FGParameters.getDataFromFilename(sFilenameINI, fgParams);
			if(spimData == null)
			{
				IJ.error("Error loading", "Error loading " + fgParams.getFullDataPathFilename() + ". \nNot an image file?");
				
				return;
			}
			IJ.log( "Loaded the data from " + fgParams.getFullDataPathFilename());
		}
		
		//ask for the output
		if(!outputSelectionDialog())
			return;
		
		//ok, assume spimData not a null now
		int nChannels = spimData.getSequenceDescription().getViewSetupsOrdered().size();
		
		if(nChannels < 2)
		{
			IJ.log( "You need image with at least 2 channels as input");
			return;
		}
		if(fgParams.nChannel1 > nChannels || fgParams.nChannel2 > nChannels)
		{
			IJ.log( "Loaded image does not have channels from stored cytofluorogram!");
			return;
			
		}
		
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();

		//keep the order of channels
		final RandomAccessibleInterval<T> channel1 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(fgParams.nChannel1).getImage(0));
		final RandomAccessibleInterval<T> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(fgParams.nChannel2).getImage(0));
		double [] voxDims = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().dimensionsAsDoubleArray();
		String sUnit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		final Calibration cal = new Calibration ();
		cal.pixelWidth  = voxDims[0];
		cal.pixelHeight = voxDims[1];
		cal.pixelDepth  = voxDims[2];
		cal.setUnit( sUnit );
		
		for (final Roi roi:rois)
		{
			IJ.showStatus( "Processing ROI " + roi.getName() );
			ImagePlus extractedImp = getFilteredPairFromROIMap(roi, channel1, channel2, fgParams);
			CompositeImage impROI = new CompositeImage(extractedImp);
			impROI.setMode( IJ.COMPOSITE );
			impROI.setCalibration( cal );
			impROI.setTitle( roi.getName() + "_" + fgParams.getChannelsNamesROI());			
			impROI.setC( 2 );
			IJ.run(impROI, "Enhance Contrast", "saturated=0.35");
			impROI.setC( 1 );
			IJ.run(impROI, "Enhance Contrast", "saturated=0.35");
			
			switch (BSSsettings.nOutputMode)
			{
			case BSSsettings.BSS_ImageJ:
				impROI.show();

				break;
			case BSSsettings.BSS_Tiff:
				IJ.saveAs(impROI, "Tiff", nOutputPath + roi.getName() + ".tif");
				break;
			}
			IJ.log( "Processed ROI " + roi.getName() );
		}

		IJ.showStatus( "All ROIs done" );
		
		IJ.log( "All ROIs done" );
	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus 
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
					Views.hyperSlice( out, 2, nOut1 ),Views.hyperSlice( out, 2, nOut2 )).
			multiThreaded(taskExecutor).forEachChunk( chunk->
			{
				long[] localCount = new long[1];
				chunk.forEachPixel( (c1, c2, co1, co2) -> 
				{
					long x = mapper1.map( new FloatType((float)f.applyAsDouble( c1.getRealDouble())));
					long y = mapper2.map( new FloatType((float)f.applyAsDouble( c2.getRealDouble())));
					if(x >= 0 && x < fgP.nBinsX && y >= 0 && y < fgP.nBinsY)
					{
						
						///VERIFY THIS!!!!
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
			}
					);
		} catch (RuntimeException e) {
		    // 1. CATCH: Executes if LoopBuilder or task execution throws an exception
		    System.err.println("BigScopeScatter execution failed: " + e.getMessage());
		    e.printStackTrace();

		} catch (Exception e) {
		    // Generic catch for any other checked exceptions (if applicable)
		    System.err.println("An unexpected error occurred: " + e.getMessage());

		} 
		final ImagePlus impOut = ImageJFunctions.wrap( out, "");

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
	
	boolean outputSelectionDialog()
	{
		final GenericDialog gdOutput = new GenericDialog( "Output parameters" );
		final String [] sOutput = new String[] {"show in Fiji", "Save as TIFFs"};
		gdOutput.addChoice( "Extract to:", sOutput , sOutput [ BSSsettings.nOutputMode ] );
		gdOutput.showDialog();
		
		if ( gdOutput.wasCanceled() )
			return false;
		
		BSSsettings.nOutputMode = gdOutput.getNextChoiceIndex();
		Prefs.set( "BSS.nOutputMode", BSSsettings.nOutputMode );
		
		//ask for the folder
		if(BSSsettings.nOutputMode == BSSsettings.BSS_Tiff)
		{
			nOutputPath = GetFolderDialog.getSelectedFolder("Save TIFFs to folder..", false);
			if (nOutputPath == null)
				return false;
		}
		return true;
	}
	
	public static void main(String[] args) throws Exception 
	{
		new ImageJ();
		//ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/test_data/cytofluorogram_1-3.tif");
		//ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/test_data/scatter_(2_1i)_1-3.tif");
		ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/fliptest/scatter_X(2)gant_Y(1f)BS-gant_3-4.tif");
		
		image.show();
		RoiManager rMan = RoiManager.getInstance2();
		if (rMan == null) {
			rMan = new RoiManager(); // creates a new one if needed
		}
		//rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/test_data/RoiSet.zip" );
		//rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/test_data/RoiSet_inverted.zip" );
		rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/fliptest/turned.roi" );
		ExtractROIs<?> test = new ExtractROIs<>();
		test.run( null);
	}
}
