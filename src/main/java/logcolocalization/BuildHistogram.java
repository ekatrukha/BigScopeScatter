package logcolocalization;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
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
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import logcolocalization.io.SpimDataLoader;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;

public class BuildHistogram < T extends RealType< T > & NativeType< T > > implements PlugIn
{

	final HistogramParameters histParams = new HistogramParameters();
	
	int nChannels;
	
	@Override
	public void run( String arg )
	{
		
		String sFilenameINI = openFilenameDialog();
		if(sFilenameINI == null)
			return;
		
		final AbstractSpimData< ? > spimData = getDataFromFilename(sFilenameINI);
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
				Cast.unchecked(  imgLoader.getSetupImgLoader(histParams.nChannel1).getImage(0));
		final RandomAccessibleInterval<T> channel2 = 
				Cast.unchecked(  imgLoader.getSetupImgLoader(histParams.nChannel2).getImage(0));
		final int [] nBins = new int [] {histParams.nBinsX, histParams.nBinsY };
		
		final ImagePlus imp = getHistogram(channel1, channel2, histParams  );
		
		imp.setTitle( "cytofluorogram_" + histParams.sDataFilename);
		imp.show();
		applyHistParameters(imp, histParams);
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
	}
	
	public static < T extends RealType< T > & NativeType< T > > ImagePlus getHistogram(
			final RandomAccessibleInterval<T> channel1, 
			final RandomAccessibleInterval<T> channel2, 
			final HistogramParameters histParams)
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
		return ImageJFunctions.wrapFloat( histFloat, "" );
	}
	
	public static void applyHistParameters(final ImagePlus imp, final HistogramParameters histParams)
	{
		final double binWx = (histParams.minmax1[1] - histParams.minmax1[0]) / histParams.nBinsX;
    	final double binWy = (histParams.minmax2[1] - histParams.minmax2[0]) / histParams.nBinsY;	
		final ImageCanvas canvas = imp.getCanvas();
		final ImageProcessor ip = imp.getProcessor();
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int x = canvas.offScreenX(e.getX());
		        int y = canvas.offScreenY(e.getY());

		        double myX = histParams.getInverseMapFunction().applyAsDouble( histParams.minmax1[0] + (x + 0.5) * binWx);//,10);
		        double myY = histParams.getInverseMapFunction().applyAsDouble( histParams.minmax2[0] + (y + 0.5) * binWy);//,10);
		        float fCount = ip.getf( x, y );
		        IJ.showStatus(
		            String.format("Count %.0f, Int1=%.2f (%d), Int2=%.2f (%d)", fCount, myX, x, myY, y)
		        );
		    }
		});	
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
		histParams.nChannel1 = gdHist.getNextChoiceIndex();
		histParams.nChannel2 = gdHist.getNextChoiceIndex();
		if(histParams.nChannel1  == histParams.nChannel2)
		{
			IJ.log("Warning! Channel X axis is equal to Channel Y!");
		}
		
		histParams.bFlipY = gdHist.getNextBoolean();
		GlobalParameters.bInvertY = histParams.bFlipY;
		Prefs.set("LC.bInvertY", histParams.bFlipY);
		
		histParams.nMapFunction = gdHist.getNextChoiceIndex();		
		GlobalParameters.nMapFunction = histParams.nMapFunction;
		Prefs.set("LC.nMapFunction", histParams.nMapFunction);
		
		histParams.nBinsX = (int)gdHist.getNextNumber();
		GlobalParameters.nBinsX = histParams.nBinsX;
		Prefs.set("LC.nBinsX", histParams.nBinsX);
		
		histParams.nBinsY = (int)gdHist.getNextNumber();
		GlobalParameters.nBinsY = histParams.nBinsY;
		Prefs.set("LC.nBinsY", histParams.nBinsY);
		
		histParams.minmax1[0] = gdHist.getNextNumber();
		GlobalParameters.dMinX = histParams.minmax1[0];
		Prefs.set("LC.dMinX", GlobalParameters.dMinX);
		
		histParams.minmax1[1] = gdHist.getNextNumber();
		GlobalParameters.dMaxX = histParams.minmax1[1];
		Prefs.set("LC.dMaxX", GlobalParameters.dMaxX);
		
		histParams.minmax2[0] = gdHist.getNextNumber();
		GlobalParameters.dMinY = histParams.minmax2[0];
		Prefs.set("LC.dMinY", GlobalParameters.dMinY);
		
		histParams.minmax2[1] = gdHist.getNextNumber();
		GlobalParameters.dMaxY = histParams.minmax2[1];
		Prefs.set("LC.dMaxY", GlobalParameters.dMaxY);
		
		return true;
	}
	
	AbstractSpimData< ? > getDataFromFilename(final String sPathFilenameIni)
	{

		final File f = new File(sPathFilenameIni);
		histParams.sDataFilename = f.getName();
		histParams.sDataPath = f.getParent();
		boolean bXML = false;
		
		if(histParams.sDataFilename.endsWith( "xml" ) || histParams.sDataFilename.endsWith( "h5" ))
		{
			bXML = true;
			if(histParams.sDataFilename.endsWith( "h5" ))
			{
				String sFilenameh5 = histParams.sDataFilename;
				histParams.sDataFilename = histParams.sDataFilename.substring( 0, histParams.sDataFilename.length() - 2 );
				histParams.sDataFilename = histParams.sDataFilename + "xml";
				IJ.log( "Opening " + histParams.sDataFilename + " instead of " + sFilenameh5 + ".");
			}
		}
		if(bXML)
		{
			return SpimDataLoader.loadHDF5(histParams.getFullDataPathFilename());
		}
		return SpimDataLoader.loadBioFormats( histParams.getFullDataPathFilename());
	}
	
	public String openFilenameDialog()
	{
		
		JFileChooser chooser = new JFileChooser(GlobalParameters.lastDir );
		chooser.setDialogTitle( "Open BioFormats or XML/HDF5 files" );

		int returnVal = chooser.showOpenDialog(null);

		if(returnVal == JFileChooser.APPROVE_OPTION) 
		{
			String sFolder = chooser.getSelectedFile().getParent();
			GlobalParameters.lastDir = sFolder;
			Prefs.set( "LC.lastDir", sFolder );
			return chooser.getSelectedFile().getPath();
		}
		return null;
	}
	public static void main(String[] args) throws Exception 
	{
		new ImageJ();
		BuildHistogram test = new BuildHistogram();
		test.run( null);
	}
}
