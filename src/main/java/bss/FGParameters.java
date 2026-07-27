package bss;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.function.DoubleUnaryOperator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.process.ImageProcessor;
import bss.io.SpimDataLoader;
import mpicbg.spim.data.generic.AbstractSpimData;

public class FGParameters
{
	public String sDataPath = "";
	public String sDataFilename = "";
	public int nChannel1;
	public int nChannel2;
	public boolean bFlipY = false;
	public boolean bHasAxesNames = false;
	public String sChannelX = "";
	public String sChannelY = "";
	public int nMapFunction = 0;
	public static final int BSS_Linear = 0, BSS_Log = 1; 
	public int nBinsX = 512;
	public int nBinsY = 512;
	public final double [] minmax1 = new double [2];
	public final double [] minmax2 = new double [2];
	
	
	public DoubleUnaryOperator getMapFunction()
	{
		if ( nMapFunction == BSS_Linear )
		{
			return  x -> x;
		}
		return  x -> Math.log(x);
	}
	
	public DoubleUnaryOperator getInverseMapFunction()
	{
		if ( nMapFunction == BSS_Linear )
		{
			return  x -> x;
		}
		return  x -> Math.exp(x);
	}
	
	public void printParams()
	{
		IJ.log( "Data path " + sDataPath );
		IJ.log( "Data filename " + sDataFilename );
		IJ.log( "Axis X channel number " + nChannel1 );
		IJ.log( "Axis Y channel number " + nChannel2 );
		IJ.log( "Invert Y axis " + bFlipY );
		switch (nMapFunction)
		{
		case BSS_Linear:
			IJ.log( "Mapping function " + "linear" );
			break;
		case BSS_Log:
			IJ.log( "Mapping function " + "log" );
			break;
		}
		IJ.log( "Bins X axis " + nBinsX );
		IJ.log( "Bins Y axis " + nBinsY );
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		IJ.log("Intensity range axis X, min " + df.format( minmax1[0] ) + " max " + df.format( minmax1[1] ));
		IJ.log("Intensity range axis Y, min " + df.format( minmax2[0] ) + " max " + df.format( minmax2[1] ));
		
	}
	

	/**
	 * Encodes parameters as key-value properties inside the ImagePlus.
	 */
	public void saveToImagePlus(final ImagePlus imp) 
	{
	    if (imp == null) return;
	    
	    imp.setProp("BSS_sVersion", BSSsettings.sVersion);
	    imp.setProp("BSS_sDataPath", sDataPath);
	    imp.setProp("BSS_sDataFilename", sDataFilename);
	    imp.setProp("BSS_nChannel1", String.valueOf(nChannel1 + 1));
	    imp.setProp("BSS_nChannel2", String.valueOf(nChannel2 + 1));
	    imp.setProp("BSS_bFlipY", String.valueOf(bFlipY));
	    imp.setProp("BSS_bHasAxesNames", String.valueOf(bHasAxesNames));
	    imp.setProp("BSS_sChannelX", sChannelX);
	    imp.setProp("BSS_sChannelY", sChannelY);
	    imp.setProp("BSS_nMapFunction", String.valueOf(nMapFunction));
	    imp.setProp("BSS_nBinsX", String.valueOf(nBinsX));
	    imp.setProp("BSS_nBinsY", String.valueOf(nBinsY));
	    imp.setProp("BSS_minmax1_0", String.valueOf(minmax1[0]));
	    imp.setProp("BSS_minmax1_1", String.valueOf(minmax1[1]));
	    imp.setProp("BSS_minmax2_0", String.valueOf(minmax2[0]));
	    imp.setProp("BSS_minmax2_1", String.valueOf(minmax2[1]));
	}

