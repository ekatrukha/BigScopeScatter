package bss;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import mpicbg.spim.data.generic.AbstractSpimData;

public class ExtractROIs < T extends RealType< T > & NativeType< T > > implements PlugIn
{
	/** cytofluorogram parameters **/
	final FGParameters fgParams = new FGParameters();	
	
	/** ROI manager instance **/
	RoiManager rm;
	
	ArrayList<Roi> rois = new ArrayList<>();
	
	String sOutputPath = "";
	
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
		final DialogOutputROI gdOut = new DialogOutputROI(fgParams); 
		if(!gdOut.showDialog())
			return;
		
		sOutputPath = gdOut.sOutputPath;
		
		//ok, assume spimData not a null now
		int nChannels = spimData.getSequenceDescription().getViewSetupsOrdered().size();
		
		if(nChannels < 2)
		{
			IJ.log( "You need image with at least 2 channels as input");
			return;
		}
		if(fgParams.nChannelX > nChannels || fgParams.nChannelY > nChannels)
		{
			IJ.log( "Loaded image does not have channels from stored cytofluorogram!");
			return;		
		}

		//keep the order of channels
		final RandomAccessibleInterval<T> channel1 = Misc.getRAIXYZT( spimData, fgParams.nChannelX );
		final RandomAccessibleInterval<T> channel2 = Misc.getRAIXYZT( spimData, fgParams.nChannelY );
		
		double [] voxDims = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().dimensionsAsDoubleArray();
		String sUnit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		final Calibration cal = new Calibration ();
		cal.pixelWidth  = voxDims[0];
		cal.pixelHeight = voxDims[1];
		cal.pixelDepth  = voxDims[2];
		cal.setUnit( sUnit );
		
		switch(BSSsettings.nOutputType)
		{
		case BSSsettings.BSS_Out_ROI:
			IJ.log( "Extracting 2 channel images for each ROI." );
			break;
		case BSSsettings.BSS_Out_ratioXY:
			IJ.log( "Extracting XY ratio for each ROI." );
			if(BSSsettings.bSubtractBG)
			{
				IJ.log( "Subtracting BG axis X" + Float.toString( BSSsettings.fBGX ) +
						" axis Y" + Float.toString( BSSsettings.fBGY ));
			}
			break;
		case BSSsettings.BSS_Out_ratioYX:
			IJ.log( "Extracting YX ratio for each ROI." );
			if(BSSsettings.bSubtractBG)
			{
				IJ.log( "Subtracting BG axis X" + Float.toString( BSSsettings.fBGX ) +
						" axis Y" + Float.toString( BSSsettings.fBGY ));
			}			
			break;	
		}
		
		float fBGX = 0.0f;
		float fBGY = 0.0f;
		
		if(BSSsettings.bSubtractBG)
		{
			fBGX = BSSsettings.fBGX;
			fBGY = BSSsettings.fBGY;
		}
		boolean bYX = false;
		if (BSSsettings.nOutputType == BSSsettings.BSS_Out_ratioYX )
		{
			bYX = true;
		}
		
		for (final Roi roi:rois)
		{
			IJ.showStatus( "Processing ROI " + roi.getName() );
			
			final DiskCachedCellImg< T, ? > roiRAI;
			switch(BSSsettings.nOutputType)
			{
			case BSSsettings.BSS_Out_ROI:
				roiRAI = RoiProcess.getFilteredPairFromROIMap(roi, channel1, channel2, fgParams);
				break;
			default:
				roiRAI = Cast.unchecked( RoiProcess.getRatioImageFromROIMap( roi, channel1, channel2, 
						fgParams, fBGX, fBGY, bYX )) ;
			}
			//wrap to ImagePlus
			ImagePlus extractedImp = ImageJFunctions.wrap( roiRAI, "");
			ImagePlus impROI = null;
			switch (BSSsettings.nOutputType)
			{
			case BSSsettings.BSS_Out_ROI:
				impROI = new CompositeImage(extractedImp);
				((CompositeImage)impROI).setMode( IJ.COMPOSITE );				
				impROI.setTitle( roi.getName() + "_" + fgParams.getChannelsNamesROI());			
				impROI.setC( 2 );
				IJ.run(impROI, "Enhance Contrast", "saturated=0.35");
				impROI.setC( 1 );
				IJ.run(impROI, "Enhance Contrast", "saturated=0.35");
				break;
			default:
				impROI = extractedImp;
				impROI.setTitle(getRatioTitle(roi, fgParams));
				IJ.run(impROI, "Enhance Contrast", "saturated=0.35");
			}
			
			impROI.setCalibration( cal );
			switch (BSSsettings.nOutputMode)
			{
			case BSSsettings.BSS_ImageJ:
				impROI.show();
				final ImageWindow window = impROI.getWindow();
		    	if (window != null) 
		    	{
		    		window.addWindowListener(new WindowAdapter() {
		    			@Override
		    			public void windowClosed(WindowEvent e) {
		    				//Shuts down the internal IoSync and releases disk resources safely
		    				roiRAI.shutdown();
		    			}
		    		});
		    	}
				break;
			case BSSsettings.BSS_Tiff:
				IJ.saveAs(impROI, "Tiff", sOutputPath + impROI.getTitle() + ".tif");
				roiRAI.shutdown();
				break;
			}
			IJ.log( "Processed ROI " + roi.getName() );
		}

		IJ.showStatus( "All ROIs done" );
		
		IJ.log( "All ROIs done" );
	}
	
	String getRatioTitle(final Roi roi, final FGParameters fgP)
	{
		String sOut = "ratio_";
		String sXName = "";
		String sYName = "";
		if(fgP.bHasAxesNames)
		{
			sXName = "[" + fgP.sChannelX + "]";
			sYName = "[" + fgP.sChannelX + "]";
		}
		if(BSSsettings.nOutputType == BSSsettings.BSS_Out_ratioXY)		
		{
			sOut = sOut +"X" + sXName + "Y" + sYName + "_";
		}
		else
		{
			sOut = sOut +"Y" + sYName + "X" + sXName + "_";
			
		}
		
		return sOut + roi.getName();
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
		//ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/test_data/cytofluorogram_1-3.tif");
		//ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/test_data/scatter_(2_1i)_1-3.tif");
		ImagePlus image = IJ.openImage("/home/eugene/Desktop/projects/BigScopeScatter/test_data/multidim/"
		+"scatter_X(1)gant_Y(2f)BS-gant_3-4.tif");
		//+"scatter_X(1)gant_Y(2f)BS-gant_time_and_z.tif");
		
		image.show();
		RoiManager rMan = RoiManager.getInstance2();
		if (rMan == null) {
			rMan = new RoiManager(); // creates a new one if needed
		}
		//rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/test_data/RoiSet.zip" );
		//rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/test_data/RoiSet_inverted.zip" );
		rMan.open( "/home/eugene/Desktop/projects/BigScopeScatter/test_data/multidim/roi.roi" );
		ExtractROIs<?> test = new ExtractROIs<>();
		test.run( null);
	}
}
