package bss;

import java.util.ArrayList;
import java.util.function.DoubleUnaryOperator;

import javax.swing.JFileChooser;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.HistogramNd;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class BuildCytoFluo < T extends RealType< T > & NativeType< T > > implements PlugIn
{

	final CFGParameters cfgParams = new CFGParameters();
	
	int nChannels;
	
	@Override
	public void run( String arg )
	{
		
		String sFilenameINI = openFilenameDialog();
		if(sFilenameINI == null)
			return;
		
		final AbstractSpimData< ? > spimData = CFGParameters.getDataFromFilename(sFilenameINI, cfgParams);
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
				Cast.unchecked(  imgLoader.getSetupImgLoader(cfgParams.nChannel1).getImage(0));
		final RandomAccessibleInterval<T> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(cfgParams.nChannel2).getImage(0));
		
		IJ.log("BigScopeScatter v." + GlobalParameters.sVersion + ": Building cytofluorogram.");
		cfgParams.printParams();
		IJ.log("Calculating, please wait...");
		final ImagePlus imp = getHistogram(channel1, channel2, cfgParams  );
		
		imp.setTitle( "cytofluorogram_" + cfgParams.sDataFilename);
		cfgParams.saveToImagePlus( imp );
		imp.show();
		CFGParameters.applyHistParameters(imp, cfgParams);
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
		IJ.log("done");
	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus getHistogram(
			final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, 
			final CFGParameters histParams)
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
		gdHist.addCheckbox( "Invert Y-axis ", GlobalParameters.bInvertY );
		gdHist.addChoice( "Axis mapping ", sMapping, sMapping[GlobalParameters.nMapFunction] );
		
		gdHist.addNumericField( "Bins number X ", GlobalParameters.nBinsX, 0);
		gdHist.addNumericField( "Bins number Y ", GlobalParameters.nBinsY, 0);

		gdHist.addMessage( "Intensity ranges" );
		gdHist.addMessage( "Intensity range X ch " );
		gdHist.addNumericField("MinX ", GlobalParameters.dMinX);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxX ", GlobalParameters.dMaxX);
		gdHist.addMessage( "Intensity range Y ch" );
		gdHist.addNumericField("MinY ", GlobalParameters.dMinY);
		gdHist.addToSameRow();
		gdHist.addNumericField("MaxY ", GlobalParameters.dMaxY);
		gdHist.showDialog();
		
		if ( gdHist.wasCanceled() )
			return false;
		cfgParams.nChannel1 = gdHist.getNextChoiceIndex();
		cfgParams.nChannel2 = gdHist.getNextChoiceIndex();
		if(cfgParams.nChannel1  == cfgParams.nChannel2)
		{
			IJ.log("Warning! Channel X axis is equal to Channel Y!");
		}
		
		cfgParams.bFlipY = gdHist.getNextBoolean();
		GlobalParameters.bInvertY = cfgParams.bFlipY;
		Prefs.set("BSS.bInvertY", cfgParams.bFlipY);
		
		cfgParams.nMapFunction = gdHist.getNextChoiceIndex();		
		GlobalParameters.nMapFunction = cfgParams.nMapFunction;
		Prefs.set("BSS.nMapFunction", cfgParams.nMapFunction);
		
		cfgParams.nBinsX = (int)gdHist.getNextNumber();
		GlobalParameters.nBinsX = cfgParams.nBinsX;
		Prefs.set("BSS.nBinsX", cfgParams.nBinsX);
		
		cfgParams.nBinsY = (int)gdHist.getNextNumber();
		GlobalParameters.nBinsY = cfgParams.nBinsY;
		Prefs.set("BSS.nBinsY", cfgParams.nBinsY);
		
		cfgParams.minmax1[0] = gdHist.getNextNumber();
		GlobalParameters.dMinX = cfgParams.minmax1[0];
		Prefs.set("BSS.dMinX", GlobalParameters.dMinX);
		
		cfgParams.minmax1[1] = gdHist.getNextNumber();
		GlobalParameters.dMaxX = cfgParams.minmax1[1];
		Prefs.set("BSS.dMaxX", GlobalParameters.dMaxX);
		
		cfgParams.minmax2[0] = gdHist.getNextNumber();
		GlobalParameters.dMinY = cfgParams.minmax2[0];
		Prefs.set("BSS.dMinY", GlobalParameters.dMinY);
		
		cfgParams.minmax2[1] = gdHist.getNextNumber();
		GlobalParameters.dMaxY = cfgParams.minmax2[1];
		Prefs.set("BSS.dMaxY", GlobalParameters.dMaxY);
		
		return true;
	}
	
	public static String openFilenameDialog()
	{
		
		JFileChooser chooser = new JFileChooser(GlobalParameters.lastDir );
		chooser.setDialogTitle( "Open BioFormats or XML/HDF5 files" );

		int returnVal = chooser.showOpenDialog(null);

		if(returnVal == JFileChooser.APPROVE_OPTION) 
		{
			String sFolder = chooser.getSelectedFile().getParent();
			GlobalParameters.lastDir = sFolder;
			Prefs.set( "BSS.lastDir", sFolder );
			return chooser.getSelectedFile().getPath();
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception 
	{
		new ImageJ();
		BuildCytoFluo<?> test = new BuildCytoFluo<>();
		test.run( null);
	}
}