	/**
	 * Decodes and restores parameters from ImagePlus properties.
	 */
	public boolean loadFromImagePlus(final ImagePlus imp) 
	{
		if (imp == null) 
			return false;
		String sVersion = "";
		boolean bParseOk = true;
		String valS;
		double valD;
		if ((valS = imp.getProp("BSS_sVersion")) != null) 
			sVersion  = valS;
		else
			bParseOk = false;
		
		if ((valS = imp.getProp("BSS_sDataPath")) != null) 
			sDataPath  = valS;
		else
			bParseOk = false;	

		if ((valS = imp.getProp("BSS_sDataFilename")) != null) 
			sDataFilename  = valS;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_nChannel1")) != Double.NaN) 
			nChannel1  = (int)valD - 1;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_nChannel2")) != Double.NaN) 
			nChannel2  = (int)valD - 1;
		else
			bParseOk = false;

		if ((valS = imp.getProp("BSS_bFlipY")) != null) 
			bFlipY  = Boolean.parseBoolean( valS );
		else
			bParseOk = false;
		
		if ((valS = imp.getProp("BSS_bHasAxesNames")) != null) 
			bHasAxesNames  = Boolean.parseBoolean( valS );
		else
			bParseOk = false;
		
		if(bHasAxesNames)
		{
			if ((valS = imp.getProp("BSS_sChannelX")) != null) 
				sChannelX  = valS;
			else
				bParseOk = false;	
			if ((valS = imp.getProp("BSS_sChannelY")) != null) 
				sChannelY  = valS;
			else
				bParseOk = false;	

		}
			

		if ((valD = imp.getNumericProp("BSS_nMapFunction")) != Double.NaN) 
			nMapFunction  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_nBinsX")) != Double.NaN) 
			nBinsX  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_nBinsY")) != Double.NaN) 
			nBinsY  = (int)valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_minmax1_0")) != Double.NaN) 
			minmax1[0]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_minmax1_1")) != Double.NaN) 
			minmax1[1]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_minmax2_0")) != Double.NaN) 
			minmax2[0]  = valD;
		else
			bParseOk = false;

		if ((valD = imp.getNumericProp("BSS_minmax2_1")) != Double.NaN) 
			minmax2[1]  = valD;
		else
			bParseOk = false;

		if(!bParseOk)
		{
			IJ.error("BigScopeScatter error", "Error loading stored cytofluorogram parameters for " + imp.getTitle() + " image!\n"
					+ "Probably the image was not generated by the plugin");
			return false;
		}
		if(!sVersion.equals( BSSsettings.sVersion ))
		{
			IJ.log( "Warining! The plugin version of cytofluorogram "+sVersion 
					+ " is not equal to the current " + BSSsettings.sVersion);
			IJ.log( "It should be fine, in principle, loading parameters anyway." );
		}
		return true;
	}
	
