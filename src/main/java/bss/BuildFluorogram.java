package bss;

import java.util.ArrayList;
import java.util.function.DoubleUnaryOperator;

import javax.swing.JFileChooser;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.StopWatch;
import net.imglib2.view.Views;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class BuildFluorogram < T extends RealType< T > & NativeType< T > > implements PlugIn
{

	final FGParameters fgParams = new FGParameters();
	
	int nChannels;
	
	@Override
	public void run( String arg )
	{
		
		String sFilenameINI = openFilenameDialog();
		if(sFilenameINI == null)
			return;
		
		final AbstractSpimData< ? > spimData = FGParameters.getDataFromFilename(sFilenameINI, fgParams);
		if(spimData == null)
		{
			IJ.log( "Error opening: " + sFilenameINI +"\n Not an image file?");
			return;
		}
		nChannels = spimData.getSequenceDescription().getViewSetupsOrdered().size();
		
		if(nChannels < 2)
		{
			IJ.log( "You need image with at least 2 channels as input");
			return;
		}
		//show parameters dialog
		if(!dialogHistParameters())
			return;
		
		
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		
		final RandomAccessibleInterval<T> channel1 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(fgParams.nChannel1).getImage(0));
		final RandomAccessibleInterval<T> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(fgParams.nChannel2).getImage(0));
		
		IJ.log("BigScopeScatter v." + BSSsettings.sVersion + ": Building cytofluorogram.");
		fgParams.printParams();
		IJ.log("Calculating, please wait...");
		final ImagePlus imp = getFluorogram(channel1, channel2, fgParams  );
		
		imp.setTitle( "scatter_" + fgParams.getChannelsConfiguration() + "_" 
						+ fgParams.getFilenameNoExtension());
		fgParams.saveToImagePlus( imp );
		imp.show();
		FGParameters.applyHistParameters(imp, fgParams);
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
		IJ.log("done");
	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus getFluorogram(
			final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, 
			final FGParameters histParams)
	{	
		final DoubleUnaryOperator f = histParams.getMapFunction();
		final double min1 = f.applyAsDouble( histParams.minmax1[0] );
		final double max1 = f.applyAsDouble( histParams.minmax1[1] );
		final double min2 = f.applyAsDouble( histParams.minmax2[0] );
		final double max2 = f.applyAsDouble( histParams.minmax2[1] );		

		Real1dBinMapper<FloatType> mapper1 = new Real1dBinMapper<>(min1, max1, histParams.nBinsX, false);
		Real1dBinMapper<FloatType> mapper2 = new Real1dBinMapper<>(min2, max2, histParams.nBinsY, false);
		
		final ArrayList<BinMapper1d<FloatType>> mappers = new ArrayList<>();
		mappers.add (mapper1);
		mappers.add (mapper2);
		HistogramNdBSS<FloatType> histogram = new HistogramNdBSS<>(mappers);
		ArrayList<Iterable<FloatType>> list = new ArrayList<>();
		RandomAccessibleInterval< FloatType > real1 = 
				Converters.convert( channel1, (i,o) -> 
				{o.set( (float)f.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		RandomAccessibleInterval< FloatType > real2 = 
				Converters.convert( channel2, (i,o) -> 
				{o.set( (float)f.applyAsDouble( i.getRealDouble()));}, new FloatType() );
		list.add( real1 );
		list.add( real2 );
		histogram.addProgressListener( ( processed, total ) -> 
		    IJ.showProgress( ( double ) processed / total ) );
		StopWatch stopwatch;
		stopwatch = StopWatch.createAndStart();
		histogram.countData( list );
		System.out.println( "single threaded time: " + stopwatch );
		RandomAccessibleInterval< FloatType > histFloat = 
				Converters.convert( histogram, (i,o) -> 
				o.set(i.getIntegerLong()), new FloatType() );
		if(histParams.bFlipY)
			histFloat = Views.invertAxis( histFloat, 1 );
		return ImageJFunctions.wrapFloat( histFloat, "" );
	}

	
	public boolean dialogHistParameters()
	{
		final GenericDialog gdHist = new GenericDialog( "Build cytofluorogram" );
		final String [] sChannels = new String[nChannels];
		
		for(int i = 0; i < nChannels; i++)
		{
			sChannels[i] = "channel " + Integer.toString( i + 1 );
		}
		final String [] sMapping = new String[] {"Linear", "Log"};
		gdHist.addChoice( "For X-axis use ", sChannels, sChannels[ 0 ] );
		gdHist.addChoice( "For Y-axis use ", sChannels, sChannels[ 1 ] );
		gdHist.addCheckbox( "Invert Y-axis ", BSSsettings.bInvertY );
		gdHist.addChoice( "Axis mapping ", sMapping, sMapping[BSSsettings.nMapFunction] );
		
		gdHist.addNumericField( "Bins number X ", BSSsettings.nBinsX, 0);
		gdHist.addNumericField( "Bins number Y ", BSSsettings.nBinsY, 0);

		gdHist.addMessage( "Intensity ranges" );
		gdHist.addMessage( "Intensity range X ch " );
		gdHist.addNumericField("MinX ", BSSsettings.dMinX);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxX ", BSSsettings.dMaxX);
		gdHist.addMessage( "Intensity range Y ch" );
		gdHist.addNumericField("MinY ", BSSsettings.dMinY);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxY ", BSSsettings.dMaxY);
		gdHist.showDialog();
		
		if ( gdHist.wasCanceled() )
			return false;
		fgParams.nChannel1 = gdHist.getNextChoiceIndex();
		fgParams.nChannel2 = gdHist.getNextChoiceIndex();
		if(fgParams.nChannel1  == fgParams.nChannel2)
		{
			IJ.log("Warning! Channel X axis is equal to Channel Y!");
		}
		
		fgParams.bFlipY = gdHist.getNextBoolean();
		BSSsettings.bInvertY = fgParams.bFlipY;
		Prefs.set("BSS.bInvertY", fgParams.bFlipY);
		
		fgParams.nMapFunction = gdHist.getNextChoiceIndex();		
		BSSsettings.nMapFunction = fgParams.nMapFunction;
		Prefs.set("BSS.nMapFunction", fgParams.nMapFunction);
		
		fgParams.nBinsX = (int)gdHist.getNextNumber();
		BSSsettings.nBinsX = fgParams.nBinsX;
		Prefs.set("BSS.nBinsX", fgParams.nBinsX);
		
		fgParams.nBinsY = (int)gdHist.getNextNumber();
		BSSsettings.nBinsY = fgParams.nBinsY;
		Prefs.set("BSS.nBinsY", fgParams.nBinsY);
		
		fgParams.minmax1[0] = gdHist.getNextNumber();
		BSSsettings.dMinX = fgParams.minmax1[0];
		Prefs.set("BSS.dMinX", BSSsettings.dMinX);
		
		fgParams.minmax1[1] = gdHist.getNextNumber();
		BSSsettings.dMaxX = fgParams.minmax1[1];
		Prefs.set("BSS.dMaxX", BSSsettings.dMaxX);
		
		fgParams.minmax2[0] = gdHist.getNextNumber();
		BSSsettings.dMinY = fgParams.minmax2[0];
		Prefs.set("BSS.dMinY", BSSsettings.dMinY);
		
		fgParams.minmax2[1] = gdHist.getNextNumber();
		BSSsettings.dMaxY = fgParams.minmax2[1];
		Prefs.set("BSS.dMaxY", BSSsettings.dMaxY);
		
		return true;
	}
	
	public static String openFilenameDialog()
	{
		
		JFileChooser chooser = new JFileChooser(BSSsettings.lastDir );
		chooser.setDialogTitle( "Open BioFormats or XML/HDF5 files" );

		int returnVal = chooser.showOpenDialog(null);

		if(returnVal == JFileChooser.APPROVE_OPTION) 
		{
			String sFolder = chooser.getSelectedFile().getParent();
			BSSsettings.lastDir = sFolder;
			Prefs.set( "BSS.lastDir", sFolder );
			return chooser.getSelectedFile().getPath();
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception 
	{
		new ImageJ();
		BuildFluorogram<?> test = new BuildFluorogram<>();
		test.run( null);
	}
}