	public static void applyHistParameters(final ImagePlus imp, final FGParameters histParams)
	{
		
		final DoubleUnaryOperator finv = histParams.getInverseMapFunction();
		final DoubleUnaryOperator f = histParams.getMapFunction();
		
		final double min1 = f.applyAsDouble( histParams.minmax1[0] );
		final double max1 = f.applyAsDouble( histParams.minmax1[1] );
		final double min2 = f.applyAsDouble( histParams.minmax2[0] );
		final double max2 = f.applyAsDouble( histParams.minmax2[1] );
		final double binWx = (max1 - min1) / histParams.nBinsX;
    	final double binWy = (max2 - min2) / histParams.nBinsY;	
		final ImageCanvas canvas = imp.getCanvas();
		final ImageProcessor ip = imp.getProcessor();
		String prefixX = "IntX_" + "ch" +  Integer.toString( histParams.nChannel1 + 1);
		String prefixY = "IntY_" + "ch" +  Integer.toString( histParams.nChannel2 + 1);

		if(histParams.bHasAxesNames)
		{
			prefixX = prefixX + "[" + histParams.sChannelX + "]";
			prefixY = prefixY + "[" + histParams.sChannelY + "]";
		}

		final String sPrefixX = prefixX;
		final String sPrefixY = prefixY;
		final int nHeight = ip.getHeight() - 1;
		
		canvas.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int x = canvas.offScreenX(e.getX());
		        int y = canvas.offScreenY(e.getY());
		        
		        //get the value of the counts
		        float fCount = 0.0f;
		        try {
		        	fCount = ip.getf( x, y );
		        }
		        catch(Exception exc)
		        {
		        	IJ.log("error");
		        }
		        //map to the cytofluorogram
		        if(histParams.bFlipY)
		        {
		        	y = nHeight - y;
		        }
		        final double myX = finv.applyAsDouble( min1 + (x + 0.5) * binWx);//,10);
		        final double myY = finv.applyAsDouble( min2 + (y + 0.5) * binWy);//,10);

		        IJ.showStatus(
		            String.format("Count %.0f, " +  sPrefixX + "=%.2f (%d), "+ sPrefixY + "=%.2f (%d)", fCount, myX, x, myY, y)
		        );
		    }
		});	
	}
	
	public static AbstractSpimData< ? > getDataFromFilename(final String sPathFilenameIni, final FGParameters cfgParamsFile)
	{

		final File f = new File(sPathFilenameIni);
		cfgParamsFile.sDataFilename = f.getName();
		cfgParamsFile.sDataPath = f.getParent();
		boolean bXML = false;
		
		if(cfgParamsFile.sDataFilename.endsWith( "xml" ) || cfgParamsFile.sDataFilename.endsWith( "h5" ))
		{
			bXML = true;
			if(cfgParamsFile.sDataFilename.endsWith( "h5" ))
			{
				String sFilenameh5 = cfgParamsFile.sDataFilename;
				cfgParamsFile.sDataFilename = cfgParamsFile.sDataFilename.substring( 0, cfgParamsFile.sDataFilename.length() - 2 );
				cfgParamsFile.sDataFilename = cfgParamsFile.sDataFilename + "xml";
				IJ.log( "Opening " + cfgParamsFile.sDataFilename + " instead of " + sFilenameh5 + ".");
			}
		}
		if(bXML)
		{
			return SpimDataLoader.loadHDF5(cfgParamsFile.getFullDataPathFilename());
		}
		return SpimDataLoader.loadBioFormats( cfgParamsFile.getFullDataPathFilename());
	}
	
	String getChannelsNamesROI()
	{
		//build channel descriptions
		String out1 = "ch" + Integer.toString( nChannel1 + 1 );
		String out2 = "ch" + Integer.toString( nChannel2 + 1 );
		if(bHasAxesNames)
		{
			out1 = out1 + "[" + sChannelX + "]_X";
			out2 = out2 + "[" + sChannelY + "]_Y";	
		}
		if(nChannel1 < nChannel2)
		{
			return out1 + "_"+ out2;
		}
		return out2 + "_"+ out1;
	}
	
	String getChannelsConfigurationString()
	{
		
		String out1 = "X(" + Integer.toString( nChannel1 + 1 ) + ")";
		String out2 = "Y(" + Integer.toString( nChannel2 + 1 );
		if(bFlipY)
			out2 = out2 + "f";
		out2 = out2 + ")";
		if(bHasAxesNames)
		{
			out1 = out1 + sChannelX;
			out2 = out2 + sChannelY;			
		}
		
		return out1 + "_" + out2;
	}
	
	public String getFilenameNoExtension() 
	{
	    if (sDataFilename == null || sDataFilename.isEmpty()) {
	        return sDataFilename;
	    }
	    
	    int lastDotIndex = sDataFilename.lastIndexOf('.');
	    
	    // Handle cases where there is no dot, or the file is hidden (e.g., ".gitignore")
	    if (lastDotIndex <= 0) {
	        return sDataFilename; 
	    }
	    
	    return sDataFilename.substring(0, lastDotIndex);
	}
	
	public String getFullDataPathFilename()
	{
		return sDataPath + File.separator + sDataFilename;
	}
}
